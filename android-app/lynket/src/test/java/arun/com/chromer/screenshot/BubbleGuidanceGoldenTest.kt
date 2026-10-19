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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.bubbles.system.BubbleRefusal
import arun.com.chromer.bubbles.system.BubbleSetting
import arun.com.chromer.ui.browsing.BubbleRefusedMessage
import arun.com.chromer.ui.settings.BrowsingModeContent
import arun.com.chromer.ui.settings.SettingsDialog
import arun.com.chromer.ui.settings.SettingsDialogActions
import arun.com.chromer.ui.settings.SettingsDialogContent
import arun.com.chromer.ui.settings.SettingsKeyTable
import arun.com.chromer.ui.settings.SettingsValues
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the refused-bubble message over a page (light and dark), Browsing mode with native
 * bubbles and the bubble setting row, and the bubble setting explainer. Record with
 * `./gradlew :lynket:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class BubbleGuidanceGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val context: Application get() = ApplicationProvider.getApplicationContext()

  private val nativeBubbles: SettingsValues
    get() = SettingsValues.from(emptyPreferences(), context).with(
      SettingsKeyTable.NATIVE_BUBBLES to true,
      SettingsKeyTable.WEB_HEAD_ENABLED to false,
    )

  private val noActions = SettingsDialogActions({}, {}, {}, {}, {}, {})

  private fun message(theme: ThemeMode) {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      ChromerTheme(themeMode = theme) {
        // The page under the message window.
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            BubbleRefusedMessage(BubbleRefusal.BUBBLES_OFF, onOpenSettings = {}, onDone = {})
          }
        }
      }
    }
    // Past the snackbar's entry, well before its timeout.
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.waitForIdle()
    composeRule.onRoot().captureRoboImage()
  }

  private fun capture(dialog: Boolean = false, content: @Composable () -> Unit) {
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) { content() }
      }
    }
    composeRule.waitForIdle()
    if (dialog) composeRule.onNode(isDialog()).captureRoboImage() else composeRule.onRoot().captureRoboImage()
  }

  @Test fun refused_message_light() = message(ThemeMode.Light)

  @Test fun refused_message_dark() = message(ThemeMode.Dark)

  @Test fun browsing_mode_selected_conversations() = capture {
    BrowsingModeContent(
      nativeBubbles,
      showNativeBubbles = true,
      onBack = {},
      onMode = {},
      onSet = { _, _ -> },
      bubbleSetting = BubbleSetting.SELECTED,
    )
  }

  @Test fun bubble_setting_explainer() = capture(dialog = true) {
    BrowsingModeContent(nativeBubbles, showNativeBubbles = true, onBack = {}, onMode = {}, onSet = { _, _ -> }, bubbleSetting = BubbleSetting.SELECTED)
    SettingsDialogContent(SettingsDialog.BubbleSettingExplainer(BubbleSetting.SELECTED), noActions)
  }
}
