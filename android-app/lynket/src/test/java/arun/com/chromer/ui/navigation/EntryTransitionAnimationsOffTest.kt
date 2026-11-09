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
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * With system animations off, the screen a link opens is in place on the first frame that shows it
 * and is gone within three frames of back: there is no slide.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EntryTransitionAnimationsOffTest {

  @get:Rule
  val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
    override val scaleFactor = 0f
  })

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val fakes = LynketEntries(
    home = { Text("fake home") },
    history = {},
    tabs = {},
    intro = {},
    changelog = {},
  )

  private fun errorShown() = composeRule.onAllNodesWithTag("link-error").fetchSemanticsNodes().isNotEmpty()

  private fun errorTop() = composeRule.onNodeWithTag("link-error").fetchSemanticsNode().boundsInRoot.top

  /** One frame: the change is applied and drawn once. */
  private fun oneFrame() {
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.waitForIdle()
  }

  @Test
  fun theLinkScreenIsInPlaceOnItsFirstFrameAndGoneRightAfterBack() {
    val navigator = LynketNavigator(mutableStateListOf(LynketKey.Home, LynketKey.Browser("https://")))
    composeRule.setContent { ChromerTheme { LynketNavDisplay(navigator = navigator, entries = fakes) } }
    composeRule.waitForIdle()
    val restingTop = errorTop()
    composeRule.runOnUiThread { navigator.back() }
    composeRule.waitForIdle()
    assertThat(errorShown()).isFalse()

    composeRule.mainClock.autoAdvance = false
    composeRule.runOnUiThread { navigator.navigate(LynketKey.Browser("https://")) }
    // The change is composed and drawn within the next frames; the first frame that shows the
    // screen shows it in place. With the slide on, that frame would still be near the bottom edge.
    var frames = 0
    while (!errorShown() && frames < 3) { oneFrame(); frames++ }
    assertThat(errorTop()).isWithin(0.5f).of(restingTop)

    // Back: gone within three frames (48 ms), where the slide would take about 260 ms.
    composeRule.runOnUiThread { navigator.back() }
    frames = 0
    while (errorShown() && frames < 3) { oneFrame(); frames++ }
    assertThat(errorShown()).isFalse()
    assertThat(navigator.backStack).containsExactly(LynketKey.Home)
  }
}
