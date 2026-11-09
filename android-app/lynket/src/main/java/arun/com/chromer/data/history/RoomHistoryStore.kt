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

import androidx.paging.PagingConfig
import arun.com.chromer.data.database.HistoryDatabase
import arun.com.chromer.data.database.entity.WebsiteEntity
import arun.com.chromer.data.database.entity.toWebsite
import arun.com.chromer.data.website.model.Website
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
class RoomHistoryStore
@Inject
constructor(
  private val historyDatabase: HistoryDatabase,
  private val clock: Clock
) : HistoryStore {

  override suspend fun get(website: Website): Website? =
    historyDatabase.dao().getByUrl(website.url)?.toWebsite()

  override suspend fun insert(website: Website) {
    with(website) {
      historyDatabase.dao().recordVisit(
        url = url,
        title = title,
        faviconUrl = faviconUrl,
        canonicalUrl = canonicalUrl,
        themeColor = themeColor,
        ampUrl = ampUrl,
        bookmarked = bookmarked,
        now = clock.now().toEpochMilliseconds()
      )
    }
    Timber.d("Recorded a visit to %s", website.url)
  }

  override suspend fun search(text: String): List<Website> =
    historyDatabase.dao().search(text).map(WebsiteEntity::toWebsite)

  companion object {
    val PAGING_CONFIG = PagingConfig(pageSize = 20, initialLoadSize = 10, enablePlaceholders = false)
  }
}
