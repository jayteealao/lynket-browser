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

package arun.com.chromer.settings.browsingmode

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** Choosing web heads without the overlay permission explains it first, then turns web heads on. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class BrowsingModePermissionTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createAndroidComposeRule<BrowsingModeActivity>()

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  private fun string(id: Int) = composeRule.activity.getString(id)

  private fun chooseWebHeads() {
    composeRule.runOnUiThread {
      composeRule.activity.onModeClicked(BrowsingModeAdapter.WEB_HEADS, View(composeRule.activity))
    }
    composeRule.waitForIdle()
  }

  @Test
  fun without_the_permission_the_explainer_shows_and_web_heads_stay_off() {
    ShadowSettings.setCanDrawOverlays(false)
    chooseWebHeads()

    composeRule.onNodeWithText(string(R.string.overlay_explainer_title)).assertExists()
    assertThat(Preferences.get(composeRule.activity).webHeads()).isFalse()
  }

  @Test
  fun not_now_changes_nothing() {
    ShadowSettings.setCanDrawOverlays(false)
    chooseWebHeads()
    composeRule.onNodeWithText(string(R.string.not_now)).performClick()

    composeRule.onNodeWithText(string(R.string.overlay_explainer_title)).assertDoesNotExist()
    assertThat(shadowOf(composeRule.activity).nextStartedActivity).isNull()
    assertThat(Preferences.get(composeRule.activity).webHeads()).isFalse()
  }

  @Test
  fun grant_opens_the_setting_and_a_grant_turns_web_heads_on() {
    ShadowSettings.setCanDrawOverlays(false)
    chooseWebHeads()
    composeRule.onNodeWithText(string(R.string.grant)).performClick()

    val shadow = shadowOf(composeRule.activity)
    val request = shadow.nextStartedActivityForResult
    assertThat(request.intent.action).isEqualTo(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)

    ShadowSettings.setCanDrawOverlays(true)
    shadow.receiveResult(request.intent, Activity.RESULT_CANCELED, Intent())
    composeRule.waitForIdle()

    assertThat(Preferences.get(composeRule.activity).webHeads()).isTrue()
  }

  @Test
  fun with_the_permission_web_heads_turn_on_at_once() {
    ShadowSettings.setCanDrawOverlays(true)
    chooseWebHeads()

    composeRule.onNodeWithText(string(R.string.overlay_explainer_title)).assertDoesNotExist()
    assertThat(Preferences.get(composeRule.activity).webHeads()).isTrue()
  }
}
