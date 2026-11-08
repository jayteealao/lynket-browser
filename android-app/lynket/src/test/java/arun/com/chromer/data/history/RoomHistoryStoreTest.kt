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

package arun.com.chromer.data.history

import android.content.Context
import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import arun.com.chromer.data.database.ChromerDatabase
import arun.com.chromer.data.database.HistoryDatabase
import arun.com.chromer.data.database.entity.WebsiteEntity
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.util.TestClock
import arun.com.chromer.util.setMillis
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The history store on Room keeps the old store's visit rule, recents, paging and the history switch. */
@RunWith(RobolectricTestRunner::class)
class RoomHistoryStoreTest {

  private val context: Context = RuntimeEnvironment.getApplication()
  private val database = Room.inMemoryDatabaseBuilder<ChromerDatabase>(context)
    .setDriver(AndroidSQLiteDriver())
    .build()
  private val dao = database.websiteDao()
  private val historyDatabase = HistoryDatabase(context, legacyImport = null, clock = TestClock()) { database }
  private val clock = TestClock(1_000L)
  private val store = RoomHistoryStore(historyDatabase, clock)

  @After
  fun close() = database.close()

  @Test
  fun aSecondVisitAddsOneAndKeepsTheStoredTitle() = runTest {
    store.insert(Website("https://example.com/").apply { title = "First" })
    assertThat(dao.getByUrl("https://example.com/")!!.createdAt).isEqualTo(1_000L)
    clock.setMillis(61_000L)
    store.insert(Website("https://example.com/").apply { title = "Second" })

    val row = dao.getByUrl("https://example.com/")!!
    assertThat(row.visitCount).isEqualTo(2)
    assertThat(row.createdAt).isEqualTo(61_000L)
    assertThat(row.title).isEqualTo("First")
    assertThat(dao.getCount()).isEqualTo(1)
  }

  @Test
  fun visitsToOneNewPageAtOnceGiveOneRowWithEveryVisit() = runTest {
    (1..8).map { async(Dispatchers.Default) { store.insert(Website("https://once.example/")) } }.awaitAll()

    assertThat(dao.getCount()).isEqualTo(1)
    assertThat(dao.getByUrl("https://once.example/")!!.visitCount).isEqualTo(8)
  }

  @Test
  fun recentsAreTheEightNewest() = runTest {
    (1..10).forEach { dao.insert(WebsiteEntity(url = "https://site$it.example/", createdAt = it * 1_000L)) }

    val recents = ModernHistoryRepository(historyDatabase, Dispatchers.Unconfined).getRecents().first().map { it.url }

    assertThat(recents).containsExactlyElementsIn((10 downTo 3).map { "https://site$it.example/" }).inOrder()
  }

  @Test
  fun theFirstPageHoldsTheNewestRowsAndPointsToTheNext() = runTest {
    (1..30).forEach { dao.insert(WebsiteEntity(url = "https://site$it.example/", createdAt = it * 1_000L)) }

    val page = dao.getAllPaged().load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

    page as PagingSource.LoadResult.Page
    assertThat(page.data.map { it.url }).containsExactlyElementsIn((30 downTo 11).map { "https://site$it.example/" }).inOrder()
    assertThat(page.nextKey).isNotNull()
  }

  @Test
  fun nothingIsStoredWhileHistoryIsOff() = runTest {
    val preferences = mockk<UserPreferencesRepository> {
      every { userPreferencesFlow } returns flowOf(UserPreferencesRepository.UserPreferences(incognitoMode = true))
    }
    val repository = DefaultHistoryRepository(store, preferences)

    repository.insert(Website("https://private.example/"))

    assertThat(dao.getCount()).isEqualTo(0)
  }
}
