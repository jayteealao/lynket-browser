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
import arun.com.chromer.history.withDayHeaders
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import arun.com.chromer.data.website.model.Website
import java.util.TimeZone

/** 12:00 UTC on 14 March 2026: the fixed "now" of the History tests and goldens. */
const val TEST_NOW = 1_773_489_600_000L
val TEST_ZONE: TimeZone = TimeZone.getTimeZone("UTC")
private const val HOUR = 3_600_000L

fun site(n: Int, hoursAgo: Long, title: String = "Seed page %03d".format(n), amp: Boolean = false, bookmarked: Boolean = false) =
  Website(
    title,
    "https://seed.example/page-%03d".format(n),
    null,
    null,
    null,
    if (amp) "https://seed.example/amp/page-%03d".format(n) else null,
    bookmarked,
    TEST_NOW - hoursAgo * HOUR,
    n % 7 + 1,
  )

/** Rows of today, yesterday and 10 March, newest first. */
val testSites: List<Website> = listOf(
  site(1, 1, "Compose BOM mapping"),
  site(2, 2, "Navigation 3", amp = true),
  site(3, 5, "r/androiddev", bookmarked = true),
  site(4, 20, "Kotlin coroutines guide"),
  site(5, 26, "lynket-browser"),
  site(6, 100, "Show HN: a tiny browser"),
)

fun loaded(sites: List<Website>): PagingData<HistoryListItem> =
  PagingData.from(sites, LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true)))
    .withDayHeaders(TEST_ZONE)

fun withRefresh(state: LoadState): PagingData<HistoryListItem> =
  PagingData.from(emptyList(), LoadStates(state, LoadState.NotLoading(false), LoadState.NotLoading(false)))
