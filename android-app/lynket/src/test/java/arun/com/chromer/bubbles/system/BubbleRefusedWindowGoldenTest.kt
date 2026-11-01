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

package arun.com.chromer.bubbles.system

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants.EXTRA_KEY_BUBBLE_REFUSAL
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/**
 * The refused-bubble window as drawn: the message for each cause in the light and dark themes, with
 * a three-button navigation bar under it, and the longest sentence at the largest text size. Record
 * with `./gradlew :lynket:recordRoborazziDebug --tests '*BubbleRefusedWindowGoldenTest*'`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class BubbleRefusedWindowGoldenTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createEmptyComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  @Inject
  lateinit var preferences: Preferences

  private val application: Application get() = RuntimeEnvironment.getApplication()

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  private fun capture(refusal: BubbleRefusal, mode: ThemeMode) {
    preferences.themeMode(mode.ordinal)
    val controller = Robolectric.buildActivity(
      BubbleRefusedActivity::class.java,
      Intent(application, BubbleRefusedActivity::class.java).putExtra(EXTRA_KEY_BUBBLE_REFUSAL, refusal.name),
    ).setup()
    composeRule.mainClock.autoAdvance = false
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.waitForIdle()
    // A 48 dp three-button navigation bar, which Robolectric does not report by itself.
    val bar = (48 * application.resources.displayMetrics.density).toInt()
    val insets = WindowInsetsCompat.Builder()
      .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bar))
      .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
      .build()
    ViewCompat.dispatchApplyWindowInsets(controller.get().window.decorView, insets)
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.waitForIdle()
    controller.get().window.decorView.captureRoboImage()
  }

  @Test
  fun notifications_off_light() = capture(BubbleRefusal.NOTIFICATIONS_OFF, ThemeMode.Light)

  @Test
  fun notifications_off_dark() = capture(BubbleRefusal.NOTIFICATIONS_OFF, ThemeMode.Dark)

  @Test
  fun bubbles_off_light() = capture(BubbleRefusal.BUBBLES_OFF, ThemeMode.Light)

  @Test
  fun bubbles_off_dark() = capture(BubbleRefusal.BUBBLES_OFF, ThemeMode.Dark)

  @Test
  fun not_shown_light() = capture(BubbleRefusal.NOT_SHOWN, ThemeMode.Light)

  @Test
  fun not_shown_dark() = capture(BubbleRefusal.NOT_SHOWN, ThemeMode.Dark)

  @Test
  fun not_shown_light_font_200() {
    RuntimeEnvironment.setFontScale(2f)
    capture(BubbleRefusal.NOT_SHOWN, ThemeMode.Light)
  }
}
