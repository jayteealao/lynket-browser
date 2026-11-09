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

package arun.com.chromer.bubbles.webheads.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.bubbles.webheads.motion.PageState
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadState
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The ring of a web head as it draws: only the front loading head turns, reduced motion turns
 * nothing, and a finished page closes the ring to the head of today in under 300 ms.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WebHeadRingMotionTest {

  /** The animator duration scale the heads see; 0 is reduced motion. */
  private val motion = object : MotionDurationScale {
    var factor by mutableFloatStateOf(1f)
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

  private fun head(state: PageState, front: Boolean = true) = WebHeadState("https://example.com").apply {
    isMaster = front
    revealed = true
    badgeOnLeft = true
    pageState = state
  }

  private fun show(head: WebHeadState) {
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) { WebHeadBubble(head, WebHeadMotion.HEAD_DP.dp, gestures = null) }
    }
    // Let the head's reveal settle, so only the ring and the fill change from here on.
    composeRule.mainClock.advanceTimeBy(1_000)
  }

  private fun frame(): Bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

  private fun later(ms: Long): Bitmap {
    composeRule.mainClock.advanceTimeBy(ms)
    return frame()
  }

  /** Changes what the head shows, and tells the composition at once, as the app's main thread does. */
  private fun change(block: () -> Unit) {
    block()
    Snapshot.sendApplyNotifications()
  }

  /** A point inside the fill below the letter, where only the fill colour shows. */
  private fun Bitmap.fillPixel(): Int = getPixel(width / 2, height / 2 + height / 6)

  @Test
  fun aLoadingFrontHeadTurnsItsRing() {
    show(head(PageState.Loading))
    val first = frame()
    assertThat(later(550).sameAs(first)).isFalse()
  }

  @Test
  fun aLoadingHeadBehindTheFrontStaysStillAndFaded() {
    val head = head(PageState.Loading, front = false)
    show(head)
    val first = frame()
    assertThat(later(550).sameAs(first)).isTrue()

    change { head.pageState = PageState.Loaded }
    val loaded = later(1_000)
    assertThat(loaded.fillPixel()).isNotEqualTo(first.fillPixel())
  }

  @Test
  fun withReducedMotionALoadingHeadDoesNotTurnAndShowsNoArc() {
    motion.factor = 0f
    show(head(PageState.Loading))
    val first = frame()
    assertThat(later(550).sameAs(first)).isTrue()

    // The same head with motion on draws its arc on the track.
    change { motion.factor = 1f }
    val withArc = later(100)
    assertThat(withArc.sameAs(first)).isFalse()
  }

  @Test
  fun aFinishedPageClosesTheRingToTheHeadOfTodayInUnderThreeHundredMilliseconds() {
    val head = head(PageState.Unknown)
    show(head)
    val today = frame()

    change { head.pageState = PageState.Loading }
    assertThat(later(1_000).sameAs(today)).isFalse()

    change { head.pageState = PageState.Loaded }
    assertThat(later(RING_CLOSE_MS - 50L).sameAs(today)).isFalse()
    // Two frames past the close: one to start the tween, one to land on the head of today.
    assertThat(later(50L + 32L).sameAs(today)).isTrue()
  }

  @Test
  fun aLoadingHeadThatComesToTheFrontStartsToTurn() {
    val head = head(PageState.Loading, front = false)
    show(head)
    val behind = frame()
    assertThat(later(550).sameAs(behind)).isTrue()

    change { head.isMaster = true }
    val front = later(16)
    assertThat(later(550).sameAs(front)).isFalse()
  }
}
