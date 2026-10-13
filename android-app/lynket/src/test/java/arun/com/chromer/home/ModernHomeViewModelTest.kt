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
import android.content.pm.PackageManager
import android.net.Uri
import app.cash.turbine.test
import arun.com.chromer.data.history.ModernHistoryRepository
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.UserPreferencesRepository.UserPreferences
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.search.provider.SearchProviders
import arun.com.chromer.search.suggestion.SuggestionsEngine
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
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
    private lateinit var tabsManager: TabsManager
    private lateinit var networkMonitor: FakeNetworkMonitor
    private lateinit var suggestionsEngine: SuggestionsEngine
    private lateinit var searchProviders: SearchProviders

    // System under test
    private lateinit var viewModel: ModernHomeViewModel

    @Before
    fun setup() {
        // The ViewModel builds provider icon URIs; android.net.Uri has no JVM implementation outside Robolectric.
        mockkStatic(Uri::class)
        every { Uri.fromParts(any(), any(), any()) } returns mockk(relaxed = true)
        // SearchProviders builds its engine icons with Uri.parse when its class loads.
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        // Create mocks
        application = mockk(relaxed = true)
        historyRepository = mockk()
        preferencesRepository = mockk()
        tabsManager = mockk(relaxed = true)
        networkMonitor = FakeNetworkMonitor()
        suggestionsEngine = mockk(relaxed = true)
        searchProviders = mockk(relaxed = true)

        // Mock application.appName() extension function, which reads applicationContext.packageManager
        val packageManager: PackageManager = mockk(relaxed = true)
        every { packageManager.getApplicationLabel(any()) } returns "Chrome"
        every { application.applicationContext } returns application
        every { application.packageManager } returns packageManager

    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        clearAllMocks()
    }

    /**
     * Helper to create ViewModel with mocked dependencies
     */
    private fun createViewModel(): ModernHomeViewModel {
        return ModernHomeViewModel(
            application = application,
            historyRepository = historyRepository,
            preferencesRepository = preferencesRepository,
            tabsManager = tabsManager,
            networkMonitor = networkMonitor,
            suggestionsEngine = suggestionsEngine,
            searchProviders = searchProviders,
        )
    }

    /** The provider card of the first loaded home state. */
    private suspend fun ModernHomeViewModel.firstProviderInfo(): CustomTabProviderInfo =
        (uiState.first { it is ModernHomeViewModel.HomeUiState.Success } as ModernHomeViewModel.HomeUiState.Success)
            .providerInfo

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

        // Then: The recents start empty
        viewModel.uiState.test {
            assertThat(awaitItem()).isInstanceOf(ModernHomeViewModel.HomeUiState.Loading::class.java)
            assertThat((awaitItem() as ModernHomeViewModel.HomeUiState.Success).recentWebsites).isEmpty()
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
        viewModel.uiState.test {
            // Skip Loading
            awaitItem()

            // Verify websites list
            val websites = (awaitItem() as ModernHomeViewModel.HomeUiState.Success).recentWebsites
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
        assertThat(viewModel.firstProviderInfo().allowChange).isTrue()
    }

    @Test
    fun `providerInfo shows WebView when full incognito is on`() = runTest {
        val prefs = createDefaultPreferences().copy(
            preferredCustomTabPackage = "com.android.chrome",
            fullIncognitoMode = true
        )
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(prefs)

        viewModel = createViewModel()

        assertThat(viewModel.firstProviderInfo().allowChange).isFalse()
    }

    @Test
    fun `providerInfo keeps the chosen provider when only history is off`() = runTest {
        // incognito_mode is the History off switch; links still open in the Custom Tab provider.
        val prefs = createDefaultPreferences().copy(
            preferredCustomTabPackage = "com.android.chrome",
            incognitoMode = true,
            fullIncognitoMode = false
        )
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(prefs)

        viewModel = createViewModel()

        val info = viewModel.firstProviderInfo()
        assertThat(info.allowChange).isTrue()
        assertThat(info.iconUri).isNotNull()
    }

    // ========== Error State Tests ==========

    @Test
    fun `uiState emits Error when repository throws exception`() = runTest {
        // Given: Repository that throws an exception
        val exception = RuntimeException("Database error")
        // getRecents() returns a cold Flow; a repository failure surfaces when the flow is collected.
        every { historyRepository.getRecents() } returns flow { throw exception }
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
    fun `Retry reads the recents again after an error`() = runTest {
        var reads = 0
        every { historyRepository.getRecents() } answers {
            reads++
            if (reads == 1) flow { throw RuntimeException("Database error") } else flowOf(createSampleWebsites())
        }
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Loading
            assertThat(awaitItem()).isInstanceOf(ModernHomeViewModel.HomeUiState.Error::class.java)

            viewModel.refreshRecents()

            val recovered = awaitItem() as ModernHomeViewModel.HomeUiState.Success
            assertThat(recovered.recentWebsites).hasSize(2)
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
        coEvery { historyRepository.toggleBookmark(any()) } just Runs

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

    // ========== State Transition Tests ==========

    @Test
    fun `uiState updates when repository emits new data`() = runTest {
        // Given: Repository with initial empty list
        val emptyList = emptyList<Website>()
        val updatedList = createSampleWebsites()
        val recents = MutableStateFlow(emptyList)
        coEvery { historyRepository.getRecents() } returns recents
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
            recents.value = updatedList

            // Success with data
            val dataState = awaitItem() as ModernHomeViewModel.HomeUiState.Success
            assertThat(dataState.recentWebsites).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Home states: open tabs, no provider, offline ==========

    @Test
    fun `activeTabs reports the open browsing tabs`() = runTest {
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())
        val tab = TabsManager.Tab("https://example.com", arun.com.chromer.tabs.CUSTOM_TAB)
        coEvery { tabsManager.getActiveTabs() } returns listOf(tab)

        viewModel = createViewModel()

        viewModel.activeTabs.test {
            assertThat(awaitItem()).isEmpty()
            assertThat(awaitItem()).containsExactly(tab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `providerInfo marks no provider when no Custom Tabs browser is installed`() = runTest {
        mockkObject(arun.com.chromer.browsing.customtabs.CustomTabs.Companion)
        every { arun.com.chromer.browsing.customtabs.CustomTabs.getCustomTabSupportingPackages(any()) } returns emptyList()
        try {
            coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
            every { preferencesRepository.userPreferencesFlow } returns flowOf(
                UserPreferences(preferredCustomTabPackage = null, useWebView = false, incognitoMode = false)
            )

            viewModel = createViewModel()

            val info = viewModel.firstProviderInfo()
            assertThat(info.noProviderInstalled).isTrue()
            assertThat(info.allowChange).isTrue()
        } finally {
            unmockkObject(arun.com.chromer.browsing.customtabs.CustomTabs.Companion)
        }
    }

    @Test
    fun `providerInfo has a provider when Chrome is chosen`() = runTest {
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        viewModel = createViewModel()

        assertThat(viewModel.firstProviderInfo().noProviderInstalled).isFalse()
    }

    @Test
    fun `isOnline follows the network monitor`() = runTest {
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())

        viewModel = createViewModel()

        viewModel.isOnline.test {
            assertThat(awaitItem()).isTrue()
            networkMonitor.online.value = false
            assertThat(awaitItem()).isFalse()
            networkMonitor.online.value = true
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openWebsite goes through the tabs manager`() = runTest {
        coEvery { historyRepository.getRecents() } returns flowOf(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createDefaultPreferences())
        viewModel = createViewModel()
        val website = createSampleWebsites().first()

        viewModel.openWebsite(application, website)

        verify { tabsManager.openUrl(application, website) }
    }
}

/** A network monitor whose connection a test sets. */
class FakeNetworkMonitor : NetworkMonitor {
    val online = MutableStateFlow(true)
    override val isOnline = online
    override fun isOnlineNow(): Boolean = online.value
}

/**
 * Custom test rule to set the main dispatcher for testing.
 * This replaces Dispatchers.Main with a test dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
