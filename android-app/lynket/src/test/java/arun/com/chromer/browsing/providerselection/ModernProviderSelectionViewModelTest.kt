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

package arun.com.chromer.browsing.providerselection

import app.cash.turbine.test
import arun.com.chromer.data.apps.AppRepository
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.UserPreferencesRepository.UserPreferences
import arun.com.chromer.home.MainDispatcherRule
import arun.com.chromer.util.events.EventBus
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.reactivex.Single
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 5: Unit tests for ModernProviderSelectionViewModel
 *
 * Tests the ViewModel's:
 * - Provider loading with RxJava interop
 * - UI state transitions (Loading → Success → Error)
 * - Provider selection (installed vs non-installed)
 * - WebView selection
 * - Preferences integration
 * - Refresh functionality
 * - Provider change notifications
 *
 * Uses:
 * - MockK for mocking AppRepository, UserPreferencesRepository, EventBus
 * - Turbine for testing StateFlow emissions
 * - Truth for assertions
 * - Coroutines Test for async operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModernProviderSelectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mocked dependencies
    private lateinit var appRepository: AppRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var eventBus: EventBus

    // System under test
    private lateinit var viewModel: ModernProviderSelectionViewModel

    @Before
    fun setup() {
        appRepository = mockk()
        preferencesRepository = mockk()
        eventBus = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    /**
     * Helper to create ViewModel
     */
    private fun createViewModel(): ModernProviderSelectionViewModel {
        return ModernProviderSelectionViewModel(
            appRepository = appRepository,
            preferencesRepository = preferencesRepository,
            eventBus = eventBus
        )
    }

    /**
     * Helper to create sample providers
     */
    private fun createSampleProviders(): List<Provider> {
        return listOf(
            Provider(
                packageName = "com.android.chrome",
                appName = "Chrome",
                version = "100.0",
                installed = true
            ),
            Provider(
                packageName = "com.brave.browser",
                appName = "Brave",
                version = "1.50",
                installed = true
            ),
            Provider(
                packageName = "com.opera.browser",
                appName = "Opera",
                version = "70.0",
                installed = false // Not installed
            )
        )
    }

    /**
     * Helper to create user preferences
     */
    private fun createPreferences(
        customTabPackage: String? = "com.android.chrome",
        useWebView: Boolean = false
    ): UserPreferences {
        return UserPreferences(
            customTabPackage = customTabPackage,
            useWebView = useWebView
        )
    }

    // ========== Initial State Tests ==========

    @Test
    fun `uiState starts with Loading and automatically loads providers`() = runTest {
        // Given: Repository with empty providers
        every { appRepository.allProviders() } returns Single.just(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Should start with Loading
        viewModel.uiState.test {
            val loading = awaitItem()
            assertThat(loading).isInstanceOf(ModernProviderSelectionViewModel.ProviderSelectionUiState.Loading::class.java)

            // Then transition to Success with empty list
            advanceUntilIdle()
            val success = awaitItem() as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
            assertThat(success.providers).isEmpty()
            assertThat(success.selectedPackage).isEqualTo("com.android.chrome")
            assertThat(success.usingWebView).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Load Providers Tests ==========

    @Test
    fun `loadProviders emits Success with providers when successful`() = runTest {
        // Given: Repository with sample providers
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Should emit Success with providers
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ModernProviderSelectionViewModel.ProviderSelectionUiState.Success::class.java)
        val successState = state as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
        assertThat(successState.providers).hasSize(3)
        assertThat(successState.providers[0].packageName).isEqualTo("com.android.chrome")
        assertThat(successState.providers[1].packageName).isEqualTo("com.brave.browser")
        assertThat(successState.providers[2].installed).isFalse()
    }

    @Test
    fun `loadProviders handles repository errors`() = runTest {
        // Given: Repository that throws error
        every { appRepository.allProviders() } returns Single.error(RuntimeException("Failed to load"))
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Should emit Error state
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ModernProviderSelectionViewModel.ProviderSelectionUiState.Error::class.java)
        val errorState = state as ModernProviderSelectionViewModel.ProviderSelectionUiState.Error
        assertThat(errorState.message).contains("Failed to load")
    }

    @Test
    fun `loadProviders includes current preferences`() = runTest {
        // Given: Repository with providers and preferences
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(
            createPreferences(customTabPackage = "com.brave.browser", useWebView = false)
        )

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Success state should include preferences
        val state = viewModel.uiState.value as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
        assertThat(state.selectedPackage).isEqualTo("com.brave.browser")
        assertThat(state.usingWebView).isFalse()
    }

    @Test
    fun `loadProviders shows WebView preference`() = runTest {
        // Given: Preferences with WebView enabled
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(
            createPreferences(customTabPackage = null, useWebView = true)
        )

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Success state should show WebView
        val state = viewModel.uiState.value as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
        assertThat(state.usingWebView).isTrue()
        assertThat(state.selectedPackage).isNull()
    }

    // ========== Select Provider Tests ==========

    @Test
    fun `selectProvider sets installed provider as default`() = runTest {
        // Given: ViewModel with providers
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())
        coEvery { preferencesRepository.setCustomTabPackage(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Selecting an installed provider
        val brave = sampleProviders[1] // Brave is installed
        viewModel.selectProvider(brave)
        advanceUntilIdle()

        // Then: Should set as default
        coVerify { preferencesRepository.setCustomTabPackage("com.brave.browser") }
    }

    @Test
    fun `selectProvider disables WebView when selecting provider`() = runTest {
        // Given: ViewModel with WebView enabled
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(
            createPreferences(useWebView = true)
        )
        coEvery { preferencesRepository.setCustomTabPackage(any()) } just Runs
        coEvery { preferencesRepository.setUseWebView(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Selecting a provider while WebView is enabled
        val chrome = sampleProviders[0]
        viewModel.selectProvider(chrome)
        advanceUntilIdle()

        // Then: Should disable WebView
        coVerify { preferencesRepository.setUseWebView(false) }
        coVerify { preferencesRepository.setCustomTabPackage("com.android.chrome") }
    }

    @Test
    fun `selectProvider ignores non-installed providers`() = runTest {
        // Given: ViewModel with providers
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())
        coEvery { preferencesRepository.setCustomTabPackage(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Selecting a non-installed provider
        val opera = sampleProviders[2] // Opera is not installed
        viewModel.selectProvider(opera)
        advanceUntilIdle()

        // Then: Should NOT set as default
        coVerify(exactly = 0) { preferencesRepository.setCustomTabPackage(any()) }
    }

    @Test
    fun `selectProvider reloads providers after selection`() = runTest {
        // Given: ViewModel with providers
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())
        coEvery { preferencesRepository.setCustomTabPackage(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Selecting a provider
        val brave = sampleProviders[1]
        viewModel.selectProvider(brave)
        advanceUntilIdle()

        // Then: Should reload providers (called twice: init + after select)
        coVerify(exactly = 2) { appRepository.allProviders() }
    }

    @Test
    fun `selectProvider handles errors gracefully`() = runTest {
        // Given: ViewModel with preferences that fail to save
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())
        coEvery { preferencesRepository.setCustomTabPackage(any()) } throws RuntimeException("Save failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Selecting a provider (should not crash)
        val chrome = sampleProviders[0]
        viewModel.selectProvider(chrome)
        advanceUntilIdle()

        // Then: Should handle error gracefully (no crash)
        coVerify { preferencesRepository.setCustomTabPackage("com.android.chrome") }
    }

    // ========== Select WebView Tests ==========

    @Test
    fun `selectWebView enables WebView in preferences`() = runTest {
        // Given: ViewModel with providers
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())
        coEvery { preferencesRepository.setUseWebView(any()) } just Runs
        coEvery { preferencesRepository.setCustomTabPackage(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Selecting WebView
        viewModel.selectWebView()
        advanceUntilIdle()

        // Then: Should enable WebView and clear custom tab package
        coVerify { preferencesRepository.setUseWebView(true) }
        coVerify { preferencesRepository.setCustomTabPackage("") }
    }

    @Test
    fun `selectWebView reloads providers after selection`() = runTest {
        // Given: ViewModel
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())
        coEvery { preferencesRepository.setUseWebView(any()) } just Runs
        coEvery { preferencesRepository.setCustomTabPackage(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Selecting WebView
        viewModel.selectWebView()
        advanceUntilIdle()

        // Then: Should reload providers
        coVerify(exactly = 2) { appRepository.allProviders() }
    }

    @Test
    fun `selectWebView handles errors gracefully`() = runTest {
        // Given: ViewModel with preferences that fail to save
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())
        coEvery { preferencesRepository.setUseWebView(any()) } throws RuntimeException("Save failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Selecting WebView (should not crash)
        viewModel.selectWebView()
        advanceUntilIdle()

        // Then: Should handle error gracefully
        coVerify { preferencesRepository.setUseWebView(true) }
    }

    // ========== Refresh Tests ==========

    @Test
    fun `refresh reloads providers`() = runTest {
        // Given: ViewModel
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Calling refresh
        viewModel.refresh()
        advanceUntilIdle()

        // Then: Should reload providers (called twice)
        coVerify(exactly = 2) { appRepository.allProviders() }
    }

    // ========== State Transitions Tests ==========

    @Test
    fun `uiState transitions from Loading to Success`() = runTest {
        // Given: Repository with providers
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())

        // When: Creating ViewModel and observing state
        viewModel = createViewModel()

        viewModel.uiState.test {
            // Should start with Loading
            val loading = awaitItem()
            assertThat(loading).isInstanceOf(ModernProviderSelectionViewModel.ProviderSelectionUiState.Loading::class.java)

            // Then transition to Success
            advanceUntilIdle()
            val success = awaitItem() as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
            assertThat(success.providers).hasSize(3)
            assertThat(success.selectedPackage).isNotNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState transitions from Loading to Error on failure`() = runTest {
        // Given: Repository that fails
        every { appRepository.allProviders() } returns Single.error(RuntimeException("Network error"))
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())

        // When: Creating ViewModel
        viewModel = createViewModel()

        viewModel.uiState.test {
            // Loading
            assertThat(awaitItem()).isInstanceOf(ModernProviderSelectionViewModel.ProviderSelectionUiState.Loading::class.java)

            // Error
            advanceUntilIdle()
            val error = awaitItem() as ModernProviderSelectionViewModel.ProviderSelectionUiState.Error
            assertThat(error.message).contains("Network error")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting provider updates UI state`() = runTest {
        // Given: ViewModel with providers
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders) andThen
            Single.just(sampleProviders) // Second call after selection
        every { preferencesRepository.userPreferencesFlow } returns flowOf(
            createPreferences(customTabPackage = "com.android.chrome")
        ) andThen flowOf(
            createPreferences(customTabPackage = "com.brave.browser") // After selection
        )
        coEvery { preferencesRepository.setCustomTabPackage(any()) } just Runs

        viewModel = createViewModel()

        viewModel.uiState.test {
            // Initial Loading
            skipItems(1)

            // Initial Success
            advanceUntilIdle()
            val initial = awaitItem() as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
            assertThat(initial.selectedPackage).isEqualTo("com.android.chrome")

            // Select Brave
            viewModel.selectProvider(sampleProviders[1])

            // Loading again
            assertThat(awaitItem()).isInstanceOf(ModernProviderSelectionViewModel.ProviderSelectionUiState.Loading::class.java)

            // Success with updated selection
            advanceUntilIdle()
            val updated = awaitItem() as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
            assertThat(updated.selectedPackage).isEqualTo("com.brave.browser")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Edge Cases Tests ==========

    @Test
    fun `handles empty providers list`() = runTest {
        // Given: Repository with no providers
        every { appRepository.allProviders() } returns Single.just(emptyList())
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Should handle gracefully
        val state = viewModel.uiState.value as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
        assertThat(state.providers).isEmpty()
    }

    @Test
    fun `handles null custom tab package preference`() = runTest {
        // Given: Preferences with null custom tab package
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(
            createPreferences(customTabPackage = null)
        )

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Should handle gracefully
        val state = viewModel.uiState.value as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
        assertThat(state.selectedPackage).isNull()
    }

    @Test
    fun `manual loadProviders call reloads state`() = runTest {
        // Given: ViewModel
        val sampleProviders = createSampleProviders()
        every { appRepository.allProviders() } returns Single.just(emptyList()) andThen
            Single.just(sampleProviders)
        every { preferencesRepository.userPreferencesFlow } returns flowOf(createPreferences())

        viewModel = createViewModel()
        advanceUntilIdle()

        var state = viewModel.uiState.value as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
        assertThat(state.providers).isEmpty()

        // When: Manually calling loadProviders
        viewModel.loadProviders()
        advanceUntilIdle()

        // Then: Should reload
        state = viewModel.uiState.value as ModernProviderSelectionViewModel.ProviderSelectionUiState.Success
        assertThat(state.providers).hasSize(3)
    }
}
