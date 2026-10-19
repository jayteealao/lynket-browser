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

package arun.com.chromer.ui.history

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.data.history.ModernHistoryRepository
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.history.HistoryDialog
import arun.com.chromer.history.HistoryUiState
import arun.com.chromer.history.ModernHistoryViewModel
import arun.com.chromer.settings.Preferences
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.util.events.Event
import arun.com.chromer.util.events.EventBus
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * The History screen with fakes: the old controls in their order, the empty, loading and error
 * states, the two switches, remove with undo, delete all with its count, search and opening a row.
 */
@RunWith(AndroidJUnit4::class)
// A phone-sized window: the default Robolectric screen is too short for the switches and the first day.
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryScreenTest {

  @get:Rule(order = 0)
  val uiDispatcherReset = UiDispatcherResetRule()

  @get:Rule(order = 1)
  val composeRule = createComposeRule()

  private val log = mutableListOf<String>()

  private val actions = HistoryActions(
    onHistoryEnabled = { log += "history:$it" },
    onIncognito = { log += "incognito:$it" },
    onOpen = { log += "open:${it.url}" },
    onOpenAmp = { log += "amp:${it.url}" },
    onDelete = { log += "delete:${it.url}" },
    onToggleBookmark = { log += "bookmark:${it.url}" },
    onClearAll = { log += "clear" },
    onConfirmClearAll = { log += "confirm" },
    onDismissDialog = { log += "dismiss" },
  )

  private fun show(data: Flow<PagingData<HistoryListItem>>, state: HistoryUiState = HistoryUiState()) {
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        HistoryContent(
          state = state,
          items = data.collectAsLazyPagingItems(),
          providerName = "Chrome",
          actions = actions,
          now = TEST_NOW,
          timeZone = TEST_ZONE,
        )
      }
    }
    composeRule.waitForIdle()
  }

  private fun top(tag: String) = composeRule.onAllNodesWithTag(tag).onFirst().getBoundsInRoot().top

  @Test
  fun theOldControlsKeepTheirOrder() {
    show(flowOf(loaded(testSites)))
    val history = top("history-switch")
    val incognito = top("incognito-switch")
    val firstDay = top("history-day")
    val firstRow = top("history-row")
    assertThat(history).isLessThan(incognito)
    assertThat(incognito).isLessThan(firstDay)
    assertThat(firstDay).isLessThan(firstRow)
    composeRule.onNodeWithTag("history-clear-all").assertExists()
    composeRule.onNodeWithText("Today").assertExists()
    composeRule.onNodeWithText("Yesterday").assertExists()
    composeRule.onNodeWithText("https://seed.example/page-001").assertExists()
    composeRule.onAllNodesWithTag("history-amp").assertCountEquals(1)
  }

  @Test
  fun anEmptyHistoryShowsTheEmptyStateAndNoDeleteButton() {
    show(flowOf(loaded(emptyList())))
    composeRule.onNodeWithText("No history yet").assertExists()
    composeRule.onNodeWithTag("history-clear-all").assertDoesNotExist()
    composeRule.onNodeWithTag("history-switch").assertExists()
  }

  @Test
  fun aSlowLoadShowsTheSkeleton() {
    show(flowOf(withRefresh(LoadState.Loading)))
    composeRule.onNodeWithTag("loading-list").assertExists()
    composeRule.onNodeWithText("No history yet").assertDoesNotExist()
  }

  @Test
  fun aFailedLoadOffersRetry() {
    show(flowOf(withRefresh(LoadState.Error(IllegalStateException("disk")))))
    composeRule.onNodeWithTag("error-state").assertExists()
    composeRule.onNodeWithText("Retry").assertExists()
  }

  @Test
  fun theSwitchesShowTheirStateAndReportTaps() {
    show(flowOf(loaded(testSites)), HistoryUiState(historyEnabled = true, incognito = false))
    composeRule.onNodeWithTag("history-switch").assertIsOn()
    composeRule.onNodeWithTag("incognito-switch").assertIsOff()
    composeRule.onNodeWithTag("history-switch").performClick()
    composeRule.onNodeWithTag("incognito-switch").performClick()
    assertThat(log).containsExactly("history:false", "incognito:true").inOrder()
  }

  @Test
  fun aSidewaysSwipeRemovesTheRow() {
    show(flowOf(loaded(testSites)))
    composeRule.onAllNodesWithTag("history-row").onFirst().performTouchInput { swipeLeft() }
    composeRule.waitForIdle()
    assertThat(log).contains("delete:https://seed.example/page-001")
  }

  @Test
  fun theRowMenuBookmarksAndRemoves() {
    show(flowOf(loaded(testSites)))
    composeRule.onAllNodesWithTag("history-row-menu").onFirst().performClick()
    composeRule.onNodeWithTag("history-menu-bookmark").performClick()
    composeRule.onAllNodesWithTag("history-row-menu").onFirst().performClick()
    composeRule.onNodeWithTag("history-menu-remove").performClick()
    assertThat(log).containsExactly("bookmark:https://seed.example/page-001", "delete:https://seed.example/page-001").inOrder()
  }

  @Test
  fun deleteAllAsksFirst() {
    show(flowOf(loaded(testSites)), HistoryUiState(dialog = HistoryDialog.ClearConfirm))
    composeRule.onNodeWithText("Are you sure?").assertExists()
    composeRule.onNodeWithText("Yes, delete").performClick()
    assertThat(log).containsExactly("confirm")
  }

  @Test
  fun aSearchWithNoMatchSaysSo() {
    show(flowOf(loaded(emptyList())), HistoryUiState(searchOpen = true, query = "zebra"))
    composeRule.onNodeWithTag("history-search").assertExists()
    composeRule.onNodeWithTag("history-no-match").assertExists()
    composeRule.onNodeWithText("No history matches \"zebra\"").assertExists()
  }

  // With the view model: the effects that need the store, the browsing path or the snackbar.

  private val repository = mockk<ModernHistoryRepository>(relaxed = true)
  private val preferences = mockk<Preferences>(relaxed = true)
  private val tabsManager = mockk<TabsManager>(relaxed = true)
  private val eventBus = mockk<EventBus>(relaxed = true)
  private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)

  private fun showWithViewModel(): ModernHistoryViewModel {
    every { preferences.historyDisabled() } returns false
    every { preferences.fullIncognitoMode() } returns false
    every { preferences.customTabPackage() } returns null
    every { repository.getPagedHistory() } returns flowOf(PagingData.from(testSites))
    every { repository.getPagedSearch(any()) } answers {
      val q = firstArg<String>()
      flowOf(PagingData.from(testSites.filter { it.title!!.contains(q, ignoreCase = true) }))
    }
    val viewModel = ModernHistoryViewModel(repository, preferences, preferencesRepository, eventBus, tabsManager)
    composeRule.setContent { ChromerTheme(themeMode = ThemeMode.Light) { HistoryScreen(onBack = {}, viewModel = viewModel) } }
    composeRule.waitForIdle()
    return viewModel
  }

  @Test
  fun deleteAllShowsHowManyRowsWentAway() {
    coEvery { repository.deleteAll() } returns 6
    showWithViewModel()
    composeRule.onNodeWithTag("history-clear-all").performClick()
    composeRule.onNodeWithTag("clear-confirm").performClick()
    composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText("6 items deleted.")).fetchSemanticsNodes().isNotEmpty() }
    coVerify { repository.deleteAll() }
  }

  @Test
  fun undoPutsARemovedRowBack() {
    coEvery { repository.delete(any()) } returns true
    coEvery { repository.restore(any()) } returns true
    showWithViewModel()
    composeRule.onAllNodesWithTag("history-row-menu").onFirst().performClick()
    composeRule.onNodeWithTag("history-menu-remove").performClick()
    composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty() }
    composeRule.onNodeWithText("Removed from history").assertExists()
    composeRule.onNodeWithText("Undo").performClick()
    composeRule.waitForIdle()
    coVerify { repository.restore(match<Website> { it.url == "https://seed.example/page-001" && it.count == testSites[0].count }) }
  }

  @Test
  fun searchShowsOnlyMatchingRows() {
    showWithViewModel()
    composeRule.onNodeWithTag("history-search-toggle").performClick()
    composeRule.onNodeWithTag("history-search").performTextInput("kotlin")
    // The search waits 300 ms after typing on the main looper, which only moves when advanced.
    ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
    composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("history-row").fetchSemanticsNodes().size == 1 }
    composeRule.onNodeWithText("Kotlin coroutines guide").assertExists()
    verify { repository.getPagedSearch("kotlin") }
  }

  @Test
  fun aRowOpensThroughTheBrowsingPath() {
    showWithViewModel()
    composeRule.onNodeWithText("Compose BOM mapping").performClick()
    verify { tabsManager.openUrl(any(), match { it.url == "https://seed.example/page-001" }, any(), any(), any(), any(), any(), any()) }
    composeRule.onNodeWithTag("history-amp").performClick()
    verify { tabsManager.openUrl(any(), match { it.url == "https://seed.example/amp/page-002" }, any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun incognitoOnExplainsItselfAndChangesTheProvider() {
    showWithViewModel()
    composeRule.onNodeWithTag("incognito-switch").performClick()
    composeRule.onNodeWithTag("incognito-dialog").assertExists()
    composeRule.waitForIdle()
    verify { eventBus.tryEmit(Event.SettingsEvent.ProviderChanged) }
    coVerify {
      preferencesRepository.setValues(mapOf(Preferences.FULL_INCOGNITO_MODE to true, Preferences.INCOGNITO_MODE to true))
    }
    composeRule.onNodeWithTag("history-switch").assertIsOff()
  }
}
