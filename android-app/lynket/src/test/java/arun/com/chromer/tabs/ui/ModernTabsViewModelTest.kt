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

package arun.com.chromer.tabs.ui

import android.content.Context
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.MainDispatcherRule
import arun.com.chromer.tabs.CUSTOM_TAB
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.TabsManager.Tab
import arun.com.chromer.tabs.WEB_VIEW
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The Tabs view model: loading with stored sites, reorder only on tap, close, close all. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModernTabsViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(dispatcher)

  private val tabsManager = mockk<TabsManager>(relaxed = true)
  private val websites = mockk<WebsiteRepository>()
  private val context = mockk<Context>()

  private val custom = Tab("https://example.com/?t=a", CUSTOM_TAB)
  private val webView = Tab("https://example.org/?t=b", WEB_VIEW)

  @Before
  fun setUp() {
    coEvery { tabsManager.getActiveTabs() } returns listOf(custom, webView)
    every { websites.getWebsiteReadOnly(custom.url) } returns flowOf(Website("Example", custom.url, null, null, null, null, false, 0, 1))
    every { websites.getWebsiteReadOnly(webView.url) } returns flow { throw IllegalStateException("not stored") }
  }

  private fun viewModel() = ModernTabsViewModel(tabsManager, websites)

  @Test
  fun startsLoadingUntilAsked() {
    assertThat(viewModel().uiState.value).isEqualTo(TabsUiState.Loading)
  }

  @Test
  fun loadsTabsWithTheirStoredSites() = runTest(dispatcher) {
    val vm = viewModel()
    vm.load()
    advanceUntilIdle()
    val state = vm.uiState.value as TabsUiState.Loaded
    assertThat(state.tabs.map { it.url }).containsExactly(custom.url, webView.url).inOrder()
    assertThat(state.tabs[0].website?.title).isEqualTo("Example")
    assertThat(state.tabs[1].website).isNull()
  }

  @Test
  fun aFailedLoadShowsTheError() = runTest(dispatcher) {
    coEvery { tabsManager.getActiveTabs() } throws IllegalStateException("no tasks")
    val vm = viewModel()
    vm.load()
    advanceUntilIdle()
    assertThat(vm.uiState.value).isEqualTo(TabsUiState.Error("no tasks"))
  }

  @Test
  fun openingATabOnlyReordersIt() = runTest(dispatcher) {
    every { tabsManager.reOrderTabByUrl(any(), any(), any()) } returns true
    val vm = viewModel()
    vm.open(context, webView)
    advanceUntilIdle()
    verify { tabsManager.reOrderTabByUrl(context, match { it.url == webView.url }, listOf(TabsManager.WEBVIEW_ACTIVITY)) }
    verify(exactly = 0) { tabsManager.openUrl(any(), any(), any(), any(), any(), any(), any()) }
    verify(exactly = 0) { tabsManager.openBrowsingTab(any(), any(), any(), any(), any(), any()) }
    coVerify(exactly = 0) { tabsManager.getActiveTabs() }
  }

  @Test
  fun aTabWithNoTaskReloadsTheList() = runTest(dispatcher) {
    every { tabsManager.reOrderTabByUrl(any(), any(), any()) } returns false
    val vm = viewModel()
    vm.open(context, custom)
    advanceUntilIdle()
    coVerify(exactly = 1) { tabsManager.getActiveTabs() }
    verify(exactly = 0) { tabsManager.openUrl(any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun closingATabFinishesItsTaskAndReloads() = runTest(dispatcher) {
    val vm = viewModel()
    vm.close(context, custom)
    advanceUntilIdle()
    verify { tabsManager.finishTabByUrl(context, match { it.url == custom.url }, listOf(TabsManager.CUSTOM_TAB_ACTIVITY)) }
    coVerify(exactly = 1) { tabsManager.getActiveTabs() }
  }

  @Test
  fun closeAllAsksThenClosesEveryTab() = runTest(dispatcher) {
    val vm = viewModel()
    vm.load()
    advanceUntilIdle()
    vm.requestCloseAll()
    assertThat((vm.uiState.value as TabsUiState.Loaded).confirmCloseAll).isTrue()
    coVerify(exactly = 0) { tabsManager.closeAllTabs() }
    coEvery { tabsManager.getActiveTabs() } returns emptyList()
    vm.confirmCloseAll()
    advanceUntilIdle()
    coVerify(exactly = 1) { tabsManager.closeAllTabs() }
    assertThat(vm.uiState.value).isEqualTo(TabsUiState.Loaded(emptyList()))
  }

  @Test
  fun closeAllCanBeCancelled() = runTest(dispatcher) {
    val vm = viewModel()
    vm.load()
    advanceUntilIdle()
    vm.requestCloseAll()
    vm.dismissCloseAll()
    assertThat((vm.uiState.value as TabsUiState.Loaded).confirmCloseAll).isFalse()
    coVerify(exactly = 0) { tabsManager.closeAllTabs() }
  }
}
