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

package arun.com.chromer.ui.history

import arun.com.chromer.history.HistoryListItem
import arun.com.chromer.history.dayStart
import arun.com.chromer.history.dayLabel
import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.TimeZone

/** Day headers of the History list: where they go and what they say. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class HistoryGroupingTest {

  @get:Rule(order = 0)
  val uiDispatcherReset = UiDispatcherResetRule()

  @get:Rule(order = 1)
  val composeRule = createComposeRule()

  private val context: Application get() = ApplicationProvider.getApplicationContext()
  private val day = 86_400_000L

  @Test
  fun aDayStartsAtLocalMidnight() {
    val utc = TimeZone.getTimeZone("UTC")
    assertThat(dayStart(TEST_NOW, utc)).isEqualTo(TEST_NOW - 12 * 3_600_000L)
    // Just after midnight in Kolkata (UTC+5:30) is still the previous day in UTC.
    val kolkata = TimeZone.getTimeZone("Asia/Kolkata")
    val justAfterMidnightKolkata = dayStart(TEST_NOW, kolkata) + 60_000L
    assertThat(dayStart(justAfterMidnightKolkata, kolkata)).isEqualTo(dayStart(TEST_NOW, kolkata))
    assertThat(dayStart(justAfterMidnightKolkata, utc)).isEqualTo(dayStart(TEST_NOW, utc) - day)
  }

  @Test
  fun labelsSayTodayYesterdayOrTheDate() {
    val today = dayStart(TEST_NOW, TEST_ZONE)
    assertThat(dayLabel(context, today, TEST_NOW, TEST_ZONE)).isEqualTo("Today")
    assertThat(dayLabel(context, today - day, TEST_NOW, TEST_ZONE)).isEqualTo("Yesterday")
    assertThat(dayLabel(context, today - 4 * day, TEST_NOW, TEST_ZONE)).isEqualTo("March 10")
  }

  @Test
  fun aDayOfAnotherYearShowsTheYear() {
    val newYearsEve = dayStart(TEST_NOW, TEST_ZONE) - 73 * day // 31 December 2025
    assertThat(dayLabel(context, newYearsEve, TEST_NOW, TEST_ZONE)).isEqualTo("December 31, 2025")
    // Just after midnight on 1 January the day before is still "Yesterday".
    val newYear = newYearsEve + day + 60_000L
    assertThat(dayLabel(context, newYearsEve, newYear, TEST_ZONE)).isEqualTo("Yesterday")
  }

  @Test
  fun aHeaderGoesBeforeEachDay() {
    lateinit var items: LazyPagingItems<HistoryListItem>
    composeRule.setContent { items = flowOf(loaded(testSites)).collectAsLazyPagingItems() }
    composeRule.waitForIdle()
    val today = dayStart(TEST_NOW, TEST_ZONE)
    val kinds = items.itemSnapshotList.items.map {
      when (it) {
        is HistoryListItem.DayHeader -> "day:${(today - it.dayStart) / day}"
        is HistoryListItem.Row -> it.website.title
      }
    }
    assertThat(kinds).containsExactly(
      "day:0", "Compose BOM mapping", "Navigation 3", "r/androiddev",
      "day:1", "Kotlin coroutines guide", "lynket-browser",
      "day:4", "Show HN: a tiny browser",
    ).inOrder()
  }

  @Test
  fun noRowsMeansNoHeader() {
    lateinit var items: LazyPagingItems<HistoryListItem>
    composeRule.setContent { items = flowOf(loaded(emptyList())).collectAsLazyPagingItems() }
    composeRule.waitForIdle()
    assertThat(items.itemCount).isEqualTo(0)
  }
}
