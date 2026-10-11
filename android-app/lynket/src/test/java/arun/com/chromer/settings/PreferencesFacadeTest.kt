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

package arun.com.chromer.settings

import arun.com.chromer.LynketRobolectricSuite
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Every reader of [Preferences] (tiles, receivers, the link path) sees each write at once,
 * whether the facade or the Compose settings screen made it.
 */
@HiltAndroidTest
class PreferencesFacadeTest : LynketRobolectricSuite() {

  @Test
  fun aFacadeWriteIsReadBackAtOnce() {
    preferences.webHeads(true)
    assertThat(preferences.webHeads()).isTrue()

    preferences.webHeads(false)
    assertThat(preferences.webHeads()).isFalse()
  }

  @Test
  fun aWriteFromTheComposeSettingsScreenReachesTheFacade() {
    runBlocking { userPreferencesRepository.setWebHeadsEnabled(true) }
    assertThat(preferences.webHeads()).isTrue()

    runBlocking { userPreferencesRepository.setAnimationType(2) }
    assertThat(preferences.animationType()).isEqualTo(2)
  }

  @Test
  fun theStaticAccessorReturnsTheInjectedInstance() {
    assertThat(Preferences.get(application)).isSameInstanceAs(preferences)
  }
}
