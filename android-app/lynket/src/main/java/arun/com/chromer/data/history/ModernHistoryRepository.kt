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

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.map
import arun.com.chromer.data.database.HistoryDatabase
import arun.com.chromer.data.database.entity.toEntity
import arun.com.chromer.data.database.entity.toWebsite
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.di.hilt.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * History for the History screen and the home: recents, paged history and search, delete with
 * undo, delete all, bookmarks and the row count.
 *
 * It reads the same Room table as [RoomHistoryStore], which records visits from the link path and
 * serves suggestions. Visits are recorded only through [HistoryRepository] (the link path), which
 * applies the History switch; every other read and change of history is here.
 */
@Singleton
class ModernHistoryRepository @Inject constructor(
    private val historyDatabase: HistoryDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** The most recent history rows (8), updated as history changes; empty when history cannot be read. */
    fun getRecents(): Flow<List<Website>> {
        return flow { emitAll(historyDatabase.dao().getRecentsFlow()) }
            .map { entities -> entities.map { it.toWebsite() } }
            .catch {
                Timber.e(it, "Recent history could not be read")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    /** Every history row, newest first, in pages. */
    fun getPagedHistory(): Flow<PagingData<Website>> {
        return flow {
            val dao = historyDatabase.dao()
            emitAll(Pager(RoomHistoryStore.PAGING_CONFIG) { dao.getAllPaged() }.flow)
        }
            .map { pagingData -> pagingData.map { it.toWebsite() } }
            .catch {
                Timber.e(it, "History could not be read")
                emit(PagingData.empty())
            }
            .flowOn(ioDispatcher)
    }

    /**
     * History rows whose URL or title contains [query], every match, newest first, in pages.
     */
    fun getPagedSearch(query: String): Flow<PagingData<Website>> {
        return flow {
            val dao = historyDatabase.dao()
            emitAll(Pager(RoomHistoryStore.PAGING_CONFIG) { dao.searchPaged(query) }.flow)
        }
            .map { pagingData -> pagingData.map { it.toWebsite() } }
            .catch {
                Timber.e(it, "History could not be read")
                emit(PagingData.empty())
            }
            .flowOn(ioDispatcher)
    }

    /**
     * Puts a deleted row back as it was: its last-visit time, visit count and bookmark (the undo of
     * a delete). A row recorded again since the delete is replaced.
     */
    suspend fun restore(website: Website): Boolean = withContext(ioDispatcher) {
        try {
            historyDatabase.dao().insert(website.toEntity())
            Timber.d("Restored website: ${website.url}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error restoring website: ${website.url}")
            false
        }
    }

    /**
     * Delete a website from history.
     */
    suspend fun delete(website: Website): Boolean = withContext(ioDispatcher) {
        try {
            historyDatabase.dao().deleteByUrl(website.url)
            Timber.d("Deleted website: ${website.url}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error deleting website: ${website.url}")
            false
        }
    }

    /**
     * Deletes every history row, and the copies of history kept on the device (the moved history
     * file of release 2.1.3 and any database moved aside as broken), so nothing of it stays.
     *
     * @return the number of rows deleted.
     */
    suspend fun deleteAll(): Int = withContext(ioDispatcher) {
        try {
            val count = historyDatabase.dao().getCount()
            historyDatabase.dao().deleteAll()
            historyDatabase.deleteKeptCopies()
            Timber.d("Deleted all history: $count items")
            count
        } catch (e: Exception) {
            Timber.e(e, "Error deleting all history")
            0
        }
    }

    /**
     * Toggle bookmark status for a website.
     */
    suspend fun toggleBookmark(url: String) = withContext(ioDispatcher) {
        try {
            historyDatabase.dao().toggleBookmark(url)
            Timber.d("Toggled bookmark for: $url")
        } catch (e: Exception) {
            Timber.e(e, "Error toggling bookmark for: $url")
        }
    }

    /**
     * Get total history count.
     */
    suspend fun getCount(): Int = withContext(ioDispatcher) {
        historyDatabase.dao().getCount()
    }
}
