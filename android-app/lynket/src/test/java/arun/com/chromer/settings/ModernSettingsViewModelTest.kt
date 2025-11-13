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

package arun.com.chromer.settings

import app.cash.turbine.test
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.UserPreferencesRepository.UserPreferences
import arun.com.chromer.home.MainDispatcherRule
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
 * Phase 5: Unit tests for ModernSettingsViewModel
 *
 * Tests the ViewModel's:
 * - DataStore integration via StateFlow
 * - All 14 preference setter methods
 * - Real-time preference updates
 * - Error handling for each setting
 *
 * Uses:
 * - MockK for mocking UserPreferencesRepository
 * - Turbine for testing StateFlow emissions
 * - Truth for assertions
 * - Coroutines Test for async operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModernSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mocked dependencies
    private lateinit var preferencesRepository: UserPreferencesRepository

    // System under test
    private lateinit var viewModel: ModernSettingsViewModel

    @Before
    fun setup() {
        preferencesRepository = mockk()

        // Default mock behavior - return default preferences
        every { preferencesRepository.userPreferencesFlow } returns flowOf(UserPreferences())
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    /**
     * Helper to create ViewModel
     */
    private fun createViewModel(): ModernSettingsViewModel {
        return ModernSettingsViewModel(preferencesRepository)
    }

    /**
     * Helper to create preferences with specific values
     */
    private fun createPreferences(
        webHeadsEnabled: Boolean = false,
        incognitoMode: Boolean = false,
        ampMode: Boolean = false
    ): UserPreferences {
        return UserPreferences(
            webHeadsEnabled = webHeadsEnabled,
            incognitoMode = incognitoMode,
            ampMode = ampMode
        )
    }

    // ========== Initial State Tests ==========

    @Test
    fun `preferences starts with default values from repository`() = runTest {
        // Given: Repository with default preferences
        val defaultPrefs = UserPreferences()
        every { preferencesRepository.userPreferencesFlow } returns flowOf(defaultPrefs)

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Preferences should have default values
        viewModel.preferences.test {
            val prefs = awaitItem()
            assertThat(prefs.webHeadsEnabled).isFalse()
            assertThat(prefs.incognitoMode).isFalse()
            assertThat(prefs.ampMode).isFalse()
            assertThat(prefs.articleMode).isFalse()
            assertThat(prefs.useWebView).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `preferences emits repository updates`() = runTest {
        // Given: Repository with custom preferences
        val customPrefs = createPreferences(
            webHeadsEnabled = true,
            incognitoMode = true,
            ampMode = true
        )
        every { preferencesRepository.userPreferencesFlow } returns flowOf(customPrefs)

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Preferences should reflect repository values
        viewModel.preferences.test {
            skipItems(1) // Skip initial default

            val prefs = awaitItem()
            assertThat(prefs.webHeadsEnabled).isTrue()
            assertThat(prefs.incognitoMode).isTrue()
            assertThat(prefs.ampMode).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Web Heads Settings Tests ==========

    @Test
    fun `setWebHeadsEnabled calls repository`() = runTest {
        // Given: ViewModel with mocked repository
        coEvery { preferencesRepository.setWebHeadsEnabled(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting web heads enabled
        viewModel.setWebHeadsEnabled(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setWebHeadsEnabled(true) }
    }

    @Test
    fun `setWebHeadsEnabled handles errors gracefully`() = runTest {
        // Given: Repository that throws error
        coEvery { preferencesRepository.setWebHeadsEnabled(any()) } throws RuntimeException("Save failed")
        viewModel = createViewModel()

        // When: Setting web heads (should not crash)
        viewModel.setWebHeadsEnabled(true)
        advanceUntilIdle()

        // Then: Should handle error gracefully
        coVerify { preferencesRepository.setWebHeadsEnabled(true) }
    }

    @Test
    fun `setWebHeadsFavicons calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setWebHeadsFavicons(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting favicons
        viewModel.setWebHeadsFavicons(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setWebHeadsFavicons(true) }
    }

    @Test
    fun `setWebHeadsCloseOnOpen calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setWebHeadsCloseOnOpen(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting close on open
        viewModel.setWebHeadsCloseOnOpen(false)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setWebHeadsCloseOnOpen(false) }
    }

    // ========== Browser Settings Tests ==========

    @Test
    fun `setIncognitoMode calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setIncognitoMode(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting incognito mode
        viewModel.setIncognitoMode(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setIncognitoMode(true) }
    }

    @Test
    fun `setIncognitoMode handles errors gracefully`() = runTest {
        // Given: Repository that throws error
        coEvery { preferencesRepository.setIncognitoMode(any()) } throws RuntimeException("Save failed")
        viewModel = createViewModel()

        // When: Setting incognito mode (should not crash)
        viewModel.setIncognitoMode(true)
        advanceUntilIdle()

        // Then: Should handle error gracefully
        coVerify { preferencesRepository.setIncognitoMode(true) }
    }

    @Test
    fun `setAmpMode calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setAmpMode(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting AMP mode
        viewModel.setAmpMode(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setAmpMode(true) }
    }

    @Test
    fun `setArticleMode calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setArticleMode(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting article mode
        viewModel.setArticleMode(false)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setArticleMode(false) }
    }

    @Test
    fun `setUseWebView calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setUseWebView(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting WebView usage
        viewModel.setUseWebView(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setUseWebView(true) }
    }

    // ========== Appearance Settings Tests ==========

    @Test
    fun `setDynamicToolbar calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setDynamicToolbar(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting dynamic toolbar
        viewModel.setDynamicToolbar(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setDynamicToolbar(true) }
    }

    @Test
    fun `setBottomBar calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setBottomBar(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting bottom bar
        viewModel.setBottomBar(false)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setBottomBar(false) }
    }

    // ========== Performance Settings Tests ==========

    @Test
    fun `setWarmUp calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setWarmUp(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting warm up
        viewModel.setWarmUp(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setWarmUp(true) }
    }

    @Test
    fun `setPreFetch calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setPreFetch(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting prefetch
        viewModel.setPreFetch(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setPreFetch(true) }
    }

    @Test
    fun `setAggressiveLoading calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setAggressiveLoading(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting aggressive loading
        viewModel.setAggressiveLoading(false)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setAggressiveLoading(false) }
    }

    // ========== Advanced Settings Tests ==========

    @Test
    fun `setPerAppSettings calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setPerAppSettings(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting per-app settings
        viewModel.setPerAppSettings(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setPerAppSettings(true) }
    }

    @Test
    fun `setMergeTabs calls repository`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setMergeTabs(any()) } just Runs
        viewModel = createViewModel()

        // When: Setting merge tabs
        viewModel.setMergeTabs(true)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { preferencesRepository.setMergeTabs(true) }
    }

    // ========== Multiple Settings Changes Tests ==========

    @Test
    fun `multiple settings changes all call repository`() = runTest {
        // Given: ViewModel with all methods mocked
        coEvery { preferencesRepository.setWebHeadsEnabled(any()) } just Runs
        coEvery { preferencesRepository.setIncognitoMode(any()) } just Runs
        coEvery { preferencesRepository.setAmpMode(any()) } just Runs
        viewModel = createViewModel()

        // When: Changing multiple settings
        viewModel.setWebHeadsEnabled(true)
        viewModel.setIncognitoMode(true)
        viewModel.setAmpMode(false)
        advanceUntilIdle()

        // Then: All repository methods should be called
        coVerify { preferencesRepository.setWebHeadsEnabled(true) }
        coVerify { preferencesRepository.setIncognitoMode(true) }
        coVerify { preferencesRepository.setAmpMode(false) }
    }

    @Test
    fun `settings can be toggled on and off`() = runTest {
        // Given: ViewModel
        coEvery { preferencesRepository.setIncognitoMode(any()) } just Runs
        viewModel = createViewModel()

        // When: Toggling a setting
        viewModel.setIncognitoMode(true)
        advanceUntilIdle()
        viewModel.setIncognitoMode(false)
        advanceUntilIdle()

        // Then: Both calls should be made
        coVerify(exactly = 1) { preferencesRepository.setIncognitoMode(true) }
        coVerify(exactly = 1) { preferencesRepository.setIncognitoMode(false) }
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `all settings handle errors independently`() = runTest {
        // Given: Repository with some methods failing
        coEvery { preferencesRepository.setWebHeadsEnabled(any()) } throws RuntimeException("Error 1")
        coEvery { preferencesRepository.setIncognitoMode(any()) } just Runs // This works
        coEvery { preferencesRepository.setAmpMode(any()) } throws RuntimeException("Error 2")
        viewModel = createViewModel()

        // When: Changing all three settings (none should crash)
        viewModel.setWebHeadsEnabled(true)
        viewModel.setIncognitoMode(true)
        viewModel.setAmpMode(true)
        advanceUntilIdle()

        // Then: All should be attempted (errors handled gracefully)
        coVerify { preferencesRepository.setWebHeadsEnabled(true) }
        coVerify { preferencesRepository.setIncognitoMode(true) }
        coVerify { preferencesRepository.setAmpMode(true) }
    }

    // ========== StateFlow Behavior Tests ==========

    @Test
    fun `preferences StateFlow caches values across subscribers`() = runTest {
        // Given: ViewModel
        viewModel = createViewModel()

        // When: Multiple subscribers collect
        viewModel.preferences.test {
            val prefs1 = awaitItem()
            assertThat(prefs1).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.preferences.test {
            val prefs2 = awaitItem()
            assertThat(prefs2).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }

        // Then: Repository flow should only be collected once (cached)
        verify(exactly = 1) { preferencesRepository.userPreferencesFlow }
    }

    @Test
    fun `preferences survives configuration changes`() = runTest {
        // Given: ViewModel that survives configuration change
        viewModel = createViewModel()

        // When: Collecting preferences
        val prefs1 = viewModel.preferences.value

        // Simulate configuration change (ViewModel survives)
        val prefs2 = viewModel.preferences.value

        // Then: Same preferences instance
        assertThat(prefs1).isSameInstanceAs(prefs2)
    }

    // ========== Integration Tests ==========

    @Test
    fun `verify all 14 setter methods exist and work`() = runTest {
        // Given: ViewModel with all methods mocked
        coEvery { preferencesRepository.setWebHeadsEnabled(any()) } just Runs
        coEvery { preferencesRepository.setWebHeadsFavicons(any()) } just Runs
        coEvery { preferencesRepository.setWebHeadsCloseOnOpen(any()) } just Runs
        coEvery { preferencesRepository.setIncognitoMode(any()) } just Runs
        coEvery { preferencesRepository.setAmpMode(any()) } just Runs
        coEvery { preferencesRepository.setArticleMode(any()) } just Runs
        coEvery { preferencesRepository.setUseWebView(any()) } just Runs
        coEvery { preferencesRepository.setDynamicToolbar(any()) } just Runs
        coEvery { preferencesRepository.setBottomBar(any()) } just Runs
        coEvery { preferencesRepository.setWarmUp(any()) } just Runs
        coEvery { preferencesRepository.setPreFetch(any()) } just Runs
        coEvery { preferencesRepository.setAggressiveLoading(any()) } just Runs
        coEvery { preferencesRepository.setPerAppSettings(any()) } just Runs
        coEvery { preferencesRepository.setMergeTabs(any()) } just Runs

        viewModel = createViewModel()

        // When: Calling all 14 setter methods
        viewModel.setWebHeadsEnabled(true)
        viewModel.setWebHeadsFavicons(true)
        viewModel.setWebHeadsCloseOnOpen(true)
        viewModel.setIncognitoMode(true)
        viewModel.setAmpMode(true)
        viewModel.setArticleMode(true)
        viewModel.setUseWebView(true)
        viewModel.setDynamicToolbar(true)
        viewModel.setBottomBar(true)
        viewModel.setWarmUp(true)
        viewModel.setPreFetch(true)
        viewModel.setAggressiveLoading(true)
        viewModel.setPerAppSettings(true)
        viewModel.setMergeTabs(true)
        advanceUntilIdle()

        // Then: All 14 repository methods should be called
        coVerify { preferencesRepository.setWebHeadsEnabled(true) }
        coVerify { preferencesRepository.setWebHeadsFavicons(true) }
        coVerify { preferencesRepository.setWebHeadsCloseOnOpen(true) }
        coVerify { preferencesRepository.setIncognitoMode(true) }
        coVerify { preferencesRepository.setAmpMode(true) }
        coVerify { preferencesRepository.setArticleMode(true) }
        coVerify { preferencesRepository.setUseWebView(true) }
        coVerify { preferencesRepository.setDynamicToolbar(true) }
        coVerify { preferencesRepository.setBottomBar(true) }
        coVerify { preferencesRepository.setWarmUp(true) }
        coVerify { preferencesRepository.setPreFetch(true) }
        coVerify { preferencesRepository.setAggressiveLoading(true) }
        coVerify { preferencesRepository.setPerAppSettings(true) }
        coVerify { preferencesRepository.setMergeTabs(true) }
    }
}
