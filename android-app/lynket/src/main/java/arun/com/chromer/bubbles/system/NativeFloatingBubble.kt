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
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
import arun.com.chromer.R
import arun.com.chromer.browsing.icons.WebsiteIconsProvider
import arun.com.chromer.bubbles.FloatingBubble
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
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
  private val websiteIconsProvider: WebsiteIconsProvider
) : FloatingBubble {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  // A channel keeps a link sent before the collector below has started; a shared flow without
  // subscribers dropped the first link after a cold start.
  private val loadQueue = Channel<BubbleLoadData>(Channel.UNLIMITED)

  init {
    scope.launch {
      loadQueue.receiveAsFlow()
        .mapNotNull { bubbleData ->
          try {
            showBubble(bubbleData)
          } catch (e: Exception) {
            Timber.e(e)
            null
          }
        }
        .flowOn(Dispatchers.IO)
        .collect { /* Process bubbles */ }
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
    if (!bubblesAllowed()) {
      Timber.w("Bubbles are not allowed; opening %s in a tab", website.url)
      Handler(Looper.getMainLooper()).post {
        Toast.makeText(application, R.string.bubbles_not_allowed, Toast.LENGTH_LONG).show()
      }
      return false
    }
    loadQueue.trySend(
      BubbleLoadData(
        website = website,
        fromMinimize = fromMinimize,
        fromAmp = fromAmp,
        incognito = incognito,
        contextRef = WeakReference(context),
        color = color
      )
    )
    return true
  }

  /** Notifications on, and on Android 11 and later, bubbles not switched off for the app. */
  private fun bubblesAllowed(): Boolean {
    val manager = application.getSystemService(NotificationManager::class.java) ?: return false
    if (!manager.areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      manager.bubblePreference == NotificationManager.BUBBLE_PREFERENCE_NONE
    ) {
      return false
    }
    return true
  }

  private suspend fun showBubble(bubbleLoadData: BubbleLoadData): BubbleLoadData {
    // Show initial bubble
    val initialBubble = bubbleNotificationManager.showBubbles(bubbleLoadData)

    // Avoid notification throttling
    delay(1000)

    // Fetch website details and icon
    val updatedBubble = try {
      val website = websiteRepository.getWebsite(initialBubble.website.url)
        .firstOrNull() ?: initialBubble.website

      val iconData = websiteIconsProvider.getBubbleIconAndColor(website)

      initialBubble.copy(
        website = website,
        icon = iconData.icon,
        color = iconData.color
      )
    } catch (e: Exception) {
      Timber.e(e)
      initialBubble
    }

    // Show updated bubble with icon and color
    return bubbleNotificationManager.showBubbles(updatedBubble)
  }
}
