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

package arun.com.chromer.ui.intro

import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.about.changelog.Changelog
import arun.com.chromer.ui.navigation.LynketKey
import arun.com.chromer.ui.navigation.LynketNavigator
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/** The first-run triggers as the old home used them: the intro once, the changelog per new version. */
@HiltAndroidTest
class FirstRunTest : LynketRobolectricSuite() {

  private fun freshStart(versionCode: Int) = LynketNavigator.initialKeys(
    isFirstRun = preferences.isFirstRun,
    showChangelog = Changelog.shouldShow(preferences, versionCode),
  )

  @Test
  fun aNewInstallShowsTheIntroOnceAndTheChangelogUnderIt() {
    clearPreferences()

    assertThat(freshStart(56)).containsExactly(LynketKey.Home, LynketKey.Changelog, LynketKey.Intro).inOrder()
    assertThat(preferences.isFirstRun).isFalse() // the first read cleared firstrun_3

    assertThat(freshStart(56)).containsExactly(LynketKey.Home)
  }

  @Test
  fun anUpgradeShowsTheChangelogOnlyOnce() {
    clearPreferences()
    preferences.versionCode(55)
    preferences.isFirstRun // a returning person has seen the intro

    assertThat(freshStart(56)).containsExactly(LynketKey.Home, LynketKey.Changelog).inOrder()
    assertThat(preferences.versionCode()).isEqualTo(56)
    assertThat(freshStart(56)).containsExactly(LynketKey.Home)
  }

  @Test
  fun changelogRule() {
    assertThat(Changelog.shouldShow(savedVersionCode = -1, currentVersionCode = 56)).isTrue()
    assertThat(Changelog.shouldShow(savedVersionCode = 55, currentVersionCode = 56)).isTrue()
    assertThat(Changelog.shouldShow(savedVersionCode = 56, currentVersionCode = 56)).isFalse()
    assertThat(Changelog.shouldShow(savedVersionCode = 57, currentVersionCode = 56)).isFalse()
  }
}
