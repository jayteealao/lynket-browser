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
import arun.com.chromer.tabs.ARTICLE
import arun.com.chromer.tabs.CUSTOM_TAB
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.WEB_VIEW
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

private fun worst(title: String?, url: String, hoursAgo: Long, count: Int = 1) =
  Website(title, url, null, null, null, null, false, TEST_NOW - hoursAgo * HOUR, count)

const val WORST_LONG_URL =
  "https://www.docs.engineering-platform.example-company.co.uk/teams/mobile/android/release-notes/2026/q3/build-4821"
const val WORST_SIBLING_URL =
  "https://www.docs.engineering-platform.example-company.co.uk/teams/mobile/android/release-notes/2026/q3/build-4822"
const val WORST_HOST_ONLY_URL = "https://github.com/"
const val WORST_IP_URL = "http://192.168.1.1:8080/settings/network/wireless?band=5ghz"
const val WORST_FILE_URL = "file:///storage/emulated/0/Download/report-2026-q3-final-v2.pdf"
const val WORST_CONTENT_URL = "content://downloads/document/12"

/**
 * The worst rows people keep, today and yesterday: a long host with a deep path and a sibling that
 * differs only at its end, an untitled host-only page, an IP address with a query, an untitled
 * `file:` download, a `content:` address and a 120-character title.
 */
val worstSites: List<Website> = listOf(
  worst("Release notes", WORST_LONG_URL, 1),
  worst("Release notes", WORST_SIBLING_URL, 2),
  worst(null, WORST_HOST_ONLY_URL, 3),
  worst("Router settings", WORST_IP_URL, 4),
  worst(null, WORST_FILE_URL, 20),
  worst("Downloaded document", WORST_CONTENT_URL, 22),
  worst(
    "A very long page title that runs well past the width of any phone screen and keeps on going to one hundred and twenty!!!",
    "https://example.org/articles/long-title",
    26,
  ),
)

/** Three rows today and one yesterday: the rows the motion tests delete, close and put back. */
val twoDaySites: List<Website> = listOf(
  site(1, 1, "Compose BOM mapping"),
  site(2, 2, "Navigation 3"),
  site(3, 3, "r/androiddev"),
  site(4, 26, "lynket-browser"),
)

/** The worst open tabs: a long address, an untitled host-only Article tab, an IP and an untitled file. */
val worstTabs: List<TabsManager.Tab> = listOf(
  TabsManager.Tab(WORST_LONG_URL, CUSTOM_TAB, worst("Release notes", WORST_LONG_URL, 0)),
  TabsManager.Tab(WORST_HOST_ONLY_URL, ARTICLE, worst(null, WORST_HOST_ONLY_URL, 0)),
  TabsManager.Tab(WORST_IP_URL, WEB_VIEW, worst("Router settings", WORST_IP_URL, 0)),
  TabsManager.Tab(WORST_FILE_URL, CUSTOM_TAB, worst(null, WORST_FILE_URL, 0)),
)
