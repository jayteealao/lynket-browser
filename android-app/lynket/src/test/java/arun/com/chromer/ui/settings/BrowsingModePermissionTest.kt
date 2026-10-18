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

package arun.com.chromer.ui.settings

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.readBoolean
import arun.com.chromer.settings.Preferences
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.events.EventBus
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import javax.inject.Inject

/**
 * Browsing mode on the settings store: web heads without the overlay permission explain it first
 * and stay off; a grant from the system screen turns them on, then notifications are asked for.
 * Native bubbles show their warning, then the notification explainer.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class BrowsingModePermissionTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Inject lateinit var repository: UserPreferencesRepository

  @Inject lateinit var preferences: Preferences

  private lateinit var viewModel: SettingsViewModel

  @Before
  fun setUp() {
    hiltRule.inject()
    runBlocking { repository.clearAll() }
    val application = RuntimeEnvironment.getApplication() as Application
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    viewModel = SettingsViewModel(application, repository, preferences, mockk<TabsManager>(relaxed = true), EventBus())
    composeRule.setContent {
      val values by viewModel.values.collectAsStateWithLifecycle()
      val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
      BrowsingModeContent(
        values = values,
        showNativeBubbles = true,
        onBack = {},
        onMode = { mode ->
          viewModel.chooseMode(mode, Settings.canDrawOverlays(composeRule.activity), notificationsAllowed(composeRule.activity))
        },
        onSet = viewModel::set,
      )
      SettingsDialogHost(dialogs.firstOrNull(), viewModel)
    }
  }

  private fun string(id: Int) = composeRule.activity.getString(id)

  private fun nativeBubbles() = repository.current().readBoolean(SettingsKeyTable.NATIVE_BUBBLES, false)

  /** Store writes finish on an IO thread and resume on the main looper, so the looper is run between checks. */
  private fun awaitStore(condition: () -> Boolean) {
    repeat(500) {
      composeRule.waitForIdle()
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      Thread.sleep(10)
    }
    error("The settings store did not reach the expected value")
  }

  private fun awaitWebHeads(on: Boolean) = awaitStore { preferences.webHeads() == on }

  @Test
  fun withoutThePermissionTheExplainerShowsAndWebHeadsStayOff() {
    ShadowSettings.setCanDrawOverlays(false)
    composeRule.onNodeWithTag("mode-web-heads").performClick()

    composeRule.onNodeWithText(string(R.string.overlay_explainer_title)).assertExists()
    composeRule.waitForIdle()
    assertThat(preferences.webHeads()).isFalse()
  }

  @Test
  fun notNowChangesNothing() {
    ShadowSettings.setCanDrawOverlays(false)
    composeRule.onNodeWithTag("mode-web-heads").performClick()
    composeRule.onNodeWithText(string(R.string.not_now)).performClick()

    composeRule.onNodeWithText(string(R.string.overlay_explainer_title)).assertDoesNotExist()
    assertThat(shadowOf(composeRule.activity).nextStartedActivity).isNull()
    assertThat(preferences.webHeads()).isFalse()
  }

  @Test
  fun grantOpensTheSettingAndAGrantTurnsWebHeadsOn() {
    ShadowSettings.setCanDrawOverlays(false)
    composeRule.onNodeWithTag("mode-web-heads").performClick()
    composeRule.onNodeWithText(string(R.string.grant)).performClick()

    val shadow = shadowOf(composeRule.activity)
    val request = shadow.nextStartedActivityForResult
    assertThat(request.intent.action).isEqualTo(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)

    ShadowSettings.setCanDrawOverlays(true)
    shadow.receiveResult(request.intent, Activity.RESULT_CANCELED, Intent())
    awaitWebHeads(true)
    assertThat(nativeBubbles()).isFalse()
  }

  @Test
  fun withThePermissionWebHeadsTurnOnAtOnce() {
    ShadowSettings.setCanDrawOverlays(true)
    composeRule.onNodeWithTag("mode-web-heads").performClick()

    composeRule.onNodeWithText(string(R.string.overlay_explainer_title)).assertDoesNotExist()
    awaitWebHeads(true)
  }

  @Test
  fun withoutNotificationsAGrantAsksForThem() {
    shadowOf(RuntimeEnvironment.getApplication() as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    ShadowSettings.setCanDrawOverlays(true)
    composeRule.onNodeWithTag("mode-web-heads").performClick()

    awaitWebHeads(true)
    composeRule.onNodeWithText(string(R.string.web_heads_notification_explainer_title)).assertExists()
  }

  @Test
  fun nativeBubblesWarnThenAskForNotifications() {
    shadowOf(RuntimeEnvironment.getApplication() as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    composeRule.onNodeWithTag("mode-native-bubbles").performClick()

    composeRule.onNodeWithText(string(R.string.browsing_mode_native_bubbles_warning)).assertExists()
    awaitStore { nativeBubbles() }
    composeRule.onNodeWithText(string(android.R.string.ok)).performClick()
    composeRule.onNodeWithText(string(R.string.bubbles_notification_explainer_title)).assertExists()
  }

  @Test
  fun slideOverTurnsBothModesOff() {
    ShadowSettings.setCanDrawOverlays(true)
    composeRule.onNodeWithTag("mode-web-heads").performClick()
    awaitWebHeads(true)

    composeRule.onNodeWithTag("mode-slide-over").performClick()
    awaitWebHeads(false)
    assertThat(nativeBubbles()).isFalse()
  }
}
