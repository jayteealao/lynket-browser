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

package arun.com.chromer.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.extenstions.StringResource
import arun.com.chromer.home.CustomTabProviderInfo
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.appicon.ApplicationIcon
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Back with the drawer open closes the drawer and keeps the person on home. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class HomeDrawerBackTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val chrome = CustomTabProviderInfo(
    iconUri = ApplicationIcon.createUri("com.android.chrome"),
    providerDescription = StringResource(R.string.tab_provider_status_message_home, listOf("Chrome")),
    providerReason = StringResource(0),
  )

  @Test
  fun backWithTheDrawerOpenClosesTheDrawerAndNotTheScreen() {
    lateinit var drawerState: DrawerState
    composeRule.setContent {
      drawerState = rememberDrawerState(DrawerValue.Open)
      ChromerTheme {
        HomeScreenContent(
          state = HomeContentState(recents = emptyList(), providerInfo = chrome),
          actions = HomeActions(),
          drawerState = drawerState,
        )
      }
    }
    composeRule.waitForIdle()
    assertThat(drawerState.isOpen).isTrue()

    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeRule.waitForIdle()

    assertThat(drawerState.isClosed).isTrue()
    assertThat(composeRule.activity.isFinishing).isFalse()
  }
}
