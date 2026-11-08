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

package arun.com.chromer.history

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import arun.com.chromer.data.database.ChromerDatabase
import arun.com.chromer.data.database.HistoryDatabase
import arun.com.chromer.data.history.ModernHistoryRepository
import arun.com.chromer.data.history.RoomHistoryStore
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.MainDispatcherRule
import arun.com.chromer.settings.Preferences
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.history.TEST_NOW
import arun.com.chromer.ui.history.TEST_ZONE
import arun.com.chromer.util.TestClock
import arun.com.chromer.util.events.EventBus
import arun.com.chromer.util.setMillis
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.TimeZone

/**
 * What the History pager emits, on a real Room database and a fixed clock: rows newest first with
 * a day header before each day, after a refresh that follows new visits, and for a search.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryPagerSnapshotTest {

  private val dispatcher = StandardTestDispatcher()

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(dispatcher)

  private val context: Context = RuntimeEnvironment.getApplication()
  private val clock = TestClock(TEST_NOW)
  private lateinit var database: ChromerDatabase
  private lateinit var store: RoomHistoryStore
  private lateinit var vm: ModernHistoryViewModel
  private lateinit var defaultZone: TimeZone

  @Before
  fun setUp() {
    defaultZone = TimeZone.getDefault()
    TimeZone.setDefault(TEST_ZONE)
    database = Room.inMemoryDatabaseBuilder<ChromerDatabase>(context)
      .setDriver(AndroidSQLiteDriver())
      .setQueryCoroutineContext(dispatcher)
      .build()
    val historyDatabase = HistoryDatabase(context, legacyImport = null, clock = clock) { database }
    store = RoomHistoryStore(historyDatabase, clock)
    vm = ModernHistoryViewModel(
      ModernHistoryRepository(historyDatabase, ioDispatcher = dispatcher),
      mockk<Preferences>(relaxed = true),
      mockk<UserPreferencesRepository>(relaxed = true),
      mockk<EventBus>(relaxed = true),
      mockk<TabsManager>(relaxed = true),
      clock,
    )
  }

  @After
  fun tearDown() {
    database.close()
    TimeZone.setDefault(defaultZone)
  }

  @Test
  fun thePagerListsDaysNewestFirstThroughARefreshAndASearch() = runTest(dispatcher) {
    visit(at(-2, 10, 0), "https://example.org/a", title = "Org A")
    visit(at(-2, 11, 0), "https://example.com/b")
    visit(at(-1, 9, 0), "https://news.example.org/c")
    visit(at(0, 8, 0), "https://example.com/d")
    visit(at(0, 8, 30), "https://example.com/e")

    assertThat(vm.items.asSnapshot().entries()).containsExactly(
      day(0),
      row("https://example.com/e", at(0, 8, 30)),
      row("https://example.com/d", at(0, 8, 0)),
      day(-1),
      row("https://news.example.org/c", at(-1, 9, 0)),
      day(-2),
      row("https://example.com/b", at(-2, 11, 0)),
      row("https://example.org/a", at(-2, 10, 0)),
    ).inOrder()

    // A new page and a repeat visit of an old one: the old row moves to today.
    visit(at(0, 9, 0), "https://example.com/f")
    visit(at(0, 9, 10), "https://example.org/a")

    assertThat(vm.items.asSnapshot { refresh() }.entries()).containsExactly(
      day(0),
      row("https://example.org/a", at(0, 9, 10)),
      row("https://example.com/f", at(0, 9, 0)),
      row("https://example.com/e", at(0, 8, 30)),
      row("https://example.com/d", at(0, 8, 0)),
      day(-1),
      row("https://news.example.org/c", at(-1, 9, 0)),
      day(-2),
      row("https://example.com/b", at(-2, 11, 0)),
    ).inOrder()

    // Past the search pause, only matching rows remain; the day with none left has no header.
    backgroundScope.launch { vm.items.collect { } }
    vm.openSearch()
    vm.setQuery("example.org")
    advanceTimeBy(400)
    runCurrent()

    assertThat(vm.items.asSnapshot().entries()).containsExactly(
      day(0),
      row("https://example.org/a", at(0, 9, 10)),
      day(-1),
      row("https://news.example.org/c", at(-1, 9, 0)),
    ).inOrder()
  }

  /** Records a visit of [url] at [millis] on the app clock, as the browser does. */
  private suspend fun visit(millis: Long, url: String, title: String? = null) {
    clock.setMillis(millis)
    store.insert(Website(url).apply { this.title = title })
  }

  /** The key of each item, and the visit time of each row, so headers and times are both checked. */
  private fun List<HistoryListItem>.entries(): List<String> = map { item ->
    when (item) {
      is HistoryListItem.DayHeader -> item.key
      is HistoryListItem.Row -> "${item.key} @${item.website.createdAt}"
    }
  }

  private fun day(offset: Int) = "day-${dayStart(at(offset, 0, 0), TEST_ZONE)}"

  private fun row(url: String, visitedAt: Long) = "url-$url @$visitedAt"

  /** [hour]:[minute] UTC on the day [dayOffset] days from the day of [TEST_NOW]. */
  private fun at(dayOffset: Int, hour: Int, minute: Int): Long =
    dayStart(TEST_NOW, TEST_ZONE) + dayOffset * DAY + hour * HOUR + minute * MINUTE

  private companion object {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR
  }
}
