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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.StringResource
import arun.com.chromer.home.CustomTabProviderInfo
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.appicon.ApplicationIcon
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The home's designed states: empty recents, no provider, offline, loading, and the drawer order. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class HomeStatesTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val chrome = CustomTabProviderInfo(
    iconUri = ApplicationIcon.createUri("com.android.chrome"),
    providerDescription = StringResource(R.string.tab_provider_status_message_home, listOf("Chrome")),
    providerReason = StringResource(0),
  )

  private fun show(state: HomeContentState, actions: HomeActions = HomeActions(), drawerOpen: Boolean = false) {
    composeRule.setContent {
      ChromerTheme {
        HomeScreenContent(
          state = state,
          actions = actions,
          drawerState = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed),
        )
      }
    }
  }

  @Test
  fun emptyRecentsShowTheEmptyState() {
    show(HomeContentState(recents = emptyList(), providerInfo = chrome))
    composeRule.onNodeWithTag("empty-state").assertIsDisplayed()
    composeRule.onNodeWithText("Recents appear here").assertIsDisplayed()
  }

  @Test
  fun noProviderShowsTheProviderErrorWithRetryAndChange() {
    var retried = 0
    var changed = 0
    show(
      HomeContentState(recents = emptyList(), providerInfo = chrome.copy(noProviderInstalled = true)),
      HomeActions(onRetryProvider = { retried++ }, onChangeProvider = { changed++ }),
    )
    composeRule.onNodeWithTag("home-provider-error").assertIsDisplayed()
    composeRule.onNodeWithText("No browser for Custom Tabs").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Change").performClick()
    assertThat(retried).isEqualTo(1)
    assertThat(changed).isEqualTo(1)
  }

  @Test
  fun offlineShowsTheSnackbarWithRetry() {
    var retried = 0
    show(
      HomeContentState(recents = emptyList(), providerInfo = chrome, isOnline = false),
      HomeActions(onRetryConnection = { retried++ }),
    )
    composeRule.onNodeWithText("You're offline. Pages open when you reconnect.").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.runOnIdle { assertThat(retried).isEqualTo(1) }
  }

  @Test
  fun loadingRecentsShowSkeletonTiles() {
    show(HomeContentState(recents = null, providerInfo = chrome))
    composeRule.onNodeWithTag("loading-tiles").assertIsDisplayed()
  }

  @Test
  fun recentsShowAsTilesAndOpenOnTap() {
    val opened = mutableListOf<String>()
    val sites = (1..3).map { Website("https://site$it.example/") }
    show(HomeContentState(recents = sites, providerInfo = chrome), HomeActions(onOpenWebsite = { opened += it.url }))
    assertThat(composeRule.onAllNodesWithTag("recent-tile").fetchSemanticsNodes()).hasSize(3)
    composeRule.onAllNodesWithTag("recent-tile").onFirst().performClick()
    assertThat(opened).containsExactly("https://site1.example/")
  }

  @Test
  fun openTabsShowTheTabsCard() {
    show(HomeContentState(recents = emptyList(), providerInfo = chrome, openTabs = 3))
    composeRule.onNodeWithTag("home-tabs-card").assertIsDisplayed()
    composeRule.onNodeWithText("3 open tabs").assertIsDisplayed()
  }

  @Test
  fun theDrawerListsTheLegacyMenuInOrder() {
    assertThat(DrawerItem.entries.map { it.name }).containsExactly(
      "Home", "History", "Tabs", "Settings", "Tips",
      "Intro", "Feedback", "Rate", "Beta", "Share", "About",
    ).inOrder()
    val picked = mutableListOf<DrawerItem>()
    show(HomeContentState(recents = emptyList(), providerInfo = chrome), HomeActions(onDrawerItem = { picked += it }), drawerOpen = true)
    composeRule.onNodeWithTag("home-drawer").assertIsDisplayed()
    composeRule.onNodeWithText("More").assertIsDisplayed()
    composeRule.onNodeWithTag("drawer-item-About").performClick()
    composeRule.runOnIdle { assertThat(picked).containsExactly(DrawerItem.About) }
  }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onFirst() = this[0]
