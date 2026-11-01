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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

  private suspend fun showBubble(bubbleLoadData: BubbleLoadData): BubbleLoadData {
    // Show initial bubble
    val initialBubble = bubbleNotificationManager.showBubbles(bubbleLoadData)

    // Avoid notification throttling
    delay(1000)

    // Android can still post the notification without showing it as a bubble. Then the page opens
    // in a tab with a message instead of being lost in the notification shade.
    if (!shownAsBubble(initialBubble.website.url)) {
      Timber.w("Android did not show %s as a bubble; opening it in a tab", initialBubble.website.url)
      bubbleNotificationManager.cancel(initialBubble.website.url)
      tabsManager.get().openRefusedBubble(application, initialBubble.website, BubbleRefusal.NOT_SHOWN, initialBubble.incognito)
      return initialBubble
    }

    val updatedBubble = withDetails(initialBubble)

    // Show updated bubble with icon and color
    return bubbleNotificationManager.showBubbles(updatedBubble)
  }

  /** The site read (without saving for incognito) and, for a normal bubble, its icon and colour. */
  private suspend fun withDetails(initial: BubbleLoadData): BubbleLoadData = try {
    val url = initial.website.url
    // An incognito page must leave no history row and no cache entry.
    val read = if (initial.incognito) {
      websiteRepository.getWebsiteWithoutSaving(url)
    } else {
      websiteRepository.getWebsite(url)
    }
    val website = read.firstOrNull() ?: initial.website

    if (initial.incognito) {
      // An incognito bubble shows the Lynket icon, so the site icon is never fetched.
      initial.copy(website = website)
    } else {
      val iconData = websiteIconsProvider.getBubbleIconAndColor(website)
      initial.copy(
        website = website,
        icon = iconData.icon,
        color = iconData.color
      )
    }
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Timber.e(e)
    initial
  }

  /** The bubble flag can follow the post by a moment, so it is read up to three times. */
  private suspend fun shownAsBubble(url: String): Boolean {
    repeat(FLAG_READS) { read ->
      if (bubbleNotificationManager.isShownAsBubble(url)) return true
      if (read < FLAG_READS - 1) delay(FLAG_READ_INTERVAL_MS)
    }
    return false
  }

  private companion object {
    const val FLAG_READS = 3
    const val FLAG_READ_INTERVAL_MS = 250L
  }
}
