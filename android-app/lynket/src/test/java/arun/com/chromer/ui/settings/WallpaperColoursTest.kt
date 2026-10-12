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

package arun.com.chromer.ui.settings

import android.app.Application
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.ui.theme.chromerColorScheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Colours from the wallpaper on Android 12 (API 31) and on the target level (API 36): the switch
 * shows, starts off after an update, and when on the scheme is the system's dynamic one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [31, 36], application = Application::class)
class WallpaperColoursTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val context: Application get() = ApplicationProvider.getApplicationContext()

  @Test
  fun theSwitchShowsStartsOffAndWritesTheKey() {
    assertThat(Build.VERSION.SDK_INT).isAtLeast(Build.VERSION_CODES.S)
    assertThat(wallpaperColoursAvailable()).isTrue()
    val writes = mutableListOf<Pair<String, Any?>>()
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        LookAndFeelContent(
          values = SettingsValues.from(emptyPreferences(), context),
          showWallpaperColours = wallpaperColoursAvailable(),
          onBack = {},
          onSet = { k, v -> writes += k to v },
        )
      }
    }
    val tag = "row-${SettingsKeyTable.USE_WALLPAPER_COLOURS}"
    composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
    composeRule.onNodeWithTag(tag).assertIsOff().performClick()
    assertThat(writes).containsExactly(SettingsKeyTable.USE_WALLPAPER_COLOURS to true)
  }

  @Test
  fun withTheOptionOnTheSchemeIsTheWallpapers() {
    val light = chromerColorScheme(context, dark = false, black = false, useWallpaperColours = true, primaryColour = null)
    val dark = chromerColorScheme(context, dark = true, black = false, useWallpaperColours = true, primaryColour = null)
    assertThat(light.primary).isEqualTo(dynamicLightColorScheme(context).primary)
    assertThat(dark.primary).isEqualTo(dynamicDarkColorScheme(context).primary)
  }

  @Test
  fun withTheOptionOffTheSeedSchemeStays() {
    val seed = chromerColorScheme(context, dark = false, black = false, useWallpaperColours = false, primaryColour = null)
    val again = chromerColorScheme(context, dark = false, black = false, useWallpaperColours = false, primaryColour = null)
    assertThat(seed.primary).isEqualTo(again.primary)
    assertThat(seed.primary).isNotEqualTo(dynamicLightColorScheme(context).primary)
  }

  @Test
  fun beforeAndroid12ThereIsNoSwitch() {
    assertThat(wallpaperColoursAvailable(sdk = Build.VERSION_CODES.R)).isFalse()
  }
}
