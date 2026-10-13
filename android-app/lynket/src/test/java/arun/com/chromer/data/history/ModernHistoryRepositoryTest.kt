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
import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import app.cash.turbine.test
import arun.com.chromer.data.database.ChromerDatabase
import arun.com.chromer.data.database.HistoryDatabase
import arun.com.chromer.data.database.dao.WebsiteDao
import arun.com.chromer.data.database.entity.toEntity
import arun.com.chromer.data.database.entity.toWebsite
import arun.com.chromer.data.history.model.HistoryTable
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * The history repository of the History screen and the home, on a real in-memory Room database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ModernHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: ChromerDatabase
    private lateinit var websiteDao: WebsiteDao
    private lateinit var repository: ModernHistoryRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    // Room delivers Flow updates from its own background executor, so Turbine waits on the
    // wall clock. Turbine's 3 s default timed out once on a heavily loaded machine.
    private val ROOM_FLOW_TIMEOUT = 10.seconds

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder<ChromerDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()
        websiteDao = database.websiteDao()
        repository = ModernHistoryRepository(
            historyDatabase = HistoryDatabase(context, legacyImport = null) { database },
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun site(url: String, title: String = url, createdAt: Long = 1_000L, bookmarked: Boolean = false) =
        Website(url).apply {
            this.title = title
            this.createdAt = createdAt
            this.count = 1
            this.bookmarked = bookmarked
        }

    private suspend fun store(vararg sites: Website) = sites.forEach { websiteDao.insert(it.toEntity()) }

    @Test
    fun `getRecents returns the eight most recent websites`() = runTest {
        store(*Array(10) { site("https://site$it.com", createdAt = 1_000L + it) })

        val recents = repository.getRecents().first()

        assertThat(recents).hasSize(8)
        assertThat(recents[0].url).isEqualTo("https://site9.com")
    }

    @Test
    fun `getRecents Flow emits updates when data changes`() = runTest {
        repository.getRecents().test(timeout = ROOM_FLOW_TIMEOUT) {
            assertThat(awaitItem()).isEmpty()

            store(site("https://site1.com"))
            val update = awaitItem()
            assertThat(update.map { it.url }).containsExactly("https://site1.com")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete removes website from database`() = runTest {
        val website = site("https://example.com")
        store(website)

        assertThat(repository.delete(website)).isTrue()

        assertThat(repository.getCount()).isEqualTo(0)
        assertThat(websiteDao.getByUrl(website.url)).isNull()
    }

    @Test
    fun `deleteAll removes all websites`() = runTest {
        store(site("https://site1.com"), site("https://site2.com"), site("https://site3.com"))

        assertThat(repository.deleteAll()).isEqualTo(3)
        assertThat(repository.getCount()).isEqualTo(0)
    }

    @Test
    fun `deleteAll also deletes the copies of history kept on the device`() = runTest {
        val migrated = File(context.filesDir, "${LegacyHistoryImport.MIGRATION_FOLDER}/History-1")
            .apply { parentFile?.mkdirs(); writeText("old history") }
        val broken = File(context.filesDir, "${HistoryDatabase.BROKEN_FOLDER}/2/${ChromerDatabase.DATABASE_NAME}")
            .apply { parentFile?.mkdirs(); writeText("broken history") }
        val notImported = context.getDatabasePath(HistoryTable.TABLE_NAME)
            .apply { parentFile?.mkdirs(); writeText("old history not imported yet") }
        store(site("https://site1.com"))

        repository.deleteAll()

        assertThat(migrated.exists()).isFalse()
        assertThat(broken.exists()).isFalse()
        assertThat(notImported.exists()).isFalse()
        assertThat(File(context.filesDir, LegacyHistoryImport.MIGRATION_FOLDER).exists()).isFalse()
    }

    @Test
    fun `toggleBookmark changes bookmark status`() = runTest {
        val website = site("https://example.com")
        store(website)

        repository.toggleBookmark(website.url)
        assertThat(websiteDao.getByUrl(website.url)?.bookmarked).isTrue()

        repository.toggleBookmark(website.url)
        assertThat(websiteDao.getByUrl(website.url)?.bookmarked).isFalse()
    }

    @Test
    fun `getCount returns correct count`() = runTest {
        assertThat(repository.getCount()).isEqualTo(0)
        store(site("https://site1.com"), site("https://site2.com"))
        assertThat(repository.getCount()).isEqualTo(2)
    }

    // ========== History screen: full search and undo ==========

    @Test
    fun `searchPaged returns every match beyond five, newest first`() = runTest {
        repeat(12) { n -> store(site("https://match.example/$n", title = "Match $n", createdAt = 1_000L + n)) }
        store(site("https://other.example", title = "Other"))

        val page = websiteDao.searchPaged("match")
            .load(PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false))
            as PagingSource.LoadResult.Page

        assertThat(page.data.map { it.url }).containsExactlyElementsIn(
            (11 downTo 0).map { "https://match.example/$it" }
        ).inOrder()
        assertThat(websiteDao.search("match")).hasSize(5)
    }

    @Test
    fun `search treats percent and underscore as typed text`() = runTest {
        store(
            site("https://a.example/a_b", title = "Underscore"),
            site("https://a.example/axb", title = "Any letter"),
            site("https://a.example/sale", title = "100% off"),
            site("https://a.example/plain", title = "100 points")
        )

        assertThat(websiteDao.search("a_b").map { it.url }).containsExactly("https://a.example/a_b")
        assertThat(websiteDao.search("100%").map { it.url }).containsExactly("https://a.example/sale")
    }

    @Test
    fun `restore puts a deleted row back with its time and visit count`() = runTest {
        val site = site("https://undo.example", bookmarked = true).apply { count = 4 }
        websiteDao.insert(site.toEntity().copy(createdAt = 42_000L))
        val stored = websiteDao.getByUrl("https://undo.example")!!.toWebsite()

        assertThat(repository.delete(stored)).isTrue()
        assertThat(websiteDao.getByUrl("https://undo.example")).isNull()

        assertThat(repository.restore(stored)).isTrue()
        val back = websiteDao.getByUrl("https://undo.example")!!
        assertThat(back.createdAt).isEqualTo(42_000L)
        assertThat(back.visitCount).isEqualTo(4)
        assertThat(back.bookmarked).isTrue()
    }
}
