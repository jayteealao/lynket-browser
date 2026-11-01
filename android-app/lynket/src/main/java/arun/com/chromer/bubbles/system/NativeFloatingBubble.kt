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
import arun.com.chromer.bubbles.FloatingBubble
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
import java.util.concurrent.ConcurrentHashMap
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
  @IoDispatcher ioDispatcher: CoroutineDispatcher,
  // Lazy: the tabs manager reaches this class through the bubble factory.
  private val tabsManager: Lazy<TabsManager>,
) : FloatingBubble {

  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
  // sdlc-debt: one small lock per distinct url stays for the life of the process; drop an entry
  // with reference counting if a person ever opens a very large number of distinct bubble links.
  /** Two sends of one link post in turn; different links never wait for each other. */
  private val postLocks = ConcurrentHashMap<String, Mutex>()

  override fun openBubble(
    website: Website,
    fromMinimize: Boolean,
    fromAmp: Boolean,
    incognito: Boolean,
    context: Context?,
    color: Int
  ): Boolean {
    val refusal = bubbleGate.refusal(bubbleConversationId(website.url))
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
        postLocks.computeIfAbsent(website.url) { Mutex() }.withLock { showBubble(data) }
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
    bubbleNotificationManager.showBubbles(ready ?: initial)

    // Android can still post the notification without showing it as a bubble. Then the page opens
    // in a tab with a message instead of being lost in the notification shade.
    if (!shownAsBubble(url)) {
      details?.cancel()
      Timber.w("Android did not show %s as a bubble; opening it in a tab", url)
      bubbleNotificationManager.cancel(url)
      tabsManager.get().openRefusedBubble(application, initial.website, BubbleRefusal.NOT_SHOWN, initial.incognito)
      return@coroutineScope
    }

    if (details != null && ready == null) {
      val late = details.await()
      // Same url, so the same notification is updated in place; the summary is already posted.
      if (late !== initial) bubbleNotificationManager.showBubbles(late, withSummary = false)
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
  private suspend fun shownAsBubble(url: String): Boolean {
    repeat(FLAG_READS) { read ->
      if (bubbleNotificationManager.isShownAsBubble(url)) return true
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
