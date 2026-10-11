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
 * Finds the app a link came from, at the moment the link opens.
 *
 * When a link arrives, [detectLinkSource] reads the usage events of the last few seconds and keeps
 * the most recent app that came to the foreground, other than Lynket. Per-app rules read
 * [nonFilteredPackage]; the app-based toolbar colour reads [filteredPackage], which also skips
 * system UI, the Custom Tab provider and the Google search box. Without usage access both stay empty,
 * so links open as they do without per-app rules.
 */
@Singleton
class AppDetectionManager
@Inject
constructor(
  private val application: Application,
  private val preferences: Preferences,
) {

  // App the last link came from, for the toolbar colour.
  @get:Synchronized
  var filteredPackage = ""
    private set

  // App the last link came from, for per-app rules.
  @get:Synchronized
  var nonFilteredPackage = ""
    private set

  /** Reads the app the link being opened now came from. Call it when an external link arrives. */
  @Synchronized
  fun detectLinkSource(now: Long = System.currentTimeMillis()) {
    clear()
    if (!Utils.canReadUsageStats(application)) {
      Timber.d("Link source: no usage access")
      return
    }
    val usageStatsManager = application.getSystemService(UsageStatsManager::class.java) ?: return
    val events = try {
      usageStatsManager.queryEvents(now - LOOKBACK_MS, now)
    } catch (e: SecurityException) {
      Timber.e(e, "Link source: usage events not readable")
      return
    } ?: return
    val event = UsageEvents.Event()
    // Events come oldest first, so the last match is the most recent one.
    while (events.hasNextEvent()) {
      events.getNextEvent(event)
      if (event.eventType != FOREGROUND_EVENT) continue
      val appPackage = event.packageName ?: continue
      if (nonFilterPackage(appPackage)) nonFilteredPackage = appPackage
      if (filterPackage(appPackage)) filteredPackage = appPackage
    }
    Timber.d("Link source: %s (for colour: %s)", nonFilteredPackage, filteredPackage)
  }

  @Synchronized
  fun clear() {
    nonFilteredPackage = ""
    filteredPackage = ""
  }

  private fun nonFilterPackage(appPackage: String): Boolean {
    return if (appPackage.equals("android", ignoreCase = true)) {   // Ignore system pop ups
      false
    } else !appPackage.equals(application.packageName, ignoreCase = true) // Ignore our app
  }

  private fun filterPackage(packageName: String): Boolean {
    // Ignore system pop ups
    if (packageName.equals("android", ignoreCase = true)) return false

    // Ignore system pop ups
    if (packageName.contains("systemui")) return false

    // Ignore our app
    if (packageName.equals(application.packageName, ignoreCase = true)) return false

    // Chances are that we picked the opening custom tab, so let's ignore our default provider
    // to be safe
    if (packageName.equals(preferences.customTabPackage() ?: "", ignoreCase = true)) return false

    // Ignore google quick search box
    return !packageName.equals("com.google.android.googlequicksearchbox", ignoreCase = true)
  }

  private companion object {
    // How far back a link-open looks for the app the person was in. Covers the time a chooser
    // dialog stays open before Lynket starts.
    const val LOOKBACK_MS = 10_000L

    // ACTIVITY_RESUMED (API 29) replaced MOVE_TO_FOREGROUND; both have the value 1.
    val FOREGROUND_EVENT: Int =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        UsageEvents.Event.ACTIVITY_RESUMED
      } else {
        @Suppress("DEPRECATION") // sdlc-debt: MOVE_TO_FOREGROUND is the only name below API 29; drop when minSdk reaches 29
        UsageEvents.Event.MOVE_TO_FOREGROUND
      }
  }
}
