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
     * Records one visit to [url] at [now], in one SQL statement: a new row with one visit and the
     * given details, or, for a stored URL, one more visit and the new visit time. A stored row keeps
     * its id, its title and its other details. Two visits to the same new page at the same moment
     * give one row with two visits, because one statement runs under SQLite's write lock.
     *
     * The statement rewrites a stored row with its own id (`INSERT OR REPLACE` of the row read in
     * the same statement), so the id never changes. It is not `@Upsert`, which can replace the row
     * on the unique `url` index and give it a new id, and not `ON CONFLICT ... DO UPDATE`, which
     * needs SQLite 3.24: the platform SQLite on Android 7 to 10 is 3.9 to 3.22.
     */
    @Query("""
        INSERT OR REPLACE INTO history
            (id, url, title, favicon_url, canonical_url, theme_color, amp_url, bookmarked, created_at, visit_count)
        SELECT seen.id,
               :url,
               CASE WHEN seen.id IS NULL THEN :title ELSE seen.title END,
               CASE WHEN seen.id IS NULL THEN :faviconUrl ELSE seen.favicon_url END,
               CASE WHEN seen.id IS NULL THEN :canonicalUrl ELSE seen.canonical_url END,
               CASE WHEN seen.id IS NULL THEN :themeColor ELSE seen.theme_color END,
               CASE WHEN seen.id IS NULL THEN :ampUrl ELSE seen.amp_url END,
               CASE WHEN seen.id IS NULL THEN :bookmarked ELSE seen.bookmarked END,
               :now,
               IFNULL(seen.visit_count, 0) + 1
        FROM (SELECT 1) LEFT JOIN history AS seen ON seen.url = :url
    """)
    suspend fun recordVisit(
        url: String,
        title: String?,
        faviconUrl: String?,
        canonicalUrl: String?,
        themeColor: String?,
        ampUrl: String?,
        bookmarked: Boolean,
        now: Long
    )

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
