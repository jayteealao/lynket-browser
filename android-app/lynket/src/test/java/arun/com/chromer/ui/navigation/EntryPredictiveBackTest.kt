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

package arun.com.chromer.ui.navigation

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The predictive back gesture on the link screen drags it down with the finger over a still home,
 * a cancelled gesture puts it back, and a finished one pops it. Another screen keeps the gesture
 * motion of the navigation library, which scales the screen instead of moving it down.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EntryPredictiveBackTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val homeColour = Color(0xFF1B6B5A)

  private val fakes = LynketEntries(
    home = { Box(Modifier.fillMaxSize().background(homeColour).testTag("fake-home")) },
    history = { Box(Modifier.fillMaxSize().background(Color(0xFFB0306A)).testTag("fake-history")) },
    tabs = {},
    intro = {},
    changelog = {},
  )

  private val dispatcher get() = composeRule.activity.onBackPressedDispatcher

  private fun show(vararg keys: NavKey): LynketNavigator {
    val navigator = LynketNavigator(mutableStateListOf(*keys))
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) { LynketNavDisplay(navigator = navigator, entries = fakes) }
    }
    composeRule.waitForIdle()
    return navigator
  }

  private fun top(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

  private fun rootHeight() = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()

  private fun gesture(progress: Float) {
    composeRule.runOnUiThread {
      dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 1000f, 0f, BackEventCompat.EDGE_LEFT))
      dispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 1000f, progress, BackEventCompat.EDGE_LEFT))
    }
    composeRule.waitForIdle()
  }

  @Test
  fun theGestureDragsTheLinkScreenDownAndACancelPutsItBack() {
    val navigator = show(LynketKey.Home, LynketKey.Browser("https://"))
    val restingTop = top("link-error")
    val height = rootHeight()

    gesture(0.5f)
    val offset = top("link-error") - restingTop
    assertWithMessage("offset under the gesture").that(offset).isGreaterThan(8f)
    assertWithMessage("offset under the gesture").that(offset).isLessThan(height)
    val pixel = composeRule.onRoot().captureToImage().asAndroidBitmap().getPixel(4, (offset - 8f).toInt())
    assertWithMessage("home above the screen").that(pixel).isEqualTo(homeColour.toArgb())

    composeRule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
    composeRule.waitForIdle()
    assertThat(top("link-error")).isWithin(1f).of(restingTop)
    assertThat(navigator.backStack).containsExactly(LynketKey.Home, LynketKey.Browser("https://")).inOrder()

    gesture(0.5f)
    composeRule.runOnUiThread { dispatcher.onBackPressed() }
    composeRule.waitForIdle()
    assertThat(navigator.backStack).containsExactly(LynketKey.Home)
    assertThat(composeRule.onAllNodesWithTag("link-error").fetchSemanticsNodes()).isEmpty()
  }

  @Test
  fun anotherScreenKeepsTheLibraryGestureMotion() {
    show(LynketKey.Home, LynketKey.History)
    val height = rootHeight()

    gesture(0.5f)
    val bounds = composeRule.onNodeWithTag("fake-history").fetchSemanticsNode().boundsInRoot
    // Scaled about its centre: smaller than the screen, and its top moved by no more than the
    // scale accounts for (the library scales down to 0.7 at most), not slid down.
    assertThat(bounds.height).isLessThan(height - 1f)
    assertThat(bounds.top).isGreaterThan(0f)
    assertThat(bounds.top).isAtMost((height - bounds.height) / 2f + 1f)

    composeRule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
    composeRule.waitForIdle()
  }
}
