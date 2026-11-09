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
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The screen a link opens slides up from the bottom edge with no fade over a home that stays as it
 * is, and back slides it down the same way; another screen keeps the fade-through. The main clock
 * is paused and the frames are sampled every 16 ms. A refused address keeps the link error screen,
 * which is what the person sees of the slide.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EntryTransitionTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val clock get() = composeRule.mainClock

  private val homeColour = Color(0xFF1B6B5A)
  private val historyColour = Color(0xFFB0306A)

  private val fakes = LynketEntries(
    home = { Box(Modifier.fillMaxSize().background(homeColour).testTag("fake-home")) },
    history = { Box(Modifier.fillMaxSize().background(historyColour).testTag("fake-history")) },
    tabs = {},
    intro = {},
    changelog = {},
  )

  private fun show(vararg keys: NavKey): LynketNavigator {
    val navigator = LynketNavigator(mutableStateListOf(*keys))
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) { LynketNavDisplay(navigator = navigator, entries = fakes) }
    }
    composeRule.waitForIdle()
    clock.autoAdvance = false
    return navigator
  }

  private fun frame() {
    clock.advanceTimeBy(16)
    composeRule.waitForIdle()
  }

  private fun errorShown() = composeRule.onAllNodesWithTag("link-error").fetchSemanticsNodes().isNotEmpty()

  private fun errorTop() = composeRule.onNodeWithTag("link-error").fetchSemanticsNode().boundsInRoot.top

  private fun rootHeight() = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()

  /** The error screen's resting top of its content, read once the display has settled. */
  private fun settledErrorTop(): Float {
    composeRule.waitForIdle()
    return errorTop()
  }

  private fun pixel(x: Int, y: Int): Int = composeRule.onRoot().captureToImage().asAndroidBitmap().getPixel(x, y)

  /** Frames until the error screen is laid out, at most 5. */
  private fun framesUntilShown() {
    repeat(5) {
      frame()
      if (errorShown()) return
    }
    error("the link error screen never showed")
  }

  @Test
  fun aLinkScreenSlidesUpOverAStillHomeWithNoBounce() {
    val navigator = show(LynketKey.Home)
    val height = rootHeight()
    // Where the content rests when the screen is in place: measured on a settled push first.
    composeRule.runOnUiThread { navigator.navigate(LynketKey.Browser("https://")) }
    clock.autoAdvance = true
    val restingTop = settledErrorTop()
    clock.autoAdvance = false
    composeRule.runOnUiThread { navigator.back() }
    clock.autoAdvance = true
    composeRule.waitForIdle()
    clock.autoAdvance = false
    assertThat(errorShown()).isFalse()

    composeRule.runOnUiThread { navigator.navigate(LynketKey.Browser("https://")) }
    framesUntilShown()
    // The screen's own top edge: the content moves with it.
    val offsets = mutableListOf(errorTop() - restingTop)
    var elapsed = 0
    while (elapsed < 400) {
      val offset = offsets.last()
      if (offset > 8f) {
        val x = 4
        assertWithMessage("home above the screen at offset $offset").that(pixel(x, (offset - 8f).toInt()))
          .isEqualTo(homeColour.toArgb())
      }
      frame()
      elapsed += 16
      offsets += errorTop() - restingTop
      if (elapsed == 304) {
        assertWithMessage("$offsets").that(offsets.last()).isWithin(1f).of(0f)
      }
    }

    assertWithMessage("$offsets").that(offsets.first()).isAtLeast(height * 0.5f)
    offsets.zipWithNext().forEach { (before, after) ->
      assertWithMessage("$offsets").that(after).isAtMost(before + 0.5f)
    }
    offsets.forEach { assertWithMessage("no bounce past the top: $offsets").that(it).isAtLeast(-0.5f) }
    assertThat(navigator.backStack).containsExactly(LynketKey.Home, LynketKey.Browser("https://")).inOrder()
  }

  @Test
  fun backSlidesTheLinkScreenDownOverAStillHome() {
    val navigator = show(LynketKey.Home, LynketKey.Browser("https://"))
    val restingTop = settledErrorTop()

    composeRule.runOnUiThread { navigator.back() }
    val offsets = mutableListOf<Float>()
    var frames = 0
    while (errorShown() && frames < 40) {
      frame()
      frames++
      if (!errorShown()) break
      val offset = errorTop() - restingTop
      offsets += offset
      if (offset > 8f && offset < rootHeight()) {
        assertWithMessage("home above the screen at offset $offset").that(pixel(4, (offset - 8f).toInt()))
          .isEqualTo(homeColour.toArgb())
      }
    }

    assertWithMessage("the screen left within 40 frames: $offsets").that(errorShown()).isFalse()
    assertWithMessage("the screen moved down: $offsets").that(offsets.any { it > 8f }).isTrue()
    offsets.zipWithNext().forEach { (before, after) ->
      assertWithMessage("$offsets").that(after).isAtLeast(before - 0.5f)
    }
    clock.autoAdvance = true
    composeRule.waitForIdle()
    assertThat(navigator.backStack).containsExactly(LynketKey.Home)
  }

  @Test
  fun anotherScreenKeepsTheFadeThrough() {
    val navigator = show(LynketKey.Home)
    composeRule.runOnUiThread { navigator.navigate(LynketKey.History) }
    // Sampled over the 200 ms of the change: some frames show neither screen at full strength, as
    // the home fades out and History fades in. A slide would show only the two colours.
    val samples = List(16) {
      frame()
      pixel(200, 600)
    }

    assertWithMessage("$samples").that(
      samples.any { it != homeColour.toArgb() && it != historyColour.toArgb() },
    ).isTrue()
    clock.autoAdvance = true
    composeRule.waitForIdle()
    assertThat(pixel(200, 600)).isEqualTo(historyColour.toArgb())
  }
}
