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

package arun.com.chromer.history

import androidx.paging.PagingData
import app.cash.turbine.test
import arun.com.chromer.data.history.ModernHistoryRepository
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 5: Unit tests for ModernHistoryViewModel
 *
 * Tests the ViewModel's:
 * - Search with debounce functionality
 * - Paging 3 integration
 * - UI state management
 * - Action methods (delete, bookmark, clear)
 * - Count tracking
 *
 * Uses:
 * - MockK for mocking dependencies
 * - Turbine for testing Flow emissions
 * - Truth for assertions
 * - Coroutines Test for testing async code with time control
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModernHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mocked dependencies
    private lateinit var historyRepository: ModernHistoryRepository
    private lateinit var preferencesRepository: UserPreferencesRepository

    // System under test
    private lateinit var viewModel: ModernHistoryViewModel

    @Before
    fun setup() {
        historyRepository = mockk()
        preferencesRepository = mockk(relaxed = true)

        // Default mock behavior
        coEvery { historyRepository.getPagedHistory() } returns flowOf(PagingData.empty())
        coEvery { historyRepository.getBookmarks() } returns flowOf(emptyList())
        coEvery { historyRepository.getCount() } returns 0
        coEvery { historyRepository.getBookmarkCount() } returns 0
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    /**
     * Helper to create ViewModel
     */
    private fun createViewModel(): ModernHistoryViewModel {
        return ModernHistoryViewModel(
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
                title = "Test Site",
                createdAt = System.currentTimeMillis(),
                visitCount = 2,
                isBookmarked = true
            )
        )
    }

    // ========== Initial State Tests ==========

    @Test
    fun `uiState starts with default values`() = runTest {
        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Initial state should have default values
        val state = viewModel.uiState.value
        assertThat(state.searchQuery).isEmpty()
        assertThat(state.isSearching).isFalse()
        assertThat(state.showClearAllDialog).isFalse()
        assertThat(state.totalCount).isEqualTo(0)
        assertThat(state.bookmarkCount).isEqualTo(0)
    }

    @Test
    fun `searchQuery starts empty`() = runTest {
        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Search query should be empty
        assertThat(viewModel.searchQuery.value).isEmpty()
    }

    @Test
    fun `bookmarks starts with empty list`() = runTest {
        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Bookmarks should be empty list
        viewModel.bookmarks.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init loads counts from repository`() = runTest {
        // Given: Repository with counts
        coEvery { historyRepository.getCount() } returns 42
        coEvery { historyRepository.getBookmarkCount() } returns 10

        // When: ViewModel is created
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Counts should be loaded
        val state = viewModel.uiState.value
        assertThat(state.totalCount).isEqualTo(42)
        assertThat(state.bookmarkCount).isEqualTo(10)
    }

    // ========== Search Tests ==========

    @Test
    fun `onSearchQueryChanged updates search query`() = runTest {
        // Given: ViewModel
        coEvery { historyRepository.searchFlow(any()) } returns flowOf(emptyList())
        viewModel = createViewModel()

        // When: Search query is changed
        viewModel.onSearchQueryChanged("kotlin")

        // Then: Search query should be updated
        assertThat(viewModel.searchQuery.value).isEqualTo("kotlin")
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("kotlin")
    }

    @Test
    fun `clearSearch resets search query`() = runTest {
        // Given: ViewModel with search query
        coEvery { historyRepository.searchFlow(any()) } returns flowOf(emptyList())
        viewModel = createViewModel()
        viewModel.onSearchQueryChanged("kotlin")
        assertThat(viewModel.searchQuery.value).isEqualTo("kotlin")

        // When: Clear search is called
        viewModel.clearSearch()

        // Then: Search query should be empty
        assertThat(viewModel.searchQuery.value).isEmpty()
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
    }

    @Test
    fun `searchResults debounces search queries`() = runTest {
        // Given: Repository with search results
        val searchResults = createSampleWebsites()
        coEvery { historyRepository.searchFlow("kotlin") } returns flowOf(searchResults)
        viewModel = createViewModel()

        // When: Observing search results
        viewModel.searchResults.test {
            // Change search query
            viewModel.onSearchQueryChanged("kotlin")

            // Should not emit immediately (debounce 300ms)
            expectNoEvents()

            // Advance time past debounce
            advanceTimeBy(350)

            // Then: Should emit search results
            val results = awaitItem()
            assertThat(results).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchResults filters blank queries`() = runTest {
        // Given: ViewModel
        viewModel = createViewModel()

        // When: Observing search results
        viewModel.searchResults.test {
            // Set blank query
            viewModel.onSearchQueryChanged("   ")

            // Advance time
            advanceTimeBy(350)

            // Then: Should not emit (blank query filtered)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchResults sets isSearching flag`() = runTest {
        // Given: Repository with delayed search
        coEvery { historyRepository.searchFlow("test") } returns flowOf(emptyList())
        viewModel = createViewModel()

        // When: Starting search
        viewModel.uiState.test {
            // Skip initial state
            skipItems(1)

            viewModel.onSearchQueryChanged("test")

            // Should update searchQuery
            val state1 = awaitItem()
            assertThat(state1.searchQuery).isEqualTo("test")

            // Advance past debounce
            advanceTimeBy(350)

            // Should set isSearching to true (briefly)
            // Then back to false after results
            val state2 = awaitItem()
            assertThat(state2.isSearching).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchResults handles errors gracefully`() = runTest {
        // Given: Repository that throws error
        coEvery { historyRepository.searchFlow(any()) } throws RuntimeException("Search failed")
        viewModel = createViewModel()

        // When: Observing search results
        viewModel.searchResults.test {
            viewModel.onSearchQueryChanged("error")
            advanceTimeBy(350)

            // Then: Should emit empty list (error caught)
            val results = awaitItem()
            assertThat(results).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Bookmarks Tests ==========

    @Test
    fun `bookmarks emits repository bookmarks`() = runTest {
        // Given: Repository with bookmarks
        val bookmarkedSites = createSampleWebsites().filter { it.isBookmarked }
        coEvery { historyRepository.getBookmarks() } returns flowOf(bookmarkedSites)

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: Bookmarks should be emitted
        viewModel.bookmarks.test {
            skipItems(1) // Skip initial empty list

            val bookmarks = awaitItem()
            assertThat(bookmarks).hasSize(1)
            assertThat(bookmarks[0].isBookmarked).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Delete Tests ==========

    @Test
    fun `deleteWebsite calls repository and refreshes counts`() = runTest {
        // Given: Repository setup
        val website = createSampleWebsites()[0]
        coEvery { historyRepository.delete(any()) } returns true
        coEvery { historyRepository.getCount() } returns 10 andThen 9
        coEvery { historyRepository.getBookmarkCount() } returns 5

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Delete website
        viewModel.deleteWebsite(website)
        advanceUntilIdle()

        // Then: Repository delete should be called
        coVerify { historyRepository.delete(website) }

        // And counts should be refreshed
        coVerify(exactly = 2) { historyRepository.getCount() } // init + after delete
        assertThat(viewModel.uiState.value.totalCount).isEqualTo(9)
    }

    @Test
    fun `deleteWebsite handles errors gracefully`() = runTest {
        // Given: Repository that throws error
        val website = createSampleWebsites()[0]
        coEvery { historyRepository.delete(any()) } throws RuntimeException("Delete failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Delete website (should not crash)
        viewModel.deleteWebsite(website)
        advanceUntilIdle()

        // Then: Should handle error gracefully (no crash)
        coVerify { historyRepository.delete(website) }
    }

    // ========== Bookmark Toggle Tests ==========

    @Test
    fun `toggleBookmark calls repository and refreshes counts`() = runTest {
        // Given: Repository setup
        val website = createSampleWebsites()[0]
        coEvery { historyRepository.toggleBookmark(any()) } just Runs
        coEvery { historyRepository.getCount() } returns 10
        coEvery { historyRepository.getBookmarkCount() } returns 5 andThen 6

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Toggle bookmark
        viewModel.toggleBookmark(website)
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { historyRepository.toggleBookmark(website.url) }

        // And counts should be refreshed
        coVerify(exactly = 2) { historyRepository.getBookmarkCount() }
        assertThat(viewModel.uiState.value.bookmarkCount).isEqualTo(6)
    }

    // ========== Clear All Tests ==========

    @Test
    fun `showClearAllDialog sets dialog flag to true`() = runTest {
        // Given: ViewModel
        viewModel = createViewModel()
        assertThat(viewModel.uiState.value.showClearAllDialog).isFalse()

        // When: Show dialog
        viewModel.showClearAllDialog()

        // Then: Flag should be true
        assertThat(viewModel.uiState.value.showClearAllDialog).isTrue()
    }

    @Test
    fun `hideClearAllDialog sets dialog flag to false`() = runTest {
        // Given: ViewModel with dialog shown
        viewModel = createViewModel()
        viewModel.showClearAllDialog()
        assertThat(viewModel.uiState.value.showClearAllDialog).isTrue()

        // When: Hide dialog
        viewModel.hideClearAllDialog()

        // Then: Flag should be false
        assertThat(viewModel.uiState.value.showClearAllDialog).isFalse()
    }

    @Test
    fun `clearAllHistory deletes all and resets counts`() = runTest {
        // Given: Repository with data
        coEvery { historyRepository.deleteAll() } returns 42
        coEvery { historyRepository.getCount() } returns 42 // Initial
        coEvery { historyRepository.getBookmarkCount() } returns 10

        viewModel = createViewModel()
        viewModel.showClearAllDialog()
        advanceUntilIdle()

        // When: Clear all history
        viewModel.clearAllHistory()
        advanceUntilIdle()

        // Then: Repository should be called
        coVerify { historyRepository.deleteAll() }

        // And state should be updated
        val state = viewModel.uiState.value
        assertThat(state.showClearAllDialog).isFalse()
        assertThat(state.totalCount).isEqualTo(0)
        assertThat(state.bookmarkCount).isEqualTo(0)
    }

    @Test
    fun `clearAllHistory handles errors gracefully`() = runTest {
        // Given: Repository that throws error
        coEvery { historyRepository.deleteAll() } throws RuntimeException("Delete failed")

        viewModel = createViewModel()
        viewModel.showClearAllDialog()

        // When: Clear all (should not crash)
        viewModel.clearAllHistory()
        advanceUntilIdle()

        // Then: Should handle error gracefully
        coVerify { historyRepository.deleteAll() }
    }

    // ========== Delete Old History Tests ==========

    @Test
    fun `deleteOldHistory calls repository with correct days`() = runTest {
        // Given: Repository setup
        coEvery { historyRepository.deleteOlderThan(any()) } returns 15
        coEvery { historyRepository.getCount() } returns 50 andThen 35
        coEvery { historyRepository.getBookmarkCount() } returns 10

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Delete old history (default 30 days)
        viewModel.deleteOldHistory()
        advanceUntilIdle()

        // Then: Repository should be called with 30 days
        coVerify { historyRepository.deleteOlderThan(30) }

        // And counts should be refreshed
        coVerify(exactly = 2) { historyRepository.getCount() }
    }

    @Test
    fun `deleteOldHistory accepts custom days parameter`() = runTest {
        // Given: Repository setup
        coEvery { historyRepository.deleteOlderThan(any()) } returns 5
        coEvery { historyRepository.getCount() } returns 50
        coEvery { historyRepository.getBookmarkCount() } returns 10

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: Delete old history with 7 days
        viewModel.deleteOldHistory(days = 7)
        advanceUntilIdle()

        // Then: Repository should be called with 7 days
        coVerify { historyRepository.deleteOlderThan(7) }
    }

    // ========== Refresh Tests ==========

    @Test
    fun `refresh reloads counts`() = runTest {
        // Given: Repository with changing counts
        coEvery { historyRepository.getCount() } returns 10 andThen 20
        coEvery { historyRepository.getBookmarkCount() } returns 5 andThen 8

        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.totalCount).isEqualTo(10)

        // When: Refresh
        viewModel.refresh()
        advanceUntilIdle()

        // Then: Counts should be reloaded
        coVerify(exactly = 2) { historyRepository.getCount() }
        assertThat(viewModel.uiState.value.totalCount).isEqualTo(20)
        assertThat(viewModel.uiState.value.bookmarkCount).isEqualTo(8)
    }

    // ========== State Flow Tests ==========

    @Test
    fun `uiState emits updates for all state changes`() = runTest {
        // Given: ViewModel
        coEvery { historyRepository.getCount() } returns 5
        coEvery { historyRepository.getBookmarkCount() } returns 2
        viewModel = createViewModel()

        // When: Observing uiState
        viewModel.uiState.test {
            // Initial state
            val initial = awaitItem()
            assertThat(initial.totalCount).isEqualTo(0)

            // Wait for counts to load
            advanceUntilIdle()
            val withCounts = awaitItem()
            assertThat(withCounts.totalCount).isEqualTo(5)
            assertThat(withCounts.bookmarkCount).isEqualTo(2)

            // Show dialog
            viewModel.showClearAllDialog()
            val withDialog = awaitItem()
            assertThat(withDialog.showClearAllDialog).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchQuery emits updates for query changes`() = runTest {
        // Given: ViewModel
        coEvery { historyRepository.searchFlow(any()) } returns flowOf(emptyList())
        viewModel = createViewModel()

        // When: Observing searchQuery
        viewModel.searchQuery.test {
            // Initial empty
            assertThat(awaitItem()).isEmpty()

            // Change query
            viewModel.onSearchQueryChanged("kotlin")
            assertThat(awaitItem()).isEqualTo("kotlin")

            // Change again
            viewModel.onSearchQueryChanged("android")
            assertThat(awaitItem()).isEqualTo("android")

            // Clear
            viewModel.clearSearch()
            assertThat(awaitItem()).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========== Edge Cases ==========

    @Test
    fun `handles multiple rapid search query changes`() = runTest {
        // Given: ViewModel
        coEvery { historyRepository.searchFlow(any()) } returns flowOf(emptyList())
        viewModel = createViewModel()

        // When: Rapidly changing search query
        viewModel.onSearchQueryChanged("a")
        viewModel.onSearchQueryChanged("an")
        viewModel.onSearchQueryChanged("and")
        viewModel.onSearchQueryChanged("andr")
        viewModel.onSearchQueryChanged("andro")
        viewModel.onSearchQueryChanged("android")

        // Advance past debounce
        advanceTimeBy(350)

        // Then: Should only search for final query (debounced)
        coVerify(exactly = 1) { historyRepository.searchFlow("android") }
    }

    @Test
    fun `pagedHistory is available immediately`() = runTest {
        // Given: Repository with paged data
        val pagingData = PagingData.from(createSampleWebsites())
        coEvery { historyRepository.getPagedHistory() } returns flowOf(pagingData)

        // When: ViewModel is created
        viewModel = createViewModel()

        // Then: pagedHistory should be available
        assertThat(viewModel.pagedHistory).isNotNull()

        // Can collect from it
        viewModel.pagedHistory.test {
            val data = awaitItem()
            assertThat(data).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
