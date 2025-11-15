/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.tabs.ui

import app.cash.turbine.test
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.MainDispatcherRule
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.TabsManager.Tab
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 8: Unit tests for ModernTabsViewModel (Updated for Coroutines)
 *
 * Tests the ViewModel's:
 * - Tab loading with Kotlin Coroutines
 * - UI state transitions (Loading → Success → Error)
 * - Dialog management (show/hide close all dialog)
 * - Close all tabs functionality
 * - Refresh functionality
 * - Tab count calculation
 * - Website enrichment with error handling
 *
 * Uses:
 * - MockK for mocking TabsManager and WebsiteRepository with coEvery for suspend functions
 * - Turbine for testing StateFlow emissions
 * - Truth for assertions
 * - Coroutines Test for async operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModernTabsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mocked dependencies
    private lateinit var tabsManager: TabsManager
    private lateinit var websiteRepository: WebsiteRepository

    // System under test
    private lateinit var viewModel: ModernTabsViewModel

    @Before
    fun setup() {
        tabsManager = mockk()
        websiteRepository = mockk()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    /**
     * Helper to create ViewModel
     */
    private fun createViewModel(): ModernTabsViewModel {
        return ModernTabsViewModel(
            tabsManager = tabsManager,
            websiteRepository = websiteRepository
        )
    }

    /**
     * Helper to create sample tabs
     */
    private fun createSampleTabs(): List<Tab> {
        return listOf(
            Tab(url = "https://example.com", title = "Example"),
            Tab(url = "https://test.com", title = "Test Site"),
            Tab(url = "https://demo.com", title = "Demo")
        )
    }

    /**
     * Helper to create sample website
     */
    private fun createWebsite(url: String, title: String): Website {
        return Website(
            url = url,
            title = title,
            createdAt = System.currentTimeMillis(),
            visitCount = 1,
            isBookmarked = false
        )
    }

    // ========== Initial State Tests ==========

    @Test
    fun `uiState starts with Loading and automatically loads tabs`() = runTest {
        // Given: TabsManager with empty tabs
        coEvery { tabsManager.getActiveTabs() } returns emptyList()

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Should start with Loading
        viewModel.uiState.test {
            val loading = awaitItem()
            assertThat(loading).isInstanceOf(ModernTabsViewModel.TabsUiState.Loading::class.java)

            // Then transition to Success with empty list
            advanceUntilIdle()
            val success = awaitItem() as ModernTabsViewModel.TabsUiState.Success
            assertThat(success.tabs).isEmpty()
            assertThat(success.showCloseAllDialog).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tabs StateFlow starts empty`() = runTest {
        // Given: Setup
        coEvery { tabsManager.getActiveTabs() } returns emptyList()

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: tabs should be empty
        assertThat(viewModel.tabs.value).isEmpty()
    }

    // ========== Load Tabs Tests ==========

    @Test
    fun `loadTabs emits Success with tabs when successful`() = runTest {
        // Given: TabsManager with sample tabs
        val sampleTabs = createSampleTabs()
        coEvery { tabsManager.getActiveTabs() } returns sampleTabs

        // Mock website repository for each tab
        sampleTabs.forEach { tab ->
            val website = createWebsite(tab.url, tab.title)
            every { websiteRepository.getWebsite(tab.url) } returns flowOf(website)
        }

        // When: ViewModel is created (loadTabs called in init)
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Should emit Success with enriched tabs
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ModernTabsViewModel.TabsUiState.Success::class.java)
        val successState = state as ModernTabsViewModel.TabsUiState.Success
        assertThat(successState.tabs).hasSize(3)
        assertThat(successState.tabs[0].url).isEqualTo("https://example.com")
    }

    @Test
    fun `loadTabs handles TabsManager errors`() = runTest {
        // Given: TabsManager that throws error
        coEvery { tabsManager.getActiveTabs() } throws RuntimeException("Tabs load failed")

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Should emit Error state
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ModernTabsViewModel.TabsUiState.Error::class.java)
        val errorState = state as ModernTabsViewModel.TabsUiState.Error
        assertThat(errorState.message).contains("Tabs load failed")
    }

    @Test
    fun `loadTabs handles website enrichment errors gracefully`() = runTest {
        // Given: Tabs that succeed but website fetch fails for one
        val sampleTabs = createSampleTabs()
        coEvery { tabsManager.getActiveTabs() } returns sampleTabs

        // First tab succeeds, second fails, third succeeds
        every { websiteRepository.getWebsite("https://example.com") } returns
            flowOf(createWebsite("https://example.com", "Example"))
        every { websiteRepository.getWebsite("https://test.com") } throws
            RuntimeException("Website fetch failed")
        every { websiteRepository.getWebsite("https://demo.com") } returns
            flowOf(createWebsite("https://demo.com", "Demo"))

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Should still succeed with tabs (even if enrichment failed)
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ModernTabsViewModel.TabsUiState.Success::class.java)
        val successState = state as ModernTabsViewModel.TabsUiState.Success
        assertThat(successState.tabs).hasSize(3) // All tabs present despite error
    }

    @Test
    fun `loadTabs transitions from Loading to Success`() = runTest {
        // Given: TabsManager with tabs
        val sampleTabs = createSampleTabs()
        coEvery { tabsManager.getActiveTabs() } returns sampleTabs
        sampleTabs.forEach { tab ->
            every { websiteRepository.getWebsite(tab.url) } returns
                flowOf(createWebsite(tab.url, tab.title))
        }

        // When: Creating ViewModel and observing state transitions
        viewModel = createViewModel()

        viewModel.uiState.test {
            // Should start with Loading
            val loading = awaitItem()
            assertThat(loading).isInstanceOf(ModernTabsViewModel.TabsUiState.Loading::class.java)

            // Then transition to Success
            advanceUntilIdle()
            val success = awaitItem() as ModernTabsViewModel.TabsUiState.Success
            assertThat(success.tabs).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `manual loadTabs call reloads tabs`() = runTest {
        // Given: ViewModel with initial empty tabs
        coEvery { tabsManager.getActiveTabs() } returnsMany listOf(emptyList(), createSampleTabs())
        every { websiteRepository.getWebsite(any()) } returns
            flowOf(createWebsite("https://example.com", "Example"))

        viewModel = createViewModel()
        advanceUntilIdle()

        // Initial state should be empty
        var state = viewModel.uiState.value as ModernTabsViewModel.TabsUiState.Success
        assertThat(state.tabs).isEmpty()

        // When: Manually calling loadTabs
        viewModel.loadTabs()
        advanceUntilIdle()

        // Then: Should reload with new tabs
        state = viewModel.uiState.value as ModernTabsViewModel.TabsUiState.Success
        assertThat(state.tabs).hasSize(3)
    }

    // ========== Dialog Management Tests ==========

    @Test
    fun `showCloseAllDialog sets dialog flag when in Success state`() = runTest {
        // Given: ViewModel in Success state
        val sampleTabs = createSampleTabs()
        coEvery { tabsManager.getActiveTabs() } returns sampleTabs
        sampleTabs.forEach { tab ->
            every { websiteRepository.getWebsite(tab.url) } returns
                flowOf(createWebsite(tab.url, tab.title))
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        var state = viewModel.uiState.value as ModernTabsViewModel.TabsUiState.Success
        assertThat(state.showCloseAllDialog).isFalse()

        // When: Showing close all dialog
        viewModel.showCloseAllDialog()

        // Then: Dialog flag should be true
        state = viewModel.uiState.value as ModernTabsViewModel.TabsUiState.Success
        assertThat(state.showCloseAllDialog).isTrue()
    }

    @Test
    fun `hideCloseAllDialog clears dialog flag when in Success state`() = runTest {
        // Given: ViewModel with dialog shown
        val sampleTabs = createSampleTabs()
        coEvery { tabsManager.getActiveTabs() } returns sampleTabs
        sampleTabs.forEach { tab ->
            every { websiteRepository.getWebsite(tab.url) } returns
                flowOf(createWebsite(tab.url, tab.title))
        }

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showCloseAllDialog()

        var state = viewModel.uiState.value as ModernTabsViewModel.TabsUiState.Success
        assertThat(state.showCloseAllDialog).isTrue()

        // When: Hiding close all dialog
        viewModel.hideCloseAllDialog()

        // Then: Dialog flag should be false
        state = viewModel.uiState.value as ModernTabsViewModel.TabsUiState.Success
        assertThat(state.showCloseAllDialog).isFalse()
    }

    @Test
    fun `showCloseAllDialog does nothing when in Loading state`() = runTest {
        // Given: ViewModel that stays in Loading (slow tabs load)
        coEvery { tabsManager.getActiveTabs() } coAnswers { kotlinx.coroutines.delay(Long.MAX_VALUE); emptyList() } // Never completes

        viewModel = createViewModel()

        val stateBefore = viewModel.uiState.value
        assertThat(stateBefore).isInstanceOf(ModernTabsViewModel.TabsUiState.Loading::class.java)

        // When: Trying to show dialog while Loading
        viewModel.showCloseAllDialog()

        // Then: State should remain Loading (no crash)
        val stateAfter = viewModel.uiState.value
        assertThat(stateAfter).isInstanceOf(ModernTabsViewModel.TabsUiState.Loading::class.java)
    }

    @Test
    fun `hideCloseAllDialog does nothing when in Error state`() = runTest {
        // Given: ViewModel in Error state
        coEvery { tabsManager.getActiveTabs() } throws RuntimeException("Error")

        viewModel = createViewModel()
        advanceUntilIdle()

        val stateBefore = viewModel.uiState.value
        assertThat(stateBefore).isInstanceOf(ModernTabsViewModel.TabsUiState.Error::class.java)

        // When: Trying to hide dialog while in Error
        viewModel.hideCloseAllDialog()

        // Then: State should remain Error (no crash)
        val stateAfter = viewModel.uiState.value
        assertThat(stateAfter).isInstanceOf(ModernTabsViewModel.TabsUiState.Error::class.java)
    }

    // ========== Close All Tabs Tests ==========

    @Test
    fun `closeAllTabs closes tabs and reloads`() = runTest {
        // Given: ViewModel with tabs
        val sampleTabs = createSampleTabs()
        coEvery { tabsManager.getActiveTabs() } returnsMany listOf(sampleTabs, emptyList()) // After closing
        coEvery { tabsManager.closeAllTabs() } returns sampleTabs
        sampleTabs.forEach { tab ->
            every { websiteRepository.getWebsite(tab.url) } returns
                flowOf(createWebsite(tab.url, tab.title))
        }

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showCloseAllDialog()

        // When: Closing all tabs
        viewModel.closeAllTabs()
        advanceUntilIdle()

        // Then: TabsManager should be called
        coVerify { tabsManager.closeAllTabs() }

        // And dialog should be hidden
        val state = viewModel.uiState.value as ModernTabsViewModel.TabsUiState.Success
        assertThat(state.showCloseAllDialog).isFalse()

        // And tabs should be reloaded (now empty)
        assertThat(state.tabs).isEmpty()
    }

    @Test
    fun `closeAllTabs handles errors gracefully`() = runTest {
        // Given: ViewModel with tabs
        val sampleTabs = createSampleTabs()
        coEvery { tabsManager.getActiveTabs() } returns sampleTabs
        coEvery { tabsManager.closeAllTabs() } throws RuntimeException("Close failed")
        sampleTabs.forEach { tab ->
            every { websiteRepository.getWebsite(tab.url) } returns
                flowOf(createWebsite(tab.url, tab.title))
        }

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showCloseAllDialog()

        // When: Closing all tabs (with error)
        viewModel.closeAllTabs()
        advanceUntilIdle()

        // Then: Should handle error gracefully and hide dialog
        val state = viewModel.uiState.value as ModernTabsViewModel.TabsUiState.Success
        assertThat(state.showCloseAllDialog).isFalse()
    }

    // ========== Refresh Tests ==========

    @Test
    fun `refresh calls loadTabs`() = runTest {
        // Given: ViewModel with tabs
        coEvery { tabsManager.getActiveTabs() } returnsMany listOf(emptyList(), createSampleTabs())
        every { websiteRepository.getWebsite(any()) } returns
            flowOf(createWebsite("https://example.com", "Example"))

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Calling refresh
        viewModel.refresh()
        advanceUntilIdle()

        // Then: Should reload tabs (getActiveTabs called twice)
        coVerify(exactly = 2) { tabsManager.getActiveTabs() }
    }

    // ========== Tab Count Tests ==========

    @Test
    fun `getTabCount returns correct count in Success state`() = runTest {
        // Given: ViewModel with 3 tabs
        val sampleTabs = createSampleTabs()
        coEvery { tabsManager.getActiveTabs() } returns sampleTabs
        sampleTabs.forEach { tab ->
            every { websiteRepository.getWebsite(tab.url) } returns
                flowOf(createWebsite(tab.url, tab.title))
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Getting tab count
        val count = viewModel.getTabCount()

        // Then: Should return 3
        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `getTabCount returns 0 in Loading state`() = runTest {
        // Given: ViewModel in Loading state
        coEvery { tabsManager.getActiveTabs() } coAnswers { kotlinx.coroutines.delay(Long.MAX_VALUE); emptyList() }

        viewModel = createViewModel()

        // When: Getting tab count while Loading
        val count = viewModel.getTabCount()

        // Then: Should return 0
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `getTabCount returns 0 in Error state`() = runTest {
        // Given: ViewModel in Error state
        coEvery { tabsManager.getActiveTabs() } throws RuntimeException("Error")

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Getting tab count while in Error
        val count = viewModel.getTabCount()

        // Then: Should return 0
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `getTabCount returns 0 when no tabs`() = runTest {
        // Given: ViewModel with empty tabs
        coEvery { tabsManager.getActiveTabs() } returns emptyList()

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Getting tab count
        val count = viewModel.getTabCount()

        // Then: Should return 0
        assertThat(count).isEqualTo(0)
    }

    // ========== State Flow Tests ==========

    @Test
    fun `uiState emits all state transitions`() = runTest {
        // Given: ViewModel setup
        val sampleTabs = createSampleTabs()
        coEvery { tabsManager.getActiveTabs() } returns sampleTabs
        sampleTabs.forEach { tab ->
            every { websiteRepository.getWebsite(tab.url) } returns
                flowOf(createWebsite(tab.url, tab.title))
        }

        // When: Creating ViewModel and observing all state changes
        viewModel = createViewModel()

        viewModel.uiState.test {
            // Loading
            assertThat(awaitItem()).isInstanceOf(ModernTabsViewModel.TabsUiState.Loading::class.java)

            // Success
            advanceUntilIdle()
            val success = awaitItem() as ModernTabsViewModel.TabsUiState.Success
            assertThat(success.tabs).hasSize(3)
            assertThat(success.showCloseAllDialog).isFalse()

            // Show dialog
            viewModel.showCloseAllDialog()
            val withDialog = awaitItem() as ModernTabsViewModel.TabsUiState.Success
            assertThat(withDialog.showCloseAllDialog).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
