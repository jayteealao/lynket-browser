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

package arun.com.chromer.appdetect

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import arun.com.chromer.settings.Preferences
import arun.com.chromer.util.Utils
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app a link came from: [nonFiltered] for per-app rules, [filtered] for the app-based toolbar
 * colour (it also skips system UI, the Custom Tab provider and the Google search box). Empty when
 * the app is not known.
 */
data class LinkSource(val nonFiltered: String = "", val filtered: String = "") {
  companion object {
    /** A link opened inside Lynket, or one whose app could not be read. */
    val NONE = LinkSource()
  }
}

/**
 * Finds the app a link came from, at the moment the link opens.
 *
 * When a link arrives, [detectLinkSource] reads the usage events of the last few seconds and returns
 * the most recent app, other than Lynket, that came to the foreground or left it. An app the person
 * has been reading in for a long time logged its resume long ago, but it logs a pause when the link
 * hands over to Lynket, so the pause names it. It keeps nothing: the caller
 * passes the result along with the link it belongs to. Without usage access it returns
 * [LinkSource.NONE], so links open as they do without per-app rules.
 */
@Singleton
class AppDetectionManager
@Inject
constructor(
  private val application: Application,
  private val preferences: Preferences,
) {

  /** Reads the app the link being opened now came from. Call it when an external link arrives. */
  fun detectLinkSource(now: Long = System.currentTimeMillis()): LinkSource {
    if (!Utils.canReadUsageStats(application)) {
      Timber.d("Link source: no usage access")
      return LinkSource.NONE
    }
    val usageStatsManager = application.getSystemService(UsageStatsManager::class.java) ?: return LinkSource.NONE
    val events = try {
      usageStatsManager.queryEvents(now - LOOKBACK_MS, now)
    } catch (e: SecurityException) {
      Timber.e(e, "Link source: usage events not readable")
      return LinkSource.NONE
    } ?: return LinkSource.NONE
    // Read once per link, not once per event.
    val provider = preferences.customTabPackage().orEmpty()
    var nonFiltered = ""
    var filtered = ""
    val event = UsageEvents.Event()
    // Events come oldest first, so the last match is the most recent one.
    while (events.hasNextEvent()) {
      events.getNextEvent(event)
      if (event.eventType !in SOURCE_EVENTS) continue
      val appPackage = event.packageName ?: continue
      if (nonFilterPackage(appPackage)) nonFiltered = appPackage
      if (filterPackage(appPackage, provider)) filtered = appPackage
    }
    Timber.d("Link source: %s (for colour: %s)", nonFiltered, filtered)
    return LinkSource(nonFiltered = nonFiltered, filtered = filtered)
  }

  private fun nonFilterPackage(appPackage: String): Boolean {
    return if (appPackage.equals("android", ignoreCase = true)) {   // Ignore system pop ups
      false
    } else !appPackage.equals(application.packageName, ignoreCase = true) // Ignore our app
  }

  private fun filterPackage(packageName: String, provider: String): Boolean {
    // Ignore system pop ups
    if (packageName.equals("android", ignoreCase = true)) return false

    // Ignore system pop ups
    if (packageName.contains("systemui")) return false

    // Ignore our app
    if (packageName.equals(application.packageName, ignoreCase = true)) return false

    // Chances are that we picked the opening custom tab, so let's ignore our default provider
    // to be safe
    if (packageName.equals(provider, ignoreCase = true)) return false

    // Ignore google quick search box
    return !packageName.equals("com.google.android.googlequicksearchbox", ignoreCase = true)
  }

  private companion object {
    // How far back a link-open looks for the app the person was in. Covers the time a chooser
    // dialog stays open before Lynket starts.
    const val LOOKBACK_MS = 10_000L

    // Events that name the app the person was in: it came forward, or it went back when the link
    // handed over. ACTIVITY_RESUMED and ACTIVITY_PAUSED (API 29) replaced MOVE_TO_FOREGROUND and
    // MOVE_TO_BACKGROUND with the same values (1 and 2); ACTIVITY_STOPPED is new in API 29.
    val SOURCE_EVENTS: Set<Int> =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        setOf(
          UsageEvents.Event.ACTIVITY_RESUMED,
          UsageEvents.Event.ACTIVITY_PAUSED,
          UsageEvents.Event.ACTIVITY_STOPPED,
        )
      } else {
        @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND and MOVE_TO_BACKGROUND are the only names below API 29; drop when minSdk reaches 29
        setOf(UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEvents.Event.MOVE_TO_BACKGROUND)
      }
  }
}
