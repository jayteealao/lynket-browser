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

package arun.com.chromer.ui.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * In-app screens are destinations of one Navigation 3 back stack: pushing, popping, the changelog
 * as a dialog destination, the first-run stack and a malformed link's error state.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LynketBackStackTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val fakes = LynketEntries(
    home = { Text("fake home") },
    history = { Text("fake history") },
    tabs = { Text("fake tabs") },
    intro = { navigator -> FakeIntro(navigator) },
    // Text with links never goes idle inside a Robolectric dialog window; the real changelog has its
    // own golden, and this checks the dialog destination around it.
    changelog = { navigator -> FakeChangelog(navigator) },
  )

  private fun show(vararg keys: NavKey): LynketNavigator {
    val navigator = LynketNavigator(mutableStateListOf(*keys))
    composeRule.setContent {
      ChromerTheme { LynketNavDisplay(navigator = navigator, entries = fakes) }
    }
    return navigator
  }

  @Test
  fun startsOnHomeAndPushesAndPops() {
    val navigator = show(LynketKey.Home)
    composeRule.onNodeWithText("fake home").assertIsDisplayed()

    composeRule.runOnIdle { navigator.navigateToHistory() }
    composeRule.onNodeWithText("fake history").assertIsDisplayed()
    assertThat(navigator.backStack).containsExactly(LynketKey.Home, LynketKey.History).inOrder()

    composeRule.runOnIdle { navigator.navigateToHistory() }
    assertThat(navigator.backStack).hasSize(2) // no duplicate on top

    composeRule.runOnIdle { assertThat(navigator.back()).isTrue() }
    composeRule.onNodeWithText("fake home").assertIsDisplayed()
    assertThat(navigator.backStack).containsExactly(LynketKey.Home)
    composeRule.runOnIdle { assertThat(navigator.back()).isFalse() } // the root is the activity's
  }

  @Test
  fun aMalformedLinkShowsTheLinkErrorState() {
    val navigator = show(LynketKey.Home, LynketKey.Browser("https://"))
    composeRule.onNodeWithTag("link-error").assertIsDisplayed()
    composeRule.onNodeWithText("https://").assertIsDisplayed()
    composeRule.onNodeWithText("Go back").performClick()
    composeRule.runOnIdle { assertThat(navigator.backStack).containsExactly(LynketKey.Home) }
  }

  @Test
  fun theChangelogIsADialogOverHome() {
    val navigator = show(LynketKey.Home, LynketKey.Changelog)
    composeRule.onNodeWithTag("changelog").assertIsDisplayed()
    composeRule.onNodeWithText("fake home").assertExists()
    composeRule.onNodeWithTag("changelog-ok").performClick()
    composeRule.runOnIdle { assertThat(navigator.backStack).containsExactly(LynketKey.Home) }
  }

  @Test
  fun aFirstRunShowsTheIntroOverTheChangelog() {
    val keys = LynketNavigator.initialKeys(isFirstRun = true, showChangelog = true)
    assertThat(keys).containsExactly(LynketKey.Home, LynketKey.Changelog, LynketKey.Intro).inOrder()

    val navigator = show(*keys.toTypedArray())
    composeRule.onNodeWithText("fake intro").assertIsDisplayed()
    composeRule.onNodeWithText("intro done").performClick()
    composeRule.runOnIdle { assertThat(navigator.backStack).containsExactly(LynketKey.Home, LynketKey.Changelog).inOrder() }
    composeRule.onNodeWithTag("changelog").assertIsDisplayed()
  }

  @Test
  fun initialKeysPutALinkOnTop() {
    val link = LynketKey.Browser("https://example.com/?q=ü#x")
    assertThat(LynketNavigator.initialKeys(isFirstRun = false, showChangelog = false, deepLink = link))
      .containsExactly(LynketKey.Home, link).inOrder()
    assertThat(LynketNavigator.initialKeys(isFirstRun = false, showChangelog = false))
      .containsExactly(LynketKey.Home)
  }
}

@androidx.compose.runtime.Composable
private fun FakeIntro(navigator: LynketNavigator) {
  androidx.compose.foundation.layout.Column {
    Text("fake intro")
    androidx.compose.material3.TextButton(onClick = { navigator.remove(LynketKey.Intro) }) { Text("intro done") }
  }
}

@androidx.compose.runtime.Composable
private fun FakeChangelog(navigator: LynketNavigator) {
  androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.testTag("changelog")) {
    Text("fake changelog")
    androidx.compose.material3.TextButton(
      onClick = { navigator.remove(LynketKey.Changelog) },
      modifier = androidx.compose.ui.Modifier.testTag("changelog-ok"),
    ) { Text("ok") }
  }
}
