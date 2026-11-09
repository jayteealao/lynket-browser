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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.StringResource
import arun.com.chromer.home.CustomTabProviderInfo
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.home.HomeActions
import arun.com.chromer.ui.home.HomeContentState
import arun.com.chromer.ui.home.HomeScreenContent
import arun.com.chromer.ui.home.navigatingWith
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.appicon.ApplicationIcon
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A tap that would open or close a screen while a screen transition runs is dropped: two taps on
 * History, or History and then Tabs, inside one transition leave one new screen on the back stack.
 * Once the transition settles, taps work again. The home is wired as the app wires it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TapGuardTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val chrome = CustomTabProviderInfo(
    iconUri = ApplicationIcon.createUri("com.android.chrome"),
    providerDescription = StringResource(R.string.tab_provider_status_message_home, listOf("Chrome")),
    providerReason = StringResource(0),
  )

  private val homeState = HomeContentState(
    recents = listOf(Website("https://example.com/a")),
    providerInfo = chrome,
    openTabs = 1,
  )

  private fun entries(guarded: Boolean) = LynketEntries(
    home = { navigator ->
      val actions = if (guarded) {
        HomeActions().navigatingWith(navigator, LocalContext.current)
      } else {
        HomeActions(onHistory = { navigator.navigateToHistory() }, onTabs = { navigator.navigateToTabs() })
      }
      HomeScreenContent(state = homeState, actions = actions)
    },
    history = { Text("fake history") },
    tabs = { Text("fake tabs") },
    intro = {},
    changelog = {},
  )

  private fun show(guarded: Boolean, vararg keys: NavKey): LynketNavigator {
    val navigator = LynketNavigator(mutableStateListOf(*keys))
    composeRule.setContent {
      ChromerTheme { LynketNavDisplay(navigator = navigator, entries = entries(guarded)) }
    }
    composeRule.waitForIdle()
    return navigator
  }

  private fun frames(count: Int) = repeat(count) {
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.waitForIdle()
  }

  private fun settle() {
    composeRule.mainClock.autoAdvance = true
    composeRule.waitForIdle()
  }

  private fun tabsButton() = composeRule.onNode(
    hasText("Tabs") and hasClickAction() and hasAnyAncestor(hasTestTag("home-tabs-card")),
  )

  /** History, then History again and the Tabs button two frames later, inside the fade-through. */
  private fun tapThreeTimesInOneTransition() {
    composeRule.mainClock.autoAdvance = false
    composeRule.onNodeWithTag("home-history").performClick()
    frames(2)
    composeRule.onNodeWithTag("home-history").performClick()
    tabsButton().performClick()
    settle()
  }

  @Test
  fun tapsDuringATransitionPushOneScreen() {
    val navigator = show(guarded = true, LynketKey.Home)

    tapThreeTimesInOneTransition()

    assertThat(navigator.backStack).containsExactly(LynketKey.Home, LynketKey.History).inOrder()
  }

  @Test
  fun aSettledScreenStillTakesTaps() {
    val navigator = show(guarded = true, LynketKey.Home)
    tapThreeTimesInOneTransition()
    composeRule.runOnUiThread { navigator.back() }
    settle()
    assertThat(navigator.backStack).containsExactly(LynketKey.Home)

    composeRule.onNodeWithTag("home-history").performClick()
    settle()

    assertThat(navigator.backStack).containsExactly(LynketKey.Home, LynketKey.History).inOrder()
  }

  @Test
  fun twoSettingsRowsInOneTransitionOpenOneScreen() {
    val navigator = show(guarded = true, LynketKey.Home, LynketKey.Settings)
    composeRule.mainClock.autoAdvance = false

    composeRule.onNodeWithTag("row-about").performClick()
    frames(2)
    composeRule.onNodeWithTag("row-browsing-mode").performClick()
    settle()

    assertThat(navigator.backStack).containsExactly(LynketKey.Home, LynketKey.Settings, LynketKey.About).inOrder()
  }

  @Test
  fun withoutTheGuardTheSameTapsPushTwoScreens() {
    val navigator = show(guarded = false, LynketKey.Home)

    tapThreeTimesInOneTransition()

    assertThat(navigator.backStack).containsExactly(LynketKey.Home, LynketKey.History, LynketKey.Tabs).inOrder()
  }
}
