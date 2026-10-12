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

package arun.com.chromer.ui.theme

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import arun.com.chromer.data.preferences.SettingsSchema
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The seed schemes, the black variant and the person's colour as primary. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ChromerThemeTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val context: Application get() = ApplicationProvider.getApplicationContext()

  private fun schemeIn(mode: ThemeMode, primary: Color? = null): ColorScheme {
    lateinit var scheme: ColorScheme
    composeRule.setContent {
      ChromerTheme(themeMode = mode, primaryColour = primary) { scheme = MaterialTheme.colorScheme }
    }
    composeRule.waitForIdle()
    return scheme
  }

  @Test
  fun light_mode_uses_the_indigo_seed() {
    val scheme = schemeIn(ThemeMode.Light)
    assertThat(scheme.primary).isEqualTo(Color(0xFF515B92))
    assertThat(scheme.tertiary).isEqualTo(Color(0xFF9A4059))
  }

  @Test
  fun dark_mode_uses_the_dark_seed_roles() {
    val scheme = schemeIn(ThemeMode.Dark)
    assertThat(scheme.primary).isEqualTo(Color(0xFFBAC3FF))
    assertThat(scheme.surface).isNotEqualTo(Color(0xFF050508))
  }

  @Test
  fun black_mode_darkens_every_surface() {
    val scheme = schemeIn(ThemeMode.Black)
    assertThat(scheme.background).isEqualTo(Color(0xFF050508))
    assertThat(scheme.surface).isEqualTo(Color(0xFF050508))
    assertThat(scheme.surfaceContainerHighest).isEqualTo(Color(0xFF1B1B22))
    assertThat(scheme.primary).isEqualTo(Color(0xFFBAC3FF))
  }

  @Test
  fun the_persons_colour_becomes_primary() {
    val teal = Color(0xFF009688)
    val scheme = chromerColorScheme(context, dark = false, black = false, useWallpaperColours = false, primaryColour = teal)
    assertThat(scheme.primary).isNotEqualTo(Color(0xFF515B92))
    assertThat(scheme.tertiary).isEqualTo(Color(0xFF9A4059))
  }

  @Test
  fun no_colour_keeps_the_seed() {
    val scheme = chromerColorScheme(context, dark = true, black = false, useWallpaperColours = false, primaryColour = null)
    assertThat(scheme.primary).isEqualTo(Color(0xFFBAC3FF))
  }

  @Test
  fun the_shapes_follow_the_expressive_scale() {
    assertThat(ChromerShapes.extraLarge).isNotEqualTo(ChromerShapes.small)
  }

  @Test
  fun every_stored_theme_code_maps_to_its_mode() {
    assertThat(themeModeOf(0)).isEqualTo(ThemeMode.Auto)
    assertThat(themeModeOf(1)).isEqualTo(ThemeMode.Light)
    assertThat(themeModeOf(2)).isEqualTo(ThemeMode.Dark)
    assertThat(themeModeOf(3)).isEqualTo(ThemeMode.Black)
    assertThat(themeModeOf(9)).isEqualTo(ThemeMode.Auto)
    assertThat(themeModeOf(-1)).isEqualTo(ThemeMode.Auto)
  }

  @Test
  fun an_update_without_the_setting_keeps_auto_and_no_wallpaper_colours() {
    val settings = themeSettingsOf(emptyPreferences(), context)
    assertThat(settings.themeMode).isEqualTo(ThemeMode.Auto)
    assertThat(settings.useWallpaperColours).isFalse()
  }

  @Test
  fun the_stored_theme_is_read_for_each_mode() {
    ThemeMode.entries.forEach { mode ->
      val stored = preferencesOf(intPreferencesKey(SettingsSchema.THEME_MODE) to mode.ordinal)
      assertThat(themeSettingsOf(stored, context).themeMode).isEqualTo(mode)
    }
  }

  @Test
  fun auto_follows_the_system() {
    // The test device is in light mode, so Auto draws the light scheme.
    val light = chromerColorScheme(context, dark = false, black = false, useWallpaperColours = false, primaryColour = null)
    val auto = schemeIn(ThemeMode.Auto)
    assertThat(auto.primary).isEqualTo(light.primary)
    assertThat(auto.surface).isEqualTo(light.surface)
  }
}
