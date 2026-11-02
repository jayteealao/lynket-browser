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

package arun.com.chromer.bubbles.webheads.qs

import androidx.datastore.preferences.core.booleanPreferencesKey
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.settings.NATIVE_BUBBLES_PREFERENCE
import arun.com.chromer.settings.Preferences
import arun.com.chromer.ui.settings.BrowsingMode
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric

/** The Web heads tile turns web heads on as the only browsing mode, or goes back to slide-over. */
@HiltAndroidTest
class WebHeadTileTest : LynketRobolectricSuite() {

  private lateinit var tile: WebHeadTile

  @Before
  fun buildTile() {
    clearPreferences()
    tile = Robolectric.buildService(WebHeadTile::class.java).create().get()
  }

  private fun stored() = runBlocking { userPreferencesRepository.fresh() }

  @Test
  fun fromSlideOverTheTileTurnsWebHeadsOnAndThenBackToSlideOver() {
    preferences.browsingMode(BrowsingMode.SlideOver)
    assertThat(tile.preference()).isFalse()

    tile.togglePreference()
    assertThat(preferences.browsingMode()).isEqualTo(BrowsingMode.WebHeads)
    assertThat(tile.preference()).isTrue()

    tile.togglePreference()
    assertThat(preferences.browsingMode()).isEqualTo(BrowsingMode.SlideOver)
    assertThat(tile.preference()).isFalse()
  }

  @Test
  fun fromNativeBubbleModeTheTileTurnsWebHeadsOnAndNativeBubblesOff() {
    preferences.browsingMode(BrowsingMode.NativeBubbles)
    assertThat(tile.preference()).isFalse()

    tile.togglePreference()

    val values = stored()
    assertThat(values[booleanPreferencesKey(Preferences.WEB_HEAD_ENABLED)]).isTrue()
    assertThat(values[booleanPreferencesKey(NATIVE_BUBBLES_PREFERENCE)]).isFalse()
    assertThat(tile.preference()).isTrue()
  }
}
