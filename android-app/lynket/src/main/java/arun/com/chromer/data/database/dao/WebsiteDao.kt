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

package arun.com.chromer.data.database.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import arun.com.chromer.data.database.entity.WebsiteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 1.3: Room DAO for Website/History operations
 *
 * The one history DAO (Room 3); the old SQLite history store is read once by LegacyHistoryImport. It uses:
 * - Kotlin Coroutines (suspend functions) for async operations
 * - Flow for reactive data streams
 * - PagingSource for efficient list pagination
 *
 * All database operations are now type-safe, null-safe, and properly async.
 */
@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface WebsiteDao {

    /**
     * Get all history items as a Flow, sorted by most recent first.
     * Flow emits updates whenever the database changes.
     */
    @Query("SELECT * FROM history ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<WebsiteEntity>>

    /**
     * Get recent history items (limit 8) as a Flow.
     * Emits updates in real-time as history changes.
     */
    @Query("SELECT * FROM history ORDER BY created_at DESC LIMIT 8")
    fun getRecentsFlow(): Flow<List<WebsiteEntity>>

    /**
     * Get a paginated list of history items for RecyclerView.
     * Efficiently handles large datasets with automatic loading.
     */
    @Query("SELECT * FROM history ORDER BY created_at DESC")
    fun getAllPaged(): PagingSource<Int, WebsiteEntity>

    /**
     * Get a website by its URL.
     * Returns null if not found.
     */
    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): WebsiteEntity?

    /**
     * Get a website by its URL as a Flow.
     * Emits updates whenever this website changes.
     */
    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    fun getByUrlFlow(url: String): Flow<WebsiteEntity?>

    /**
     * Search history by URL or title.
     * Case-insensitive search, returns up to 5 results. `%`, `_` and `\` in the query match
     * themselves, not any text (they are escaped in each search query).
     */
    @Query("""
        SELECT * FROM history
        WHERE url LIKE '%' || replace(replace(replace(:query, '\', '\\'), '%', '\%'), '_', '\_') || '%' ESCAPE '\'
           OR title LIKE '%' || replace(replace(replace(:query, '\', '\\'), '%', '\%'), '_', '\_') || '%' ESCAPE '\'
        ORDER BY created_at DESC
        LIMIT 5
    """)
    suspend fun search(query: String): List<WebsiteEntity>

    /**
     * Search history by URL or title as a Flow.
     */
    @Query("""
        SELECT * FROM history
        WHERE url LIKE '%' || replace(replace(replace(:query, '\', '\\'), '%', '\%'), '_', '\_') || '%' ESCAPE '\'
           OR title LIKE '%' || replace(replace(replace(:query, '\', '\\'), '%', '\%'), '_', '\_') || '%' ESCAPE '\'
        ORDER BY created_at DESC
        LIMIT 5
    """)
    fun searchFlow(query: String): Flow<List<WebsiteEntity>>

    /**
     * Search history by URL or title, every match, newest first, as pages for the History screen.
     * [search] keeps its limit of 5 for the home suggestions.
     */
    @Query("""
        SELECT * FROM history
        WHERE url LIKE '%' || replace(replace(replace(:query, '\', '\\'), '%', '\%'), '_', '\_') || '%' ESCAPE '\'
           OR title LIKE '%' || replace(replace(replace(:query, '\', '\\'), '%', '\%'), '_', '\_') || '%' ESCAPE '\'
        ORDER BY created_at DESC
    """)
    fun searchPaged(query: String): PagingSource<Int, WebsiteEntity>

    /**
     * Get all bookmarked websites.
     */
    @Query("SELECT * FROM history WHERE bookmarked = 1 ORDER BY created_at DESC")
    fun getBookmarksFlow(): Flow<List<WebsiteEntity>>

    /**
     * Check if a URL exists in the database.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM history WHERE url = :url LIMIT 1)")
    suspend fun exists(url: String): Boolean

    /**
     * Get the visit count for a URL.
     */
    @Query("SELECT visit_count FROM history WHERE url = :url LIMIT 1")
    suspend fun getVisitCount(url: String): Int?

    /**
     * Insert a website. If URL already exists, replace it.
     * Returns the row ID of the inserted item.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(website: WebsiteEntity): Long

    /**
     * Insert multiple websites.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(websites: List<WebsiteEntity>)

    /**
     * Update a website.
     */
    @Update
    suspend fun update(website: WebsiteEntity)

    /**
     * Delete a website.
     */
    @Delete
    suspend fun delete(website: WebsiteEntity)

    /**
     * Delete a website by URL.
     */
    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    /**
     * Delete all history (nuclear option).
     */
    @Query("DELETE FROM history")
    suspend fun deleteAll()

    /**
     * Delete history older than a certain timestamp.
     * Useful for cleanup jobs.
     */
    @Query("DELETE FROM history WHERE created_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Increment visit count for a URL.
     * Updates the created_at timestamp to mark it as recently visited.
     */
    @Query("""
        UPDATE history
        SET visit_count = visit_count + 1,
            created_at = :timestamp
        WHERE url = :url
    """)
    suspend fun incrementVisitCount(url: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Toggle bookmark status for a URL.
     */
    @Query("UPDATE history SET bookmarked = NOT bookmarked WHERE url = :url")
    suspend fun toggleBookmark(url: String)

    /**
     * Set bookmark status for a URL.
     */
    @Query("UPDATE history SET bookmarked = :bookmarked WHERE url = :url")
    suspend fun setBookmarked(url: String, bookmarked: Boolean)

    /**
     * Get the total count of history items.
     */
    @Query("SELECT COUNT(*) FROM history")
    suspend fun getCount(): Int

    /**
     * Get the total count of bookmarked items.
     */
    @Query("SELECT COUNT(*) FROM history WHERE bookmarked = 1")
    suspend fun getBookmarkCount(): Int

    /**
     * Merges rows read from an older history store, in one transaction.
     *
     * One row per URL remains. When the URL is already stored, the newest visit time and the larger
     * visit count win; title, icons and other details come from the newer row, and an empty detail
     * is filled from the other row; the row is bookmarked when either is. A visit count below 1
     * counts as 1. Running it twice with the same rows gives the same table.
     *
     * @return the number of rows that were merged into an existing URL.
     */
    /**
     * Records one visit to [website] at [now], in one transaction: a new row with one visit, or
     * one more visit and the new visit time for a stored URL (its title and other details stay).
     * Two visits to the same new page at the same moment give one row with two visits.
     *
     * @return the row as stored.
     */
    @Transaction
    suspend fun recordVisit(website: WebsiteEntity, now: Long): WebsiteEntity {
        val stored = getByUrl(website.url)
        val visited = if (stored != null) {
            stored.copy(visitCount = stored.visitCount + 1, createdAt = now)
        } else {
            website.copy(id = 0, createdAt = now, visitCount = 1)
        }
        if (stored != null) update(visited) else insert(visited)
        return visited
    }

    @Transaction
    suspend fun mergeLegacyRows(rows: List<WebsiteEntity>): Int {
        var merged = 0
        for (row in rows) {
            val incoming = row.copy(id = 0, visitCount = maxOf(1, row.visitCount))
            val stored = getByUrl(incoming.url)
            if (stored == null) {
                insert(incoming)
            } else {
                update(mergeHistoryRows(stored, incoming).copy(id = stored.id))
                merged++
            }
        }
        return merged
    }
}

/**
 * The merge rule for two rows of the same URL: newest time, larger count, details from the newer
 * row with empty details filled from the older one, bookmarked when either is.
 */
fun mergeHistoryRows(a: WebsiteEntity, b: WebsiteEntity): WebsiteEntity {
    val (newer, older) = if (b.createdAt > a.createdAt) b to a else a to b
    return newer.copy(
        title = newer.title ?: older.title,
        faviconUrl = newer.faviconUrl ?: older.faviconUrl,
        canonicalUrl = newer.canonicalUrl ?: older.canonicalUrl,
        themeColor = newer.themeColor ?: older.themeColor,
        ampUrl = newer.ampUrl ?: older.ampUrl,
        bookmarked = a.bookmarked || b.bookmarked,
        createdAt = maxOf(a.createdAt, b.createdAt),
        visitCount = maxOf(a.visitCount, b.visitCount)
    )
}
