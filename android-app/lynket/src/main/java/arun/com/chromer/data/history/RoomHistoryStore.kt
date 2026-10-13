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

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import arun.com.chromer.data.database.HistoryDatabase
import arun.com.chromer.data.database.entity.WebsiteEntity
import arun.com.chromer.data.database.entity.toEntity
import arun.com.chromer.data.database.entity.toWebsite
import arun.com.chromer.data.website.model.Website
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The history store behind [HistoryRepository], on the Room database (`chromer.db`).
 *
 * It keeps the visit rule of the old SQLite store: opening a known URL adds one visit, sets the
 * time to now and keeps the stored title and icons; a new URL starts at one visit.
 */
@Singleton
class RoomHistoryStore
@Inject
constructor(private val historyDatabase: HistoryDatabase) : HistoryStore {

  override suspend fun get(website: Website): Website? =
    historyDatabase.dao().getByUrl(website.url)?.toWebsite()

  override suspend fun insert(website: Website): Website {
    val dao = historyDatabase.dao()
    val stored = dao.getByUrl(website.url)
    return if (stored != null) {
      visitAgain(stored)
    } else {
      val entity = website.toEntity().copy(createdAt = System.currentTimeMillis(), visitCount = 1)
      dao.insert(entity)
      entity.toWebsite()
    }
  }

  override suspend fun update(website: Website): Website {
    val stored = historyDatabase.dao().getByUrl(website.url) ?: return website
    return visitAgain(stored)
  }

  private suspend fun visitAgain(stored: WebsiteEntity): Website {
    val visited = stored.copy(visitCount = stored.visitCount + 1, createdAt = System.currentTimeMillis())
    historyDatabase.dao().update(visited)
    Timber.d("Updated %s in history", stored.url)
    return visited.toWebsite()
  }

  override suspend fun delete(website: Website): Website {
    historyDatabase.dao().deleteByUrl(website.url)
    return website
  }

  override suspend fun exists(website: Website): Boolean = historyDatabase.dao().exists(website.url)

  override suspend fun deleteAll(): Int {
    val dao = historyDatabase.dao()
    val count = dao.getCount()
    dao.deleteAll()
    historyDatabase.deleteKeptCopies()
    return count
  }

  override fun recents(): Flow<List<Website>> =
    flow { emitAll(historyDatabase.dao().getRecentsFlow()) }
      .map { rows -> rows.map(WebsiteEntity::toWebsite) }
      .catch {
        Timber.e(it, "Recent history could not be read")
        emit(emptyList())
      }

  override suspend fun search(text: String): List<Website> =
    historyDatabase.dao().search(text).map(WebsiteEntity::toWebsite)

  override fun pagedHistory(): Flow<PagingData<Website>> =
    flow {
      val dao = historyDatabase.dao()
      emitAll(Pager(PAGING_CONFIG) { dao.getAllPaged() }.flow)
    }
      .map { page -> page.map(WebsiteEntity::toWebsite) }
      .catch {
        Timber.e(it, "History could not be read")
        emit(PagingData.empty())
      }

  companion object {
    val PAGING_CONFIG = PagingConfig(pageSize = 20, initialLoadSize = 10, enablePlaceholders = false)
  }
}
