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

package arun.com.chromer.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import arun.com.chromer.data.database.ChromerDatabase
import arun.com.chromer.data.database.dao.WebsiteDao
import arun.com.chromer.data.preferences.UserPreferences
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 5: Integration tests for ModernHistoryRepository
 *
 * Uses Robolectric to test Repository with real Room database (in-memory).
 * Tests actual database operations, Flow emissions, and business logic.
 *
 * Best practices:
 * - Use in-memory database for fast, isolated tests
 * - Test actual database operations (not mocks)
 * - Test Flow emissions with Turbine
 * - Test CRUD operations and edge cases
 * - Clean up after each test
 *
 * Note: Robolectric is used because Room requires Android framework.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30]) // Use Android API 30 for tests
class ModernHistoryRepositoryTest {

    // Real Room database (in-memory)
    private lateinit var database: ChromerDatabase
    private lateinit var websiteDao: WebsiteDao

    // Mocked preferences
    private lateinit var preferencesRepository: UserPreferencesRepository

    // System under test
    private lateinit var repository: ModernHistoryRepository

    // Test dispatcher
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        // Create in-memory database
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ChromerDatabase::class.java
        )
            .allowMainThreadQueries() // Allow for testing
            .build()

        websiteDao = database.websiteDao()

        // Mock preferences repository (default: not incognito)
        preferencesRepository = mockk()
        every { preferencesRepository.userPreferencesFlow } returns flowOf(
            UserPreferences(
                preferredCustomTabPackage = "com.android.chrome",
                useWebView = false,
                incognitoMode = false
            )
        )

        // Create repository with real DAO
        repository = ModernHistoryRepository(
            websiteDao = websiteDao,
            preferencesRepository = preferencesRepository,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Helper to create sample websites
     */
    private fun createSampleWebsite(
        url: String = "https://example.com",
        title: String = "Example",
        visitCount: Int = 1,
        isBookmarked: Boolean = false
    ): Website {
        return Website(
            url = url,
            title = title,
            createdAt = System.currentTimeMillis(),
            visitCount = visitCount,
            isBookmarked = isBookmarked
        )
    }

    // ========== Insert/Record Tests ==========

    @Test
    fun `recordVisit inserts new website`() = runTest {
        // Given: A new website
        val website = createSampleWebsite()

        // When: Recording a visit
        val result = repository.recordVisit(website)

        // Then: Website should be inserted
        assertThat(result).isNotNull()
        assertThat(result?.url).isEqualTo(website.url)
        assertThat(result?.title).isEqualTo(website.title)
        assertThat(repository.getCount()).isEqualTo(1)
    }

    @Test
    fun `recordVisit increments visit count for existing website`() = runTest {
        // Given: An existing website
        val website = createSampleWebsite()
        repository.recordVisit(website)

        // When: Recording another visit to the same URL
        val result = repository.recordVisit(website.copy(title = "Updated Title"))

        // Then: Visit count should increment
        assertThat(result).isNotNull()
        assertThat(result?.visitCount).isEqualTo(2)
        assertThat(result?.title).isEqualTo("Updated Title") // Title should update
        assertThat(repository.getCount()).isEqualTo(1) // Should still be one entry
    }

    @Test
    fun `recordVisit does not save in incognito mode`() = runTest {
        // Given: Incognito mode is enabled
        every { preferencesRepository.userPreferencesFlow } returns flowOf(
            UserPreferences(
                preferredCustomTabPackage = "com.android.chrome",
                useWebView = false,
                incognitoMode = true
            )
        )

        // When: Recording a visit
        val website = createSampleWebsite()
        val result = repository.recordVisit(website)

        // Then: Website should not be saved
        assertThat(result).isNotNull() // Returns the website
        assertThat(repository.getCount()).isEqualTo(0) // But doesn't save it
    }

    // ========== Read Tests ==========

    @Test
    fun `getAllHistory returns all websites`() = runTest {
        // Given: Multiple websites
        repository.recordVisit(createSampleWebsite("https://site1.com", "Site 1"))
        repository.recordVisit(createSampleWebsite("https://site2.com", "Site 2"))
        repository.recordVisit(createSampleWebsite("https://site3.com", "Site 3"))

        // When: Getting all history
        val history = repository.getAllHistory().first()

        // Then: Should return all 3 websites
        assertThat(history).hasSize(3)
        assertThat(history.map { it.url }).containsExactly(
            "https://site1.com",
            "https://site2.com",
            "https://site3.com"
        )
    }

    @Test
    fun `getRecents returns most recent websites`() = runTest {
        // Given: 10 websites (more than the 8 recent limit)
        repeat(10) { index ->
            repository.recordVisit(
                createSampleWebsite(
                    url = "https://site$index.com",
                    title = "Site $index"
                )
            )
            Thread.sleep(10) // Ensure different timestamps
        }

        // When: Getting recents
        val recents = repository.getRecents().first()

        // Then: Should return only 8 most recent
        assertThat(recents).hasSize(8)
        // Most recent should be site9 (last inserted)
        assertThat(recents[0].url).isEqualTo("https://site9.com")
    }

    @Test
    fun `getByUrl returns correct website`() = runTest {
        // Given: Multiple websites
        val website = createSampleWebsite("https://target.com", "Target Site")
        repository.recordVisit(website)
        repository.recordVisit(createSampleWebsite("https://other.com", "Other Site"))

        // When: Getting by URL
        val result = repository.getByUrl("https://target.com")

        // Then: Should return the correct website
        assertThat(result).isNotNull()
        assertThat(result?.url).isEqualTo("https://target.com")
        assertThat(result?.title).isEqualTo("Target Site")
    }

    @Test
    fun `getByUrl returns null for non-existent URL`() = runTest {
        // Given: Empty database
        // When: Getting by non-existent URL
        val result = repository.getByUrl("https://nonexistent.com")

        // Then: Should return null
        assertThat(result).isNull()
    }

    @Test
    fun `search returns matching websites`() = runTest {
        // Given: Websites with different titles
        repository.recordVisit(createSampleWebsite("https://kotlin.org", "Kotlin Programming"))
        repository.recordVisit(createSampleWebsite("https://java.com", "Java Programming"))
        repository.recordVisit(createSampleWebsite("https://python.org", "Python"))

        // When: Searching for "kotlin"
        val results = repository.search("kotlin")

        // Then: Should return only Kotlin website
        assertThat(results).hasSize(1)
        assertThat(results[0].url).isEqualTo("https://kotlin.org")
    }

    @Test
    fun `getBookmarks returns only bookmarked websites`() = runTest {
        // Given: Mix of bookmarked and non-bookmarked websites
        repository.recordVisit(createSampleWebsite("https://site1.com", "Site 1", isBookmarked = true))
        repository.recordVisit(createSampleWebsite("https://site2.com", "Site 2", isBookmarked = false))
        repository.recordVisit(createSampleWebsite("https://site3.com", "Site 3", isBookmarked = true))

        // When: Getting bookmarks
        val bookmarks = repository.getBookmarks().first()

        // Then: Should return only bookmarked sites
        assertThat(bookmarks).hasSize(2)
        assertThat(bookmarks.map { it.url }).containsExactly(
            "https://site1.com",
            "https://site3.com"
        )
    }

    // ========== Update Tests ==========

    @Test
    fun `update modifies existing website`() = runTest {
        // Given: An existing website
        val original = createSampleWebsite("https://example.com", "Original Title")
        repository.recordVisit(original)

        // When: Updating the website
        val updated = original.copy(title = "Updated Title", visitCount = 5)
        repository.update(updated)

        // Then: Changes should persist
        val result = repository.getByUrl("https://example.com")
        assertThat(result?.title).isEqualTo("Updated Title")
        assertThat(result?.visitCount).isEqualTo(5)
    }

    // ========== Delete Tests ==========

    @Test
    fun `delete removes website from database`() = runTest {
        // Given: An existing website
        val website = createSampleWebsite()
        repository.recordVisit(website)
        assertThat(repository.getCount()).isEqualTo(1)

        // When: Deleting the website
        val success = repository.delete(website)

        // Then: Website should be removed
        assertThat(success).isTrue()
        assertThat(repository.getCount()).isEqualTo(0)
        assertThat(repository.getByUrl(website.url)).isNull()
    }

    @Test
    fun `deleteAll removes all websites`() = runTest {
        // Given: Multiple websites
        repository.recordVisit(createSampleWebsite("https://site1.com", "Site 1"))
        repository.recordVisit(createSampleWebsite("https://site2.com", "Site 2"))
        repository.recordVisit(createSampleWebsite("https://site3.com", "Site 3"))
        assertThat(repository.getCount()).isEqualTo(3)

        // When: Deleting all
        val deletedCount = repository.deleteAll()

        // Then: All websites should be removed
        assertThat(deletedCount).isEqualTo(3)
        assertThat(repository.getCount()).isEqualTo(0)
    }

    @Test
    fun `deleteOlderThan removes old websites`() = runTest {
        // Given: Websites with different ages
        val now = System.currentTimeMillis()
        val oldWebsite = createSampleWebsite("https://old.com", "Old Site")
            .copy(createdAt = now - (10 * 24 * 60 * 60 * 1000L)) // 10 days ago
        val recentWebsite = createSampleWebsite("https://recent.com", "Recent Site")
            .copy(createdAt = now - (2 * 24 * 60 * 60 * 1000L)) // 2 days ago

        repository.recordVisit(oldWebsite)
        repository.recordVisit(recentWebsite)

        // When: Deleting websites older than 5 days
        val deletedCount = repository.deleteOlderThan(days = 5)

        // Then: Only old website should be deleted
        assertThat(deletedCount).isEqualTo(1)
        assertThat(repository.getCount()).isEqualTo(1)
        assertThat(repository.exists("https://recent.com")).isTrue()
        assertThat(repository.exists("https://old.com")).isFalse()
    }

    // ========== Bookmark Tests ==========

    @Test
    fun `toggleBookmark changes bookmark status`() = runTest {
        // Given: A website that's not bookmarked
        val website = createSampleWebsite(isBookmarked = false)
        repository.recordVisit(website)

        // When: Toggling bookmark
        repository.toggleBookmark(website.url)

        // Then: Should be bookmarked
        val result = repository.getByUrl(website.url)
        assertThat(result?.isBookmarked).isTrue()

        // When: Toggling again
        repository.toggleBookmark(website.url)

        // Then: Should not be bookmarked
        val result2 = repository.getByUrl(website.url)
        assertThat(result2?.isBookmarked).isFalse()
    }

    @Test
    fun `setBookmarked sets bookmark status`() = runTest {
        // Given: A website
        val website = createSampleWebsite(isBookmarked = false)
        repository.recordVisit(website)

        // When: Setting bookmark to true
        repository.setBookmarked(website.url, true)

        // Then: Should be bookmarked
        val result = repository.getByUrl(website.url)
        assertThat(result?.isBookmarked).isTrue()

        // When: Setting bookmark to false
        repository.setBookmarked(website.url, false)

        // Then: Should not be bookmarked
        val result2 = repository.getByUrl(website.url)
        assertThat(result2?.isBookmarked).isFalse()
    }

    // ========== Count Tests ==========

    @Test
    fun `getCount returns correct count`() = runTest {
        // Given: Multiple websites
        assertThat(repository.getCount()).isEqualTo(0)

        repository.recordVisit(createSampleWebsite("https://site1.com", "Site 1"))
        assertThat(repository.getCount()).isEqualTo(1)

        repository.recordVisit(createSampleWebsite("https://site2.com", "Site 2"))
        assertThat(repository.getCount()).isEqualTo(2)

        repository.recordVisit(createSampleWebsite("https://site3.com", "Site 3"))
        assertThat(repository.getCount()).isEqualTo(3)
    }

    @Test
    fun `getBookmarkCount returns correct count`() = runTest {
        // Given: Mix of bookmarked and non-bookmarked
        repository.recordVisit(createSampleWebsite("https://site1.com", "Site 1", isBookmarked = true))
        repository.recordVisit(createSampleWebsite("https://site2.com", "Site 2", isBookmarked = false))
        repository.recordVisit(createSampleWebsite("https://site3.com", "Site 3", isBookmarked = true))

        // When: Getting bookmark count
        val count = repository.getBookmarkCount()

        // Then: Should return 2
        assertThat(count).isEqualTo(2)
    }

    // ========== Exists Tests ==========

    @Test
    fun `exists returns true for existing website`() = runTest {
        // Given: An existing website
        val website = createSampleWebsite()
        repository.recordVisit(website)

        // When: Checking existence
        val exists = repository.exists(website.url)

        // Then: Should return true
        assertThat(exists).isTrue()
    }

    @Test
    fun `exists returns false for non-existent website`() = runTest {
        // Given: Empty database
        // When: Checking non-existent URL
        val exists = repository.exists("https://nonexistent.com")

        // Then: Should return false
        assertThat(exists).isFalse()
    }

    // ========== Flow Tests ==========

    @Test
    fun `getAllHistory Flow emits updates when data changes`() = runTest {
        // When: Observing all history
        repository.getAllHistory().test {
            // Initially empty
            assertThat(awaitItem()).isEmpty()

            // Add a website
            repository.recordVisit(createSampleWebsite("https://site1.com", "Site 1"))
            val update1 = awaitItem()
            assertThat(update1).hasSize(1)

            // Add another website
            repository.recordVisit(createSampleWebsite("https://site2.com", "Site 2"))
            val update2 = awaitItem()
            assertThat(update2).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRecents Flow emits updates when data changes`() = runTest {
        // When: Observing recents
        repository.getRecents().test {
            // Initially empty
            assertThat(awaitItem()).isEmpty()

            // Add a website
            repository.recordVisit(createSampleWebsite("https://site1.com", "Site 1"))
            val update = awaitItem()
            assertThat(update).hasSize(1)
            assertThat(update[0].url).isEqualTo("https://site1.com")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getByUrlFlow emits updates when website changes`() = runTest {
        // Given: A website
        val website = createSampleWebsite()
        repository.recordVisit(website)

        // When: Observing specific URL
        repository.getByUrlFlow(website.url).test {
            // Initial value
            val initial = awaitItem()
            assertThat(initial?.visitCount).isEqualTo(1)

            // Update visit count
            repository.recordVisit(website)
            val updated = awaitItem()
            assertThat(updated?.visitCount).isEqualTo(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getBookmarks Flow emits updates when bookmarks change`() = runTest {
        // Given: A website
        val website = createSampleWebsite(isBookmarked = false)
        repository.recordVisit(website)

        // When: Observing bookmarks
        repository.getBookmarks().test {
            // Initially empty (no bookmarks)
            assertThat(awaitItem()).isEmpty()

            // Toggle bookmark
            repository.toggleBookmark(website.url)
            val updated = awaitItem()
            assertThat(updated).hasSize(1)
            assertThat(updated[0].url).isEqualTo(website.url)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchFlow emits matching results`() = runTest {
        // Given: Multiple websites
        repository.recordVisit(createSampleWebsite("https://kotlin.org", "Kotlin Programming"))
        repository.recordVisit(createSampleWebsite("https://java.com", "Java Programming"))

        // When: Observing search results
        repository.searchFlow("kotlin").test {
            val results = awaitItem()
            assertThat(results).hasSize(1)
            assertThat(results[0].url).isEqualTo("https://kotlin.org")

            cancelAndIgnoreRemainingEvents()
        }
    }
}
