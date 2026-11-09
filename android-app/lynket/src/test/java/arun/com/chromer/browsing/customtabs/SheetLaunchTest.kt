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

import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_ACTIVITY_HEIGHT_RESIZE_BEHAVIOR
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_ACTIVITY_SIDE_SHEET_BREAKPOINT_DP
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_DISABLE_BACKGROUND_INTERACTION
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_INITIAL_ACTIVITY_HEIGHT_PX
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_INITIAL_ACTIVITY_WIDTH_PX
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.data.preferences.BrowsingMode
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/** The sheet options reach the Custom Tab intent only when the sheet is on. */
@HiltAndroidTest
class SheetLaunchTest : LynketRobolectricSuite() {

  private fun built(options: SheetOptions, height: Int, width: Int = 1400): Pair<Boolean, CustomTabsIntent> {
    val builder = CustomTabsIntent.Builder()
    val applied = SheetLaunch.applyTo(builder, options, height, width)
    return applied to builder.build()
  }

  @Test
  fun theSheetOffLeavesTheIntentWithoutAnySheetExtra() {
    val (applied, intent) = built(SheetOptions(sheet = false, sideSheet = true), 2000)

    assertThat(applied).isFalse()
    assertThat(intent.intent.hasExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX)).isFalse()
    assertThat(intent.intent.hasExtra(EXTRA_ACTIVITY_SIDE_SHEET_BREAKPOINT_DP)).isFalse()
    assertThat(intent.intent.hasExtra(EXTRA_INITIAL_ACTIVITY_WIDTH_PX)).isFalse()
    assertThat(intent.intent.hasExtra(EXTRA_DISABLE_BACKGROUND_INTERACTION)).isFalse()
  }

  @Test
  fun theSheetOnOpensAtHalfTheWindowResizableWithTheAppBehindNotTappableAndTheSideSheetAt600() {
    val (applied, intent) = built(SheetOptions(sheet = true, sideSheet = true), 2000)

    assertThat(applied).isTrue()
    assertThat(intent.intent.getIntExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX, -1)).isEqualTo(1000)
    assertThat(intent.intent.getIntExtra(EXTRA_ACTIVITY_HEIGHT_RESIZE_BEHAVIOR, -1))
      .isEqualTo(CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
    assertThat(intent.intent.getBooleanExtra(EXTRA_DISABLE_BACKGROUND_INTERACTION, false)).isTrue()
    assertThat(intent.intent.getIntExtra(EXTRA_ACTIVITY_SIDE_SHEET_BREAKPOINT_DP, -1)).isEqualTo(600)
  }

  @Test
  fun theSideSheetOnSetsAnInitialWidthOfHalfTheWindowSoTheBrowserCanOpenASideSheet() {
    val (_, intent) = built(SheetOptions(sheet = true, sideSheet = true), 2000, width = 1401)

    assertThat(intent.intent.getIntExtra(EXTRA_INITIAL_ACTIVITY_WIDTH_PX, -1)).isEqualTo(701)
  }

  @Test
  fun theSideSheetOffSetsNoInitialWidth() {
    val (_, intent) = built(SheetOptions(sheet = true, sideSheet = false), 2000, width = 1400)

    assertThat(intent.intent.hasExtra(EXTRA_INITIAL_ACTIVITY_WIDTH_PX)).isFalse()
  }

  @Test
  fun anOddWindowHeightIsHalvedUpwards() {
    val (_, intent) = built(SheetOptions(sheet = true, sideSheet = true), 2001)

    assertThat(intent.intent.getIntExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX, -1)).isEqualTo(1001)
  }

  @Test
  fun theSideSheetOffSetsABreakpointNoWindowReaches() {
    val (applied, intent) = built(SheetOptions(sheet = true, sideSheet = false), 2000)

    assertThat(applied).isTrue()
    assertThat(intent.intent.getIntExtra(EXTRA_ACTIVITY_SIDE_SHEET_BREAKPOINT_DP, -1))
      .isEqualTo(SheetLaunch.NO_SIDE_SHEET_BREAKPOINT_DP)
  }

  @Test
  fun noWindowHeightLeavesTheIntentAsItWas() {
    val (applied, intent) = built(SheetOptions(sheet = true, sideSheet = true), 0)

    assertThat(applied).isFalse()
    assertThat(intent.intent.hasExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX)).isFalse()
    assertThat(intent.intent.hasExtra(EXTRA_DISABLE_BACKGROUND_INTERACTION)).isFalse()
  }

  @Test
  fun theOptionsFollowTheStoredKeysInSlideOver() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.SlideOver)
    assertThat(SheetLaunch.options(preferences)).isEqualTo(SheetOptions(sheet = false, sideSheet = true))

    preferences.customTabSheet(true)
    preferences.customTabSideSheet(false)
    assertThat(SheetLaunch.options(preferences)).isEqualTo(SheetOptions(sheet = true, sideSheet = false))
  }

  @Test
  fun theSheetIsOffInWebHeadsAndNativeBubblesEvenWithTheKeyOn() {
    clearPreferences()
    preferences.customTabSheet(true)

    preferences.browsingMode(BrowsingMode.WebHeads)
    assertThat(SheetLaunch.options(preferences).sheet).isFalse()

    preferences.browsingMode(BrowsingMode.NativeBubbles)
    assertThat(SheetLaunch.options(preferences).sheet).isFalse()
  }
}
