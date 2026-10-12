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

package arun.com.chromer.ui.tabs

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.ARTICLE
import arun.com.chromer.tabs.CUSTOM_TAB
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.ui.ModernTabsViewModel
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Tabs screen with a fake tab manager: a tap brings the task forward and never opens a page,
 * a tab that is gone only reloads the list, swipe and the close icon finish the task, close all
 * asks first, the empty state, and a reload whenever the screen comes back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TabsScreenTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val tabsManager = mockk<TabsManager>(relaxed = true)
  private val websites = mockk<WebsiteRepository>()

  private val tabs = listOf(
    TabsManager.Tab("https://example.com/?t=a", CUSTOM_TAB),
    TabsManager.Tab("https://example.org/?t=b", ARTICLE),
  )

  private fun show(open: List<TabsManager.Tab> = tabs) {
    coEvery { tabsManager.getActiveTabs() } returns open
    every { websites.getWebsiteReadOnly(any()) } answers { flowOf(Website("Site ${firstArg<String>().last()}", firstArg(), null, null, null, null, false, 0, 1)) }
    val viewModel = ModernTabsViewModel(tabsManager, websites)
    composeRule.setContent { ChromerTheme(themeMode = ThemeMode.Light) { TabsScreen(onBack = {}, closeIcon = true, viewModel = viewModel) } }
    composeRule.waitForIdle()
  }

  @Test
  fun aTapBringsTheTaskForwardAndOpensNothing() {
    every { tabsManager.reOrderTabByUrl(any(), any(), any()) } returns true
    show()
    composeRule.onNodeWithText("Site a").performClick()
    verify { tabsManager.reOrderTabByUrl(any(), match { it.url == "https://example.com/?t=a" }, listOf(TabsManager.CUSTOM_TAB_ACTIVITY)) }
    verify(exactly = 0) { tabsManager.openUrl(any(), any(), any(), any(), any(), any(), any()) }
    verify(exactly = 0) { tabsManager.openBrowsingTab(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun aTabThatIsGoneOnlyReloadsTheList() {
    every { tabsManager.reOrderTabByUrl(any(), any(), any()) } returns false
    show()
    coVerify(exactly = 1) { tabsManager.getActiveTabs() }
    composeRule.onNodeWithText("Site b").performClick()
    composeRule.waitForIdle()
    coVerify(exactly = 2) { tabsManager.getActiveTabs() }
    verify(exactly = 0) { tabsManager.openUrl(any(), any(), any(), any(), any(), any(), any()) }
    verify(exactly = 0) { tabsManager.openBrowsingTab(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun theCloseIconAndASwipeFinishTheTask() {
    show()
    composeRule.onAllNodesWithTag("tab-close").onFirst().performClick()
    verify { tabsManager.finishTabByUrl(any(), match { it.url == "https://example.com/?t=a" }, listOf(TabsManager.CUSTOM_TAB_ACTIVITY)) }
    composeRule.onAllNodesWithTag("tab-row")[1].performTouchInput { swipeRight() }
    composeRule.waitForIdle()
    verify { tabsManager.finishTabByUrl(any(), match { it.url == "https://example.org/?t=b" }, listOf(TabsManager.ARTICLE_ACTIVITY)) }
  }

  @Test
  fun closeAllAsksFirst() {
    show()
    composeRule.onNodeWithTag("tabs-close-all").performClick()
    composeRule.onNodeWithText("Are you sure?").assertExists()
    coVerify(exactly = 0) { tabsManager.closeAllTabs() }
    composeRule.onNodeWithTag("close-all-confirm").performClick()
    composeRule.waitForIdle()
    coVerify(exactly = 1) { tabsManager.closeAllTabs() }
  }

  @Test
  fun noOpenTabsShowsTheEmptyState() {
    show(open = emptyList())
    composeRule.onNodeWithText("No tabs open").assertExists()
    composeRule.onNodeWithTag("tabs-close-all").assertDoesNotExist()
  }

  @Test
  fun rowsShowTheOldTypeLabels() {
    show()
    composeRule.onNodeWithText("Custom Tab · https://example.com/?t=a").assertExists()
    composeRule.onNodeWithText("Article mode · https://example.org/?t=b").assertExists()
  }

  @Test
  fun theListReloadsWhenTheScreenComesBack() {
    show()
    coVerify(exactly = 1) { tabsManager.getActiveTabs() }
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    composeRule.waitForIdle()
    coVerify(exactly = 2) { tabsManager.getActiveTabs() }
  }
}
