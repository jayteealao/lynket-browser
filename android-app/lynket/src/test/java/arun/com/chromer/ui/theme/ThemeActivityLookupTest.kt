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

import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.R
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The theme finds the screen's activity, through any context wrapper, and gives its window the
 * app's background and status bar icons that read on the app's light or dark surfaces.
 */
@HiltAndroidTest
@Config(qualifiers = "notnight")
class ThemeActivityLookupTest : LynketRobolectricSuite() {

  @get:Rule(order = 1)
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private var background = 0

  /** Shows the theme from the stored settings around a line of text, and keeps its background. */
  private fun show(applySystemBars: Boolean = true, wrap: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
    composeRule.setContent {
      wrap {
        ChromerThemeFromSettings(applySystemBars = applySystemBars) {
          background = MaterialTheme.colorScheme.background.toArgb()
          Text("Lynket")
        }
      }
    }
    composeRule.waitForIdle()
  }

  private val window get() = composeRule.activity.window
  private fun windowColour() = (window.decorView.background as? ColorDrawable)?.color
  private fun lightStatusBarIcons() = WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars

  @Test
  fun theLightThemeColoursTheWindowAndUsesDarkStatusBarIcons() {
    show()
    assertThat(windowColour()).isEqualTo(background)
    assertThat(lightStatusBarIcons()).isTrue()
  }

  @Config(qualifiers = "night")
  @Test
  fun theDarkThemeColoursTheWindowAndUsesLightStatusBarIcons() {
    show()
    assertThat(windowColour()).isEqualTo(background)
    assertThat(lightStatusBarIcons()).isFalse()
  }

  @Test
  fun theActivityIsFoundThroughAThemedWrapper() {
    show { content ->
      val activity = LocalContext.current
      CompositionLocalProvider(LocalContext provides ContextThemeWrapper(activity, R.style.AppTheme)) { content() }
    }
    assertThat(windowColour()).isEqualTo(background)
    assertThat(lightStatusBarIcons()).isTrue()
  }

  @Test
  fun aContextWithNoActivityChangesNoWindowAndDoesNotThrow() {
    val before = windowColour()
    show { content ->
      val app = LocalContext.current.applicationContext
      CompositionLocalProvider(LocalContext provides ContextThemeWrapper(app, R.style.AppTheme)) { content() }
    }
    assertThat(windowColour()).isEqualTo(before)
    assertThat(lightStatusBarIcons()).isFalse()
  }

  @Test
  fun aScreenThatColoursItsOwnBarsGetsNoWindowChange() {
    val before = windowColour()
    show(applySystemBars = false)
    assertThat(windowColour()).isEqualTo(before)
    assertThat(lightStatusBarIcons()).isFalse()
  }
}
