/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.bubbles.system

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import arun.com.chromer.browsing.icons.WebsiteIconsProvider
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubbleLoadData
import arun.com.chromer.bubbles.BubblePlace
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.FloatingBubble
import arun.com.chromer.bubbles.OpenBubble
import arun.com.chromer.bubbles.bubbleConversationId
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.di.hilt.IoDispatcher
import arun.com.chromer.shared.Constants
import arun.com.chromer.tabs.TabsManager
import dagger.Lazy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@RequiresApi(Build.VERSION_CODES.Q)
@Singleton
class NativeFloatingBubble
@Inject
constructor(
  private val application: Application,
  private val websiteRepository: WebsiteRepository,
  private val bubbleNotificationManager: BubbleNotificationManager,
  private val websiteIconsProvider: WebsiteIconsProvider,
  private val bubbleGate: BubbleGate,
  private val bubbleRegistry: BubbleRegistry,
  private val bubbleReconciler: BubbleReconciler,
  @IoDispatcher ioDispatcher: CoroutineDispatcher,
  // Lazy: the tabs manager opens bubbles through this class, so the two cannot build each other.
  private val tabsManager: Lazy<TabsManager>,
) : FloatingBubble {

  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
  /**
   * Two sends of one link post in turn; different links never wait for each other. A link's lock
   * is dropped once no send of it is posting or waiting, so the map holds only links in flight.
   */
  private val postLocks = HashMap<String, PostLock>()

  /** One return at a time: see [returnNewestOverflowBubble]. */
  private val returnLock = Mutex()
  private val waitingReturns = AtomicInteger()

  /** Closes in the open stack that wait for the fold. */
  private val heldReturns = AtomicInteger()

  /** A return failed, so the closes still waiting give up: they would post the same bubble again. */
  @Volatile
  private var returnFailed = false

  private class PostLock {
    val mutex = Mutex()
    /** Sends of the link that hold or wait for [mutex]; read and written under postLocks. */
    var users = 0
  }

  private suspend fun <T> withPostLock(url: String, block: suspend () -> T): T {
    val lock = synchronized(postLocks) { postLocks.getOrPut(url) { PostLock() }.also { it.users++ } }
    try {
      return lock.mutex.withLock { block() }
    } finally {
      synchronized(postLocks) { if (--lock.users == 0) postLocks.remove(url) }
    }
  }

  override fun openBubble(
    website: Website,
    fromMinimize: Boolean,
    fromAmp: Boolean,
    incognito: Boolean,
    context: Context?,
    color: Int,
    siteColor: Int
  ): Boolean {
    val refusal = bubbleGate.refusal(bubbleConversationId(website.url, incognito))
    if (refusal != null) {
      Timber.w("Bubble refused (%s); opening %s in a tab", refusal, website.url)
      return false
    }
    val data = BubbleLoadData(
      website = website,
      fromMinimize = fromMinimize,
      fromAmp = fromAmp,
      incognito = incognito,
      contextRef = WeakReference(context),
      color = color,
      siteColor = siteColor
    )
    scope.launch {
      try {
        withPostLock(website.url) { showBubble(data) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
    return true
  }

  private suspend fun showBubble(initial: BubbleLoadData): Unit = coroutineScope {
    val url = initial.website.url
    // An incognito bubble shows only its generic label and the Lynket icon, so nothing about the
    // site is read for it. A normal bubble's site and icon load while the first post publishes.
    val details = if (initial.incognito) null else async { withDetails(initial) }
    val ready = details?.let { withTimeoutOrNull(ICON_WAIT_MS) { it.await() } }
    val key = BubbleKey.of(BrowsingMode.NativeBubbles, url, initial.incognito)
    bubbleRegistry.posting(key)
    val posted = bubbleNotificationManager.showBubbles((ready ?: initial).withHeightOfOpenBubble())
    bubbleRegistry.add(BrowsingMode.NativeBubbles, url, initial.incognito, posted, posting = true)

    // Android can still post the notification without showing it as a bubble. Then the page opens
    // in a tab with a message instead of being lost in the notification shade.
    if (!shownAsBubble(url, initial.incognito)) {
      details?.cancel()
      Timber.w("Android did not show %s as a bubble; opening it in a tab", url)
      bubbleRegistry.remove(key)
      bubbleNotificationManager.cancel(url, initial.incognito)
      tabsManager.get().openRefusedBubble(application, initial.website, BubbleRefusal.NOT_SHOWN, initial.incognito)
      return@coroutineScope
    }

    bubbleRegistry.settled(key)
    bubbleReconciler.reconcile()

    if (details != null && ready == null) {
      val late = details.await()
      // Same url, so the same notification is updated in place; the summary is already posted. An
      // update would set the flag of a bubble that aged out and leave it in the overflow, so that
      // bubble only keeps the details for its next return.
      if (late !== initial) {
        val update = late.copy(desiredHeightDp = posted.desiredHeightDp)
        if (bubbleNotificationManager.isShownAsBubble(url, initial.incognito)) {
          bubbleNotificationManager.showBubbles(update, withSummary = false)
        }
        bubbleRegistry.updateLoadData(key, update)
      }
    }
  }

  /** A link sent again updates its bubble, so the page keeps the height it opened with. */
  private fun BubbleLoadData.withHeightOfOpenBubble(): BubbleLoadData {
    val open = bubbleRegistry.find(BrowsingMode.NativeBubbles, website.url, incognito)?.loadData
    return open?.desiredHeightDp?.let { copy(desiredHeightDp = it) } ?: this
  }

  /**
   * Posts the newest overflow bubble anew after the person closed a bubble in a folded stack, with no
   * alert. Returns run one at a time and each waits for Android's flag, so a close that follows reads
   * where this bubble went. A return that fails says nothing: the bubble stays in the overflow.
   *
   * A drag of the stack takes the flag off every bubble in it, and their delete intents follow one by
   * one, so until they arrive a closed bubble reads like an overflow bubble. So a close first reads
   * the stack: when every other bubble in it keeps its flag, this was a single close and
   * the bubble returns at once, while the row closes its gap. Any other read may be a drag, and the
   * wait lets its delete intents arrive. sdlc-debt: a fixed wait; a delete intent later than it posts
   * its closed bubble for a moment. A read that tells a closing bubble from an aged one would replace it.
   */
  fun returnNewestOverflowBubble(): Job = launchReturn {
    val single = wasSingleClose()
    if (!single) delay(CLOSES_SETTLE_MS)
    returnLock.withLock { if (!returnFailed) returnNewest(reconciled = single) }
  }

  /** A close in the open stack left a place free, and it fills when the stack folds. */
  fun holdReturn() {
    heldReturns.incrementAndGet()
  }

  /**
   * Fills the places that closes left free while the stack was open, newest overflow bubble first,
   * one for each close and up to the fullest stack. Every post reads the free places from the list
   * again, and a close with no place left to fill, or no bubble to return, drops out. The fullest
   * stack alone cannot say that a place is free: a read just after a post can find the flag of a
   * bubble Android is ageing out, so it can stand one or two above the real limit, and a fold with no
   * close would then push bubbles round. The closes end with the process, as the list does.
   *
   * It stops when [stillFolded] says the person opened the stack again, or after a failed return:
   * what is left waits for the next fold.
   */
  fun fillFreePlaces(stillFolded: () -> Boolean): Job = launchReturn {
    returnLock.withLock {
      while (heldReturns.get() > 0 && !returnFailed && stillFolded()) {
        if (returnNewest(reconciled = false)) {
          heldReturns.decrementAndGet()
        } else {
          if (!returnFailed) heldReturns.set(0)
          break
        }
      }
    }
  }

  /**
   * Posts the newest overflow bubble at once with the stack open, for the X of the last bubble in it:
   * see [BubbleCloser.closePage]. It takes no held close, because its own close is not held. The
   * answer says whether the bubble came back.
   */
  fun returnIntoOpenStack(): Deferred<Boolean> {
    val back = CompletableDeferred<Boolean>()
    launchReturn {
      returnLock.withLock { back.complete(!returnFailed && returnNewest(reconciled = false)) }
    }.invokeOnCompletion { back.complete(false) }
    return back
  }

  private fun launchReturn(block: suspend () -> Unit): Job {
    waitingReturns.incrementAndGet()
    return scope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e)
      } finally {
        if (waitingReturns.decrementAndGet() == 0) returnFailed = false
      }
    }
  }

  /**
   * Whether every bubble the list has in the stack keeps its flag. The read that says so is the one
   * the list takes, so a later read in the middle of a drag cannot file a closing bubble under the
   * overflow. A read that says otherwise leaves the list as it was: a close that follows must still
   * find its bubble in the stack. A bubble that aged out unseen also says otherwise, and only costs
   * this close the wait.
   */
  private fun wasSingleClose(): Boolean {
    val stack = bubbleRegistry.list(BrowsingMode.NativeBubbles).filter { it.place == BubblePlace.STACK }
    return stack.isNotEmpty() && bubbleReconciler.reconcileIf { places ->
      stack.all { places[it.key.notificationId] == BubblePlace.STACK }
    }
  }

  /** Returns whether a bubble came back. */
  private suspend fun returnNewest(reconciled: Boolean): Boolean {
    val fullest = bubbleRegistry.fullestStack()
    if (!reconciled) bubbleReconciler.reconcile()
    // A stack as full as it has been has no room: the close was in the overflow after all, and a post
    // would push another bubble out.
    val stack = bubbleRegistry.list(BrowsingMode.NativeBubbles).count { it.place == BubblePlace.STACK }
    if (fullest > 0 && stack >= fullest) return false
    val bubble = bubbleRegistry.returnCandidates(BrowsingMode.NativeBubbles).lastOrNull() ?: return false
    val data = bubble.loadData ?: return false
    Timber.d("Returning: %d", bubble.key.notificationId)
    bubbleRegistry.posting(bubble.key)
    val shown = try {
      bubbleNotificationManager.returnBubble(data)
      shownAsBubble(bubble.url, bubble.incognito).also { shown ->
        if (shown) Timber.d("Returned: %d", bubble.key.notificationId) else returnFailed = true
      }
    } finally {
      bubbleRegistry.settled(bubble.key)
    }
    bubbleReconciler.reconcile()
    if (data.icon == null && !bubble.incognito) scope.launch { loadMissingIcon(bubble) }
    return shown
  }

  /** Under the link's lock, so it follows a load in flight, and keeps the posted title and text. */
  private suspend fun loadMissingIcon(bubble: OpenBubble) {
    try {
      withPostLock(bubble.url) {
        val current = bubbleRegistry.find(bubble.key)?.loadData ?: return@withPostLock
        if (current.icon != null) return@withPostLock
        val loaded = withDetails(current)
        val update = current.copy(icon = loaded.icon, color = loaded.color)
        if (update.icon == null) return@withPostLock
        // A post brings a bubble back to the stack, so one that aged out during the load only keeps
        // the icon for its next return.
        bubbleReconciler.reconcile()
        if (bubbleRegistry.find(bubble.key)?.place == BubblePlace.STACK) {
          bubbleNotificationManager.showBubbles(update, withSummary = false)
        }
        bubbleRegistry.updateLoadData(bubble.key, update)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.e(e)
    }
  }

  /** The site read, with its icon and colour, of a normal bubble. */
  private suspend fun withDetails(initial: BubbleLoadData): BubbleLoadData = try {
    val website = websiteRepository.getWebsite(initial.website.url).firstOrNull() ?: initial.website
    val iconData = websiteIconsProvider.getBubbleIconAndColor(website)
    initial.copy(
      website = website,
      icon = iconData.icon,
      color = iconData.color
    )
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Timber.e(e)
    initial
  }

  /**
   * The bubble flag can follow the post by a moment, so it is read every 250 ms for up to 1.5 s,
   * stopping as soon as it shows.
   */
  private suspend fun shownAsBubble(url: String, incognito: Boolean): Boolean {
    repeat(FLAG_READS) { read ->
      if (bubbleNotificationManager.isShownAsBubble(url, incognito)) return true
      if (read < FLAG_READS - 1) delay(FLAG_READ_INTERVAL_MS)
    }
    return false
  }

  private companion object {
    /** The longest wait for the site icon before the first post. */
    const val ICON_WAIT_MS = 300L
    const val FLAG_READS = 7
    const val FLAG_READ_INTERVAL_MS = 250L
    /** The time the delete intents of one drag take to arrive. A single close does not wait. */
    const val CLOSES_SETTLE_MS = 500L
  }
}
