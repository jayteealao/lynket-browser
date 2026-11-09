/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.appdetect

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Process
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.di.hilt.ClockModule
import arun.com.chromer.util.TestClock
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import javax.inject.Inject
import kotlin.time.Clock

/**
 * The app a link came from is read from usage events when the link opens; no service polls for it.
 */
@HiltAndroidTest
@UninstallModules(ClockModule::class)
class AppDetectionManagerTest : LynketRobolectricSuite() {

  @BindValue
  @JvmField
  val clock: Clock = TestClock(CLOCK_NOW)

  @Inject
  lateinit var appDetectionManager: AppDetectionManager

  private val usageStats get() = shadowOf(application.getSystemService(UsageStatsManager::class.java))

  private fun usageAccess(mode: Int) {
    shadowOf(application.getSystemService(AppOpsManager::class.java))
      .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), application.packageName, mode)
  }

  private fun resumed(pkg: String, at: Long) =
    usageStats.addEvent(pkg, at, UsageEvents.Event.ACTIVITY_RESUMED)

  @Test
  fun theMostRecentForegroundAppOtherThanLynketIsTheLinkSource() {
    usageAccess(AppOpsManager.MODE_ALLOWED)
    val now = 1_000_000L
    resumed("com.google.android.calendar", now - 6_000)
    resumed("com.google.android.deskclock", now - 2_000)
    resumed("android", now - 1_500)
    resumed(application.packageName, now - 500)

    val source = appDetectionManager.detectLinkSource(now)

    assertThat(source.nonFiltered).isEqualTo("com.google.android.deskclock")
    assertThat(source.filtered).isEqualTo("com.google.android.deskclock")
  }

  @Test
  fun theColourSourceSkipsSystemUi() {
    usageAccess(AppOpsManager.MODE_ALLOWED)
    val now = 1_000_000L
    resumed("com.google.android.apps.messaging", now - 3_000)
    resumed("com.android.systemui", now - 1_000)

    val source = appDetectionManager.detectLinkSource(now)

    assertThat(source.nonFiltered).isEqualTo("com.android.systemui")
    assertThat(source.filtered).isEqualTo("com.google.android.apps.messaging")
  }

  private fun paused(pkg: String, at: Long) =
    usageStats.addEvent(pkg, at, UsageEvents.Event.ACTIVITY_PAUSED)

  private fun stopped(pkg: String, at: Long) =
    usageStats.addEvent(pkg, at, UsageEvents.Event.ACTIVITY_STOPPED)

  @Test
  fun anAppInFrontForLongerThanTheLookbackIsFoundByItsPause() {
    usageAccess(AppOpsManager.MODE_ALLOWED)
    val now = 1_000_000L
    // Messages came forward a minute ago; the link tap pauses it and resumes Lynket.
    resumed("com.google.android.apps.messaging", now - 60_000)
    paused("com.google.android.apps.messaging", now - 400)
    resumed(application.packageName, now - 300)

    val source = appDetectionManager.detectLinkSource(now)

    assertThat(source.nonFiltered).isEqualTo("com.google.android.apps.messaging")
    assertThat(source.filtered).isEqualTo("com.google.android.apps.messaging")
  }

  @Test
  fun theLatestEventOfAnotherAppWinsAndLynketsOwnEventsAreSkipped() {
    usageAccess(AppOpsManager.MODE_ALLOWED)
    val now = 1_000_000L
    paused("com.google.android.calendar", now - 5_000)
    stopped("com.google.android.apps.messaging", now - 600)
    paused(application.packageName, now - 200)
    stopped(application.packageName, now - 100)

    val source = appDetectionManager.detectLinkSource(now)

    assertThat(source.nonFiltered).isEqualTo("com.google.android.apps.messaging")
    assertThat(source.filtered).isEqualTo("com.google.android.apps.messaging")
  }

  @Test
  fun anAppOlderThanTheLookbackIsNotTheLinkSource() {
    usageAccess(AppOpsManager.MODE_ALLOWED)
    val now = 1_000_000L
    resumed("com.google.android.calendar", now - 60_000)

    val source = appDetectionManager.detectLinkSource(now)

    assertThat(source.nonFiltered).isEmpty()
    assertThat(source.filtered).isEmpty()
  }

  @Test
  fun withoutUsageAccessNoAppIsDetected() {
    usageAccess(AppOpsManager.MODE_IGNORED)
    val now = 1_000_000L
    resumed("com.google.android.calendar", now - 1_000)

    val source = appDetectionManager.detectLinkSource(now)

    assertThat(source.nonFiltered).isEmpty()
    assertThat(source.filtered).isEmpty()
  }

  @Test
  fun withNoTimeGivenTheWindowEndsAtTheAppClock() {
    usageAccess(AppOpsManager.MODE_ALLOWED)
    resumed("com.google.android.calendar", CLOCK_NOW - 60_000)
    resumed("com.google.android.deskclock", CLOCK_NOW - 2_000)

    val source = appDetectionManager.detectLinkSource()

    assertThat(source.nonFiltered).isEqualTo("com.google.android.deskclock")
    assertThat(source.filtered).isEqualTo("com.google.android.deskclock")
  }

  @Test
  fun withNoTimeGivenAnAppResumedOnlyBeforeTheWindowIsIgnored() {
    usageAccess(AppOpsManager.MODE_ALLOWED)
    resumed("com.google.android.calendar", CLOCK_NOW - 60_000)

    val source = appDetectionManager.detectLinkSource()

    assertThat(source.nonFiltered).isEmpty()
    assertThat(source.filtered).isEmpty()
  }

  private companion object {
    /** The app clock of this test, far from the device time, so a system-time read would miss every event. */
    const val CLOCK_NOW = 5_000_000L
  }
}
