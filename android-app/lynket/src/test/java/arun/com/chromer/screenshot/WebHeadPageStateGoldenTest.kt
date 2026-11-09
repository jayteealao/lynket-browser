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

package arun.com.chromer.screenshot

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.bubbles.webheads.motion.PageState
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadState
import arun.com.chromer.bubbles.webheads.ui.WebHeadBubble
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of a web head in each page state: unknown, loading, loaded and failed, in the light and
 * dark themes at the normal and small sizes; the front head of a full stack loading; a loading
 * head with reduced motion; and a loading head behind the front. The clock is held, so a turning
 * ring is drawn where it starts, at the top.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WebHeadPageStateGoldenTest {

  /** The animator duration scale the head sees; 0 is reduced motion. */
  private val motion = object : MotionDurationScale {
    var factor = 1f
    override val scaleFactor: Float get() = factor
  }

  @get:Rule
  val composeRule = createComposeRule(effectContext = motion)

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  @Before
  fun holdTheClock() {
    composeRule.mainClock.autoAdvance = false
  }

  private fun head(state: PageState, front: Boolean = true, count: Int = 3) = WebHeadState("https://lemire.me/blog").apply {
    isMaster = front
    revealed = true
    badgeCount = count
    badgeOnLeft = true
    pageState = state
  }

  @Composable
  private fun OnSurface(content: @Composable () -> Unit) {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceDim).padding(16.dp)) { content() }
  }

  private fun capture(mode: ThemeMode, state: WebHeadState, size: Float = WebHeadMotion.HEAD_DP) {
    composeRule.setContent {
      ChromerTheme(themeMode = mode) {
        OnSurface { WebHeadBubble(state, size.dp, gestures = null) }
      }
    }
    composeRule.onRoot().captureRoboImage()
  }

  @Test fun headState_unknown_light_normal() = capture(ThemeMode.Light, head(PageState.Unknown))
  @Test fun headState_unknown_light_small() = capture(ThemeMode.Light, head(PageState.Unknown), WebHeadMotion.SMALL_HEAD_DP)
  @Test fun headState_unknown_dark_normal() = capture(ThemeMode.Dark, head(PageState.Unknown))
  @Test fun headState_unknown_dark_small() = capture(ThemeMode.Dark, head(PageState.Unknown), WebHeadMotion.SMALL_HEAD_DP)

  @Test fun headState_loading_light_normal() = capture(ThemeMode.Light, head(PageState.Loading))
  @Test fun headState_loading_light_small() = capture(ThemeMode.Light, head(PageState.Loading), WebHeadMotion.SMALL_HEAD_DP)
  @Test fun headState_loading_dark_normal() = capture(ThemeMode.Dark, head(PageState.Loading))
  @Test fun headState_loading_dark_small() = capture(ThemeMode.Dark, head(PageState.Loading), WebHeadMotion.SMALL_HEAD_DP)

  @Test fun headState_loaded_light_normal() = capture(ThemeMode.Light, head(PageState.Loaded))
  @Test fun headState_loaded_light_small() = capture(ThemeMode.Light, head(PageState.Loaded), WebHeadMotion.SMALL_HEAD_DP)
  @Test fun headState_loaded_dark_normal() = capture(ThemeMode.Dark, head(PageState.Loaded))
  @Test fun headState_loaded_dark_small() = capture(ThemeMode.Dark, head(PageState.Loaded), WebHeadMotion.SMALL_HEAD_DP)

  @Test fun headState_failed_light_normal() = capture(ThemeMode.Light, head(PageState.Failed))
  @Test fun headState_failed_light_small() = capture(ThemeMode.Light, head(PageState.Failed), WebHeadMotion.SMALL_HEAD_DP)
  @Test fun headState_failed_dark_normal() = capture(ThemeMode.Dark, head(PageState.Failed))
  @Test fun headState_failed_dark_small() = capture(ThemeMode.Dark, head(PageState.Failed), WebHeadMotion.SMALL_HEAD_DP)

  /** The front head of a full stack, still loading, with the widest badge. */
  @Test fun headState_worst_light() = capture(ThemeMode.Light, head(PageState.Loading, count = 99))
  @Test fun headState_worst_dark() = capture(ThemeMode.Dark, head(PageState.Loading, count = 99))

  @Test
  fun headState_reducedMotion_light() {
    motion.factor = 0f
    capture(ThemeMode.Light, head(PageState.Loading))
  }

  @Test
  fun headState_reducedMotion_dark() {
    motion.factor = 0f
    capture(ThemeMode.Dark, head(PageState.Loading))
  }

  /** A loading head behind the front: a still arc, no badge. */
  @Test fun headState_behind_light() = capture(ThemeMode.Light, head(PageState.Loading, front = false))
}
