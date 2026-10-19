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

package arun.com.chromer.history

import android.content.Context
import androidx.paging.PagingData
import app.cash.turbine.test
import arun.com.chromer.data.history.ModernHistoryRepository
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.MainDispatcherRule
import arun.com.chromer.settings.Preferences
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.events.Event
import arun.com.chromer.util.events.EventBus
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The History view model: the switch rule, search, remove and undo, delete all, opening rows. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModernHistoryViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(dispatcher)

  private val repository = mockk<ModernHistoryRepository>(relaxed = true)
  private val preferences = mockk<Preferences>(relaxed = true)
  private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
  private val eventBus = mockk<EventBus>(relaxed = true)
  private val tabsManager = mockk<TabsManager>(relaxed = true)

  private val site = Website("Example", "https://example.com/a", null, null, null, "https://example.com/amp/a", false, 1_000L, 3)

  @Before
  fun setUp() {
    every { preferences.historyDisabled() } returns false
    every { preferences.fullIncognitoMode() } returns false
    every { preferences.customTabPackage() } returns "com.android.chrome"
    every { repository.getPagedHistory() } returns flowOf(PagingData.from(listOf(site)))
    every { repository.getPagedSearch(any()) } returns flowOf(PagingData.from(listOf(site)))
  }

  private fun viewModel() = ModernHistoryViewModel(repository, preferences, preferencesRepository, eventBus, tabsManager)

  @Test
  fun theStateStartsFromTheStoredSwitches() {
    every { preferences.historyDisabled() } returns true
    every { preferences.fullIncognitoMode() } returns true
    val state = viewModel().uiState.value
    assertThat(state.historyEnabled).isFalse()
    assertThat(state.incognito).isTrue()
    assertThat(state.providerPackage).isEqualTo("com.android.chrome")
  }

  @Test
  fun historyOnTurnsIncognitoOff() = runTest(dispatcher) {
    every { preferences.historyDisabled() } returns true
    every { preferences.fullIncognitoMode() } returns true
    val vm = viewModel()
    vm.setHistoryEnabled(true)
    advanceUntilIdle()
    coVerify {
      preferencesRepository.setValues(mapOf(Preferences.INCOGNITO_MODE to false, Preferences.FULL_INCOGNITO_MODE to false))
    }
    verify { eventBus.tryEmit(Event.SettingsEvent.ProviderChanged) }
    assertThat(vm.uiState.value.historyEnabled).isTrue()
    assertThat(vm.uiState.value.incognito).isFalse()
  }

  @Test
  fun historyOffLeavesIncognitoAlone() = runTest(dispatcher) {
    val vm = viewModel()
    vm.setHistoryEnabled(false)
    advanceUntilIdle()
    coVerify { preferencesRepository.setValues(mapOf(Preferences.INCOGNITO_MODE to true)) }
    verify(exactly = 0) { eventBus.tryEmit(any()) }
    assertThat(vm.uiState.value.historyEnabled).isFalse()
  }

  @Test
  fun incognitoOnTurnsHistoryOffAndExplainsItself() = runTest(dispatcher) {
    val vm = viewModel()
    vm.setIncognito(true)
    advanceUntilIdle()
    coVerify {
      preferencesRepository.setValues(mapOf(Preferences.FULL_INCOGNITO_MODE to true, Preferences.INCOGNITO_MODE to true))
    }
    verify { eventBus.tryEmit(Event.SettingsEvent.ProviderChanged) }
    assertThat(vm.uiState.value.historyEnabled).isFalse()
    assertThat(vm.uiState.value.dialog).isEqualTo(HistoryDialog.IncognitoExplanation)
    vm.dismissDialog()
    assertThat(vm.uiState.value.dialog).isEqualTo(HistoryDialog.None)
  }

  @Test
  fun incognitoOffChangesTheProviderOnly() = runTest(dispatcher) {
    every { preferences.fullIncognitoMode() } returns true
    val vm = viewModel()
    vm.setIncognito(false)
    advanceUntilIdle()
    coVerify { preferencesRepository.setValues(mapOf(Preferences.FULL_INCOGNITO_MODE to false)) }
    verify { eventBus.tryEmit(Event.SettingsEvent.ProviderChanged) }
    assertThat(vm.uiState.value.dialog).isEqualTo(HistoryDialog.None)
  }

  @Test
  fun aQuerySwitchesToTheFullSearchAfterAPause() = runTest(dispatcher) {
    val vm = viewModel()
    // Work in the background scope only runs on runCurrent or advanceTimeBy, not advanceUntilIdle.
    backgroundScope.launch { vm.items.collect { } }
    runCurrent()
    verify(exactly = 1) { repository.getPagedHistory() }
    vm.openSearch()
    vm.setQuery("exa")
    advanceTimeBy(100)
    verify(exactly = 0) { repository.getPagedSearch(any()) }
    vm.setQuery("exam")
    advanceTimeBy(400)
    verify(exactly = 1) { repository.getPagedSearch("exam") }
    vm.closeSearch()
    runCurrent()
    verify(exactly = 2) { repository.getPagedHistory() }
    assertThat(vm.uiState.value.searchOpen).isFalse()
    assertThat(vm.uiState.value.query).isEmpty()
  }

  @Test
  fun aRemovedRowCanBePutBack() = runTest(dispatcher) {
    coEvery { repository.delete(site) } returns true
    val vm = viewModel()
    vm.events.test {
      vm.delete(site)
      assertThat(awaitItem()).isEqualTo(HistoryEvent.Deleted(site))
    }
    vm.undo(site)
    advanceUntilIdle()
    coVerify { repository.restore(site) }
  }

  @Test
  fun aFailedRemoveOffersNoUndo() = runTest(dispatcher) {
    coEvery { repository.delete(site) } returns false
    val vm = viewModel()
    vm.events.test {
      vm.delete(site)
      advanceUntilIdle()
      expectNoEvents()
    }
  }

  @Test
  fun deleteAllAsksThenReportsTheCount() = runTest(dispatcher) {
    coEvery { repository.deleteAll() } returns 500
    val vm = viewModel()
    vm.requestClearAll()
    assertThat(vm.uiState.value.dialog).isEqualTo(HistoryDialog.ClearConfirm)
    coVerify(exactly = 0) { repository.deleteAll() }
    vm.events.test {
      vm.confirmClearAll()
      assertThat(awaitItem()).isEqualTo(HistoryEvent.Cleared(500))
    }
    assertThat(vm.uiState.value.dialog).isEqualTo(HistoryDialog.None)
  }

  @Test
  fun rowsOpenThroughTheBrowsingPath() = runTest(dispatcher) {
    val context = mockk<Context>()
    val vm = viewModel()
    vm.open(context, site)
    verify { tabsManager.openUrl(context, site, any(), any(), any(), any(), any(), any()) }
    vm.toggleBookmark(site)
    advanceUntilIdle()
    coVerify { repository.toggleBookmark("https://example.com/a") }
  }
}
