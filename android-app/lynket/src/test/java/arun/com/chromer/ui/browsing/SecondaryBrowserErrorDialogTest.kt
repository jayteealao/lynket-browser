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

package arun.com.chromer.ui.browsing

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The error a blacklisted app's link shows when the secondary browser cannot open it: the same
 * title, message and buttons as before; "Launch settings" opens Lynket from the top; either
 * button closes the dialog and the activity that received the link.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SecondaryBrowserErrorDialogTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val message = "Could not launch the link with the browser you have chosen. Would you like to edit this setting?"

  private fun show() {
    composeRule.runOnUiThread {
      showSecondaryBrowserError(composeRule.activity, message) { content ->
        ChromerTheme(themeMode = ThemeMode.Light) { content() }
      }
    }
  }

  @Test
  fun theDialogShowsTheTitleMessageAndBothButtons() {
    show()

    composeRule.onNodeWithText("Error launching secondary browser").assertIsDisplayed()
    composeRule.onNodeWithText(message).assertIsDisplayed()
    composeRule.onNodeWithText("Launch settings").assertIsDisplayed()
    composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    assertThat(composeRule.activity.isShowingSecondaryBrowserError).isTrue()
  }

  @Test
  fun launchSettingsOpensLynketFromTheTopAndFinishesTheHost() {
    val activity = composeRule.activity
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(activity.packageName)
    shadowOf(activity.packageManager).addResolveInfoForIntent(
      launcher,
      ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
          packageName = activity.packageName
          name = "arun.com.chromer.MainActivity"
        }
      },
    )
    show()

    composeRule.onNodeWithTag("secondary-browser-launch-settings").performClick()

    val started = shadowOf(activity).nextStartedActivity
    assertThat(started.component?.className).isEqualTo("arun.com.chromer.MainActivity")
    assertThat(started.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP).isNotEqualTo(0)
    assertThat(activity.isFinishing).isTrue()
    assertThat(activity.isShowingSecondaryBrowserError).isFalse()
  }

  @Test
  fun cancelFinishesTheHostAndStartsNothing() {
    val activity = composeRule.activity
    show()

    composeRule.onNodeWithTag("secondary-browser-cancel").performClick()

    assertThat(shadowOf(activity).nextStartedActivity).isNull()
    assertThat(activity.isFinishing).isTrue()
  }
}
