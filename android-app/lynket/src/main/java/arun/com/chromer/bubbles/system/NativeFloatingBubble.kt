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
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import arun.com.chromer.browsing.icons.WebsiteIconsProvider
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.FloatingBubble
import arun.com.chromer.bubbles.OpenBubble
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.di.hilt.IoDispatcher
import arun.com.chromer.shared.Constants
import arun.com.chromer.tabs.TabsManager
import dagger.Lazy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
import javax.inject.Inject
import javax.inject.Singleton

data class BubbleLoadData(
  val website: Website,
  val fromMinimize: Boolean,
  val fromAmp: Boolean,
  val incognito: Boolean,
  val contextRef: WeakReference<Context?> = WeakReference(null),
  val icon: Bitmap? = null,
  val color: Int = Constants.NO_COLOR
)

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
    color: Int
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
      color = color
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
    val posted = bubbleNotificationManager.showBubbles(ready ?: initial)
    bubbleRegistry.add(BrowsingMode.NativeBubbles, url, initial.incognito, posted)

    // Android can still post the notification without showing it as a bubble. Then the page opens
    // in a tab with a message instead of being lost in the notification shade.
    if (!shownAsBubble(url, initial.incognito)) {
      details?.cancel()
      Timber.w("Android did not show %s as a bubble; opening it in a tab", url)
      bubbleRegistry.remove(BubbleKey.of(BrowsingMode.NativeBubbles, url, initial.incognito))
      bubbleNotificationManager.cancel(url, initial.incognito)
      tabsManager.get().openRefusedBubble(application, initial.website, BubbleRefusal.NOT_SHOWN, initial.incognito)
      return@coroutineScope
    }

    bubbleReconciler.reconcile()

    if (details != null && ready == null) {
      val late = details.await()
      // Same url, so the same notification is updated in place; the summary is already posted.
      if (late !== initial) {
        bubbleNotificationManager.showBubbles(late, withSummary = false)
        bubbleRegistry.updateLoadData(BubbleKey.of(BrowsingMode.NativeBubbles, url, initial.incognito), late)
      }
    }
  }

  /**
   * Posts the newest overflow bubble again after the person closed a bubble in the stack, with no
   * alert. Returns run one at a time and each waits for Android's flag, so a close that follows reads
   * where this bubble went. A return that fails says nothing: the bubble stays in the overflow.
   */
  fun returnNewestOverflowBubble(done: () -> Unit = {}) {
    scope.launch {
      try {
        returnLock.withLock { returnNewest() }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e)
      } finally {
        done()
      }
    }
  }

  private suspend fun returnNewest() {
    bubbleReconciler.reconcile()
    val bubble = bubbleRegistry.returnCandidates(BrowsingMode.NativeBubbles).lastOrNull() ?: return
    val data = bubble.loadData ?: return
    bubbleNotificationManager.showBubbles(data, withSummary = false)
    if (shownAsBubble(bubble.url, bubble.incognito)) Timber.d("Returned: %d", bubble.key.notificationId)
    bubbleReconciler.reconcile()
    if (data.icon == null && !bubble.incognito) scope.launch { loadMissingIcon(bubble) }
  }

  /** Under the link's lock, so it follows a load in flight, and keeps the posted title and text. */
  private suspend fun loadMissingIcon(bubble: OpenBubble) {
    try {
      withPostLock(bubble.url) {
        val current = bubbleRegistry.find(bubble.key)?.loadData ?: return@withPostLock
        val update = if (current.icon != null) current else {
          val loaded = withDetails(current)
          current.copy(icon = loaded.icon, color = loaded.color)
        }
        if (update.icon != null && bubbleRegistry.find(bubble.key) != null) {
          bubbleNotificationManager.showBubbles(update, withSummary = false)
          bubbleRegistry.updateLoadData(bubble.key, update)
        }
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
  }
}
