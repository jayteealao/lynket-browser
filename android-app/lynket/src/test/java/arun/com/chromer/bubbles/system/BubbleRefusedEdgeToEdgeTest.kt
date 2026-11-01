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
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants.EXTRA_KEY_BUBBLE_REFUSAL
import arun.com.chromer.ui.browsing.messageRes
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * The refused-bubble window draws behind the system bars: its message stays above the navigation
 * bar, and the bar icons follow the app theme, dark on the light theme and light on the dark and
 * black themes.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 36], application = HiltTestApplication::class)
class BubbleRefusedEdgeToEdgeTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createEmptyComposeRule()

  @Inject
  lateinit var preferences: Preferences

  private val application: Application get() = RuntimeEnvironment.getApplication()

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  /** Opens the message for [refusal] in [mode], with the clock held so the snackbar stays shown. */
  private fun launch(refusal: BubbleRefusal, mode: ThemeMode): ActivityController<BubbleRefusedActivity> {
    preferences.themeMode(mode.ordinal)
    val controller = Robolectric.buildActivity(
      BubbleRefusedActivity::class.java,
      Intent(application, BubbleRefusedActivity::class.java).putExtra(EXTRA_KEY_BUBBLE_REFUSAL, refusal.name),
    ).setup()
    composeRule.mainClock.autoAdvance = false
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.waitForIdle()
    return controller
  }

  private fun close(controller: ActivityController<BubbleRefusedActivity>) {
    controller.pause().stop().destroy()
    composeRule.mainClock.autoAdvance = true
  }

  private fun assertEdgeToEdge(activity: BubbleRefusedActivity, lightIcons: Boolean) {
    val window = activity.window
    val bars = WindowInsetsControllerCompat(window, window.decorView)
    assertThat(bars.isAppearanceLightStatusBars).isEqualTo(lightIcons)
    assertThat(bars.isAppearanceLightNavigationBars).isEqualTo(lightIcons)
    assertThat(window.attributes.layoutInDisplayCutoutMode)
      .isEqualTo(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS)
    if (Build.VERSION.SDK_INT < 35) {
      @Suppress("DEPRECATION") // sdlc-debt: the bar colour is a no-op from API 35; drop this check when the tests stop running API 34
      assertThat(window.statusBarColor).isEqualTo(Color.TRANSPARENT)
    }
  }

  @Test
  fun light_theme_draws_edge_to_edge_with_dark_bar_icons() {
    for (refusal in BubbleRefusal.entries) {
      val controller = launch(refusal, ThemeMode.Light)
      // A light appearance flag means dark icons, for a light surface.
      assertEdgeToEdge(controller.get(), lightIcons = true)
      close(controller)
    }
  }

  @Test
  fun dark_theme_draws_edge_to_edge_with_light_bar_icons() {
    val controller = launch(BubbleRefusal.BUBBLES_OFF, ThemeMode.Dark)
    assertEdgeToEdge(controller.get(), lightIcons = false)
    close(controller)
  }

  @Test
  fun black_theme_has_light_bar_icons() {
    val controller = launch(BubbleRefusal.BUBBLES_OFF, ThemeMode.Black)
    val window = controller.get().window
    val bars = WindowInsetsControllerCompat(window, window.decorView)
    assertThat(bars.isAppearanceLightStatusBars).isFalse()
    assertThat(bars.isAppearanceLightNavigationBars).isFalse()
    close(controller)
  }

  @Test
  fun the_window_keeps_its_bottom_strip_shape() {
    val controller = launch(BubbleRefusal.NOT_SHOWN, ThemeMode.Light)
    val attributes = controller.get().window.attributes
    assertThat(attributes.gravity).isEqualTo(Gravity.BOTTOM)
    assertThat(attributes.height).isEqualTo(ViewGroup.LayoutParams.WRAP_CONTENT)
    assertThat(attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL).isNotEqualTo(0)
    assertThat(attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND).isEqualTo(0)
    close(controller)
  }

  private fun px(dp: Int): Int = (dp * application.resources.displayMetrics.density).toInt()

  /** Gives the window a navigation bar of [height] px, as a three-button bar would. */
  private fun dispatchNavigationBar(activity: BubbleRefusedActivity, height: Int) {
    val insets = WindowInsetsCompat.Builder()
      .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, height))
      .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
      .build()
    ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets)
    composeRule.mainClock.advanceTimeBy(100)
    composeRule.waitForIdle()
  }

  @Test
  fun the_message_sits_above_the_navigation_bar_inset() {
    val controller = launch(BubbleRefusal.NOT_SHOWN, ThemeMode.Light)
    val inset = px(48)
    dispatchNavigationBar(controller.get(), inset)

    val rootHeight = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.height
    val message = composeRule.onNodeWithText(application.getString(BubbleRefusal.NOT_SHOWN.messageRes()))
      .fetchSemanticsNode().boundsInRoot
    val action = composeRule.onNodeWithText(application.getString(R.string.open_settings))
      .fetchSemanticsNode().boundsInRoot
    assertThat(message.bottom).isAtMost(rootHeight - inset + 0.5f)
    assertThat(action.bottom).isAtMost(rootHeight - inset + 0.5f)
    close(controller)
  }

  private fun assertOpenSettingsTarget() {
    for (refusal in BubbleRefusal.entries) {
      val controller = launch(refusal, ThemeMode.Light)
      val target = composeRule.onNodeWithText(application.getString(R.string.open_settings))
        .fetchSemanticsNode().touchBoundsInRoot
      assertThat(target.height).isAtLeast(px(48) - 0.5f)
      assertThat(target.width).isAtLeast(px(48) - 0.5f)
      close(controller)
    }
  }

  @Test
  fun open_settings_keeps_a_48dp_target() {
    assertOpenSettingsTarget()
    // The largest system text size: the sentence wraps and the action keeps its size.
    RuntimeEnvironment.setFontScale(2f)
    assertOpenSettingsTarget()
  }
}
