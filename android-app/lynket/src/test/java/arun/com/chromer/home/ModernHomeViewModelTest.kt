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

package arun.com.chromer.home

import android.app.Application
import app.cash.turbine.test
import arun.com.chromer.data.history.ModernHistoryRepository
import arun.com.chromer.data.preferences.UserPreferences
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Phase 5: Unit tests for ModernHomeViewModel
 *
 * Tests the modern ViewModel's StateFlow behavior, state transitions,
 * and action methods using:
 * - MockK for mocking dependencies
 * - Turbine for testing Flow emissions
 * - Truth for assertions
 * - Coroutines Test for testing suspend functions
 *
 * Best practices:
 * - Use MainDispatcherRule to handle coroutine dispatchers
 * - Test initial states (Loading)
 * - Test success states with mocked data
 * - Test error states with exceptions
 * - Test action methods
 * - Verify repository calls
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModernHomeViewModelTest {

    /**
     * Rule to set the main dispatcher to a test dispatcher.
     * This ensures all coroutines run on the test dispatcher.
     */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mocked dependencies
    private lateinit var application: Application
    private lateinit var historyRepository: ModernHistoryRepository
    private lateinit var preferencesRepository: UserPreferencesRepository

    // System under test
    private lateinit var viewModel: ModernHomeViewModel

    @Before
    fun setup() {
        // Create mocks
        application = mockk(relaxed = true)
        historyRepository = mockk()
        preferencesRepository = mockk()

        // Mock application.appName() extension function
        every { application.packageManager } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    /**
     * Helper to create ViewModel with mocked dependencies
     */
    private fun createViewModel(): ModernHomeViewModel {
        return ModernHomeViewModel(
            application = application,
            historyRepository = historyRepository,
            preferencesRepository = preferencesRepository
        )
    }

    /**
     * Helper to create sample websites
     */
    private fun createSampleWebsites(): List<Website> {
        return listOf(
            Website(
                url = "https://example.com",
                title = "Example",
                createdAt = System.currentTimeMillis(),
                visitCount = 1,
                isBookmarked = false
            ),
            Website(
                url = "https://test.com",
                title = "Test",
                createdAt = System.currentTimeMillis(),
                visitCount = 2,
                isBookmarked = true
            )
        )
    }

    /**
     * Helper to create default user preferences
     */
    private fun createDefaultPreferences(): UserPreferences {
        return UserPreferences(
            preferredCustomTabPackage = "com.android.chrome",
            useWebView = false,
            incognitoMode = false
        )
    }

    // ========== Initial State Tests ==========

    @Test
    fun `uiState starts with Loading state`() = runTest {
        // Given: Repositories that emit data after some time
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Initial state should be Loading
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState).isInstanceOf(ModernHomeViewModel.HomeUiState.Loading::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recentWebsites starts with empty list`() = runTest {
        // Given: Repository that emits data
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Initial value should be empty list
        viewModel.recentWebsites.test {
            val initialValue = awaitItem()
            assertThat(initialValue).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Success State Tests ==========

    @Test
    fun `uiState emits Success when data loads successfully`() = runTest {
        // Given: Repositories with sample data
        val sampleWebsites = createSampleWebsites()
        coEvery { historyRepository.getRecents() } returns flowOf(sampleWebsites)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Should emit Loading followed by Success
        viewModel.uiState.test {
            // Skip Loading state
            assertThat(awaitItem()).isInstanceOf(ModernHomeViewModel.HomeUiState.Loading::class.java)

            // Verify Success state
            val successState = awaitItem() as ModernHomeViewModel.HomeUiState.Success
            assertThat(successState.recentWebsites).hasSize(2)
            assertThat(successState.recentWebsites[0].url).isEqualTo("https://example.com")
            assertThat(successState.recentWebsites[1].url).isEqualTo("https://test.com")
            assertThat(successState.providerInfo).isNotNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recentWebsites emits list when data loads successfully`() = runTest {
        // Given: Repository with sample data
        val sampleWebsites = createSampleWebsites()
        coEvery { historyRepository.getRecents() } returns flowOf(sampleWebsites)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Should emit empty list followed by sample websites
        viewModel.recentWebsites.test {
            // Skip initial empty list
            assertThat(awaitItem()).isEmpty()

            // Verify websites list
            val websites = awaitItem()
            assertThat(websites).hasSize(2)
            assertThat(websites[0].title).isEqualTo("Example")
            assertThat(websites[1].title).isEqualTo("Test")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `providerInfo emits correct info for Chrome provider`() = runTest {
        // Given: Preferences with Chrome as provider
        val prefs = createDefaultPreferences().copy(preferredCustomTabPackage = "com.android.chrome")
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(prefs)

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Provider info should reflect Chrome
        viewModel.providerInfo.test {
            skipItems(1) // Skip initial value

            val providerInfo = awaitItem()
            assertThat(providerInfo.allowChange).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `providerInfo shows WebView when incognito mode is enabled`() = runTest {
        // Given: Preferences with incognito mode enabled
        val prefs = createDefaultPreferences().copy(
            preferredCustomTabPackage = "com.android.chrome",
            incognitoMode = true
        )
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(prefs)

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Provider info should show WebView and disallow change
        viewModel.providerInfo.test {
            skipItems(1) // Skip initial value

            val providerInfo = awaitItem()
            assertThat(providerInfo.allowChange).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Error State Tests ==========

    @Test
    fun `uiState emits Error when repository throws exception`() = runTest {
        // Given: Repository that throws an exception
        val exception = RuntimeException("Database error")
        coEvery { historyRepository.getRecents() } throws exception
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Should emit Loading followed by Error
        viewModel.uiState.test {
            // Skip Loading state
            assertThat(awaitItem()).isInstanceOf(ModernHomeViewModel.HomeUiState.Loading::class.java)

            // Verify Error state
            val errorState = awaitItem() as ModernHomeViewModel.HomeUiState.Error
            assertThat(errorState.message).contains("Database error")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recentWebsites emits empty list when repository throws exception`() = runTest {
        // Given: Repository that throws an exception
        coEvery { historyRepository.getRecents() } throws RuntimeException("Database error")
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Should emit and stay at empty list (catch operator handles error)
        viewModel.recentWebsites.test {
            val websites = awaitItem()
            assertThat(websites).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Action Method Tests ==========

    @Test
    fun `deleteWebsite calls repository delete`() = runTest {
        // Given: Successful setup
        val website = createSampleWebsites()[0]
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())
        coEvery { historyRepository.delete(any()) } returns true

        viewModel = createViewModel()

        // When: Delete website is called
        viewModel.deleteWebsite(website)
        advanceUntilIdle() // Wait for coroutine to complete

        // Then: Repository delete should be called with the website
        coVerify { historyRepository.delete(website) }
    }

    @Test
    fun `clearAllHistory calls repository deleteAll`() = runTest {
        // Given: Successful setup
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())
        coEvery { historyRepository.deleteAll() } returns 10

        viewModel = createViewModel()

        // When: Clear all history is called
        viewModel.clearAllHistory()
        advanceUntilIdle()

        // Then: Repository deleteAll should be called
        coVerify { historyRepository.deleteAll() }
    }

    @Test
    fun `toggleBookmark calls repository toggleBookmark`() = runTest {
        // Given: Successful setup
        val website = createSampleWebsites()[0]
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())
        coEvery { historyRepository.toggleBookmark(any()) } returns true

        viewModel = createViewModel()

        // When: Toggle bookmark is called
        viewModel.toggleBookmark(website)
        advanceUntilIdle()

        // Then: Repository toggleBookmark should be called with the URL
        coVerify { historyRepository.toggleBookmark(website.url) }
    }

    @Test
    fun `setCustomTabProvider updates preferences`() = runTest {
        // Given: Successful setup
        val packageName = "com.brave.browser"
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())
        coEvery { preferencesRepository.setCustomTabPackage(any()) } just Runs

        viewModel = createViewModel()

        // When: Set custom tab provider is called
        viewModel.setCustomTabProvider(packageName)
        advanceUntilIdle()

        // Then: Preferences repository should be updated
        coVerify { preferencesRepository.setCustomTabPackage(packageName) }
    }

    @Test
    fun `refreshRecents calls repository getCount`() = runTest {
        // Given: Successful setup
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())
        coEvery { historyRepository.getCount() } returns 5

        viewModel = createViewModel()

        // When: Refresh recents is called
        viewModel.refreshRecents()
        advanceUntilIdle()

        // Then: Repository getCount should be called
        coVerify { historyRepository.getCount() }
    }

    // ========== State Transition Tests ==========

    @Test
    fun `uiState updates when repository emits new data`() = runTest {
        // Given: Repository with initial empty list
        val emptyList = emptyList<Website>()
        val updatedList = createSampleWebsites()
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList, updatedList)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Should emit Loading, Success(empty), Success(with data)
        viewModel.uiState.test {
            // Loading
            assertThat(awaitItem()).isInstanceOf(ModernHomeViewModel.HomeUiState.Loading::class.java)

            // Success with empty list
            val emptyState = awaitItem() as ModernHomeViewModel.HomeUiState.Success
            assertThat(emptyState.recentWebsites).isEmpty()

            // Success with data
            val dataState = awaitItem() as ModernHomeViewModel.HomeUiState.Success
            assertThat(dataState.recentWebsites).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }
}

/**
 * Custom test rule to set the main dispatcher for testing.
 * This replaces Dispatchers.Main with a test dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
