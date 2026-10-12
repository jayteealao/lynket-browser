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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.extenstions.StringResource
import arun.com.chromer.home.CustomTabProviderInfo
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.glide.appicon.ApplicationIcon
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** On a wide window the home is one centred column of at most 680 dp. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w841dp-h701dp-xxhdpi")
class HomeFoldWidthTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun theFeedIsAtMost680DpWideOnAFold() {
    val chrome = CustomTabProviderInfo(
      iconUri = ApplicationIcon.createUri("com.android.chrome"),
      providerDescription = StringResource(R.string.tab_provider_status_message_home, listOf("Chrome")),
      providerReason = StringResource(0),
    )
    composeRule.setContent {
      ChromerTheme {
        HomeScreenContent(state = HomeContentState(recents = emptyList(), providerInfo = chrome), actions = HomeActions())
      }
    }

    val bounds = composeRule.onNodeWithTag("home-feed").getBoundsInRoot()
    assertThat((bounds.right - bounds.left).value).isAtMost(680.dp.value)
    // centred: equal space on both sides
    assertThat(bounds.left.value).isWithin(1f).of(841f - bounds.right.value)
  }
}
