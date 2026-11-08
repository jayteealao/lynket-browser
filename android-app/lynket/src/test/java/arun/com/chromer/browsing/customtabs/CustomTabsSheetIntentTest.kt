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

package arun.com.chromer.browsing.customtabs

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_ACTIVITY_HEIGHT_RESIZE_BEHAVIOR
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_ACTIVITY_SIDE_SHEET_BREAKPOINT_DP
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_DISABLE_BACKGROUND_INTERACTION
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_INITIAL_ACTIVITY_HEIGHT_PX
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.data.preferences.BrowsingMode
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * The Custom Tab that a Slide over link opens: full screen and unchanged with the sheet off, a
 * sheet launched for a result with the sheet on.
 */
@HiltAndroidTest
class CustomTabsSheetIntentTest : LynketRobolectricSuite() {

  private val url = "https://example.com/page"
  private val provider = "com.android.chrome"
  private lateinit var activity: Activity

  @Before
  fun installProviderAndOpenAnActivity() {
    clearPreferences()
    shadowOf(application.packageManager).installPackage(PackageInfo().apply { packageName = provider })
    preferences.customTabPackage(provider)
    preferences.browsingMode(BrowsingMode.SlideOver)
    activity = Robolectric.buildActivity(Activity::class.java).setup().get()
  }

  private fun tabs() = CustomTabs(activity).forUrl(url)

  @Test
  fun theDefaultKeysBuildTheFullScreenIntentAndAPlainLaunch() {
    val intent = tabs().buildIntent().intent
    assertThat(intent.hasExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX)).isFalse()
    assertThat(intent.hasExtra(EXTRA_ACTIVITY_HEIGHT_RESIZE_BEHAVIOR)).isFalse()
    assertThat(intent.hasExtra(EXTRA_ACTIVITY_SIDE_SHEET_BREAKPOINT_DP)).isFalse()
    assertThat(intent.hasExtra(EXTRA_DISABLE_BACKGROUND_INTERACTION)).isFalse()

    assertThat(tabs().launch()).isFalse()
    val shadow = shadowOf(activity)
    val started = shadow.nextStartedActivityForResult
    assertThat(started.intent.`package`).isEqualTo(provider)
    assertThat(started.intent.dataString).isEqualTo(url)
    // A plain start: Robolectric records it with request code -1.
    assertThat(started.requestCode).isEqualTo(-1)
    assertThat(started.intent.hasExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX)).isFalse()
  }

  @Test
  fun theSheetOnInSlideOverLaunchesASheetForAResult() {
    preferences.customTabSheet(true)
    val height = SheetLaunch.windowHeightPx(activity)
    assertThat(height).isGreaterThan(1)

    val intent = tabs().buildIntent().intent
    assertThat(intent.getIntExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX, -1)).isEqualTo((height + 1) / 2)
    assertThat(intent.getBooleanExtra(EXTRA_DISABLE_BACKGROUND_INTERACTION, false)).isTrue()
    assertThat(intent.getIntExtra(EXTRA_ACTIVITY_SIDE_SHEET_BREAKPOINT_DP, -1)).isEqualTo(600)

    assertThat(tabs().launch()).isTrue()
    val started = shadowOf(activity).nextStartedActivityForResult
    assertThat(started.requestCode).isEqualTo(CustomTabs.SHEET_REQUEST)
    assertThat(started.intent.`package`).isEqualTo(provider)
    assertThat(started.intent.dataString).isEqualTo(url)
    assertThat(started.intent.getIntExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX, -1)).isEqualTo((height + 1) / 2)
    // A new-task launch would return its result at once and lose the sheet.
    assertThat(started.intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)
  }

  @Test
  fun theSideSheetOffSetsNoReachableBreakpoint() {
    preferences.customTabSheet(true)
    preferences.customTabSideSheet(false)

    val intent = tabs().buildIntent().intent

    assertThat(intent.getIntExtra(EXTRA_ACTIVITY_SIDE_SHEET_BREAKPOINT_DP, -1))
      .isEqualTo(SheetLaunch.NO_SIDE_SHEET_BREAKPOINT_DP)
  }
}
