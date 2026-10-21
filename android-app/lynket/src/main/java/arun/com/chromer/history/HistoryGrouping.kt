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
import android.text.format.DateUtils
import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import java.util.Calendar
import java.util.TimeZone

/** One item of the History list: a day header or a visited site. */
sealed interface HistoryListItem {
  val key: String

  data class DayHeader(val dayStart: Long) : HistoryListItem {
    override val key: String get() = "day-$dayStart"
  }

  data class Row(val website: Website) : HistoryListItem {
    override val key: String get() = "url-${website.url}"
  }
}

/** Midnight that starts the day of [millis] in [timeZone]. */
fun dayStart(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
  Calendar.getInstance(timeZone).apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
  }.timeInMillis

/** The day before the day that starts at [dayStart] (a day is not always 24 hours long). */
private fun previousDayStart(dayStart: Long, timeZone: TimeZone): Long =
  Calendar.getInstance(timeZone).apply {
    timeInMillis = dayStart
    add(Calendar.DAY_OF_MONTH, -1)
  }.timeInMillis

/**
 * Rows of the paged history with a day header before the first row and between rows of different
 * days, by the last visit time the list is ordered by.
 */
fun PagingData<Website>.withDayHeaders(timeZone: TimeZone = TimeZone.getDefault()): PagingData<HistoryListItem> =
  map<Website, HistoryListItem> { HistoryListItem.Row(it) }
    .insertSeparators { before, after ->
      val next = (after as? HistoryListItem.Row)?.website ?: return@insertSeparators null
      val nextDay = dayStart(next.createdAt, timeZone)
      val previous = (before as? HistoryListItem.Row)?.website
      if (previous == null || dayStart(previous.createdAt, timeZone) != nextDay) HistoryListItem.DayHeader(nextDay) else null
    }

/** "Today", "Yesterday", otherwise the date, for the day that starts at [dayStart]. */
fun dayLabel(context: Context, dayStart: Long, now: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
  val today = dayStart(now, timeZone)
  return when (dayStart) {
    today -> context.getString(R.string.today)
    previousDayStart(today, timeZone) -> context.getString(R.string.yesterday)
    else -> {
      val sameYear = Calendar.getInstance(timeZone).apply { timeInMillis = dayStart }.get(Calendar.YEAR) ==
        Calendar.getInstance(timeZone).apply { timeInMillis = now }.get(Calendar.YEAR)
      val flags = DateUtils.FORMAT_SHOW_DATE or (if (sameYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR)
      java.util.Formatter().use { formatter ->
        DateUtils.formatDateRange(context, formatter, dayStart, dayStart, flags, timeZone.id).toString()
      }
    }
  }
}
