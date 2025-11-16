// Phase 8: Converted from RxJava to Kotlin Coroutines
/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.data.history

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.PagedList
import arun.com.chromer.data.history.model.HistoryTable.ALL_COLUMN_PROJECTION
import arun.com.chromer.data.history.model.HistoryTable.COLUMN_AMP
import arun.com.chromer.data.history.model.HistoryTable.COLUMN_BOOKMARKED
import arun.com.chromer.data.history.model.HistoryTable.COLUMN_CANONICAL
import arun.com.chromer.data.history.model.HistoryTable.COLUMN_COLOR
import arun.com.chromer.data.history.model.HistoryTable.COLUMN_CREATED_AT
import arun.com.chromer.data.history.model.HistoryTable.COLUMN_FAVICON
import arun.com.chromer.data.history.model.HistoryTable.COLUMN_TITLE
import arun.com.chromer.data.history.model.HistoryTable.COLUMN_URL
import arun.com.chromer.data.history.model.HistoryTable.COLUMN_VISITED
import arun.com.chromer.data.history.model.HistoryTable.DATABASE_CREATE
import arun.com.chromer.data.history.model.HistoryTable.ORDER_BY_TIME_DESC
import arun.com.chromer.data.history.model.HistoryTable.TABLE_NAME
import arun.com.chromer.data.website.model.Website
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Arunkumar on 03-03-2017.
 */
@Singleton
class HistorySqlDiskStore
@Inject
internal constructor(application: Application) : SQLiteOpenHelper(
  application,
  TABLE_NAME,
  null,
  DATABASE_VERSION
), HistoryStore {

  private lateinit var database: SQLiteDatabase

  private val isOpen @Synchronized get() = ::database.isInitialized && database.isOpen

  private val changesFlow = MutableSharedFlow<Int>(replay = 0)

  override fun changes(): Flow<Int> = changesFlow.asSharedFlow()

  private suspend fun broadcastChanges() {
    changesFlow.emit(0)
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(DATABASE_CREATE)
    Timber.d("onCreate called")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

  }

  @Synchronized
  fun open() {
    if (!isOpen) {
      database = writableDatabase
    }
  }

  @Synchronized
  override fun close() {
    if (isOpen) {
      database.close()
    }
  }

  override suspend fun get(website: Website): Website? = withContext(Dispatchers.IO) {
    open()
    val cursor =
      database.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_URL=?", arrayOf(website.url))
    when {
      cursor == null -> null
      cursor.count == 0 -> {
        cursor.close()
        null
      }
      else -> {
        cursor.moveToFirst()
        val savedSite = Website.fromCursor(cursor)
        cursor.close()
        savedSite
      }
    }
  }

  override suspend fun insert(website: Website): Website = withContext(Dispatchers.IO) {
    val exists = exists(website)
    val result = if (exists) {
      update(website)
    } else {
      val values = ContentValues()
      values.put(COLUMN_URL, website.url)
      values.put(COLUMN_TITLE, website.title)
      values.put(COLUMN_FAVICON, website.faviconUrl)
      values.put(COLUMN_CANONICAL, website.canonicalUrl)
      values.put(COLUMN_COLOR, website.themeColor)
      values.put(COLUMN_AMP, website.ampUrl)
      values.put(COLUMN_BOOKMARKED, website.bookmarked)
      values.put(COLUMN_CREATED_AT, System.currentTimeMillis())
      values.put(COLUMN_VISITED, 1)
      if (database.insert(TABLE_NAME, null, values) != -1L) {
        website
      } else {
        website
      }
    }
    broadcastChanges()
    result
  }

  override suspend fun update(website: Website): Website = withContext(Dispatchers.IO) {
    val saved = get(website)
    val result = if (saved != null) {
      val values = ContentValues()
      values.put(COLUMN_URL, saved.url)
      values.put(COLUMN_TITLE, saved.title)
      values.put(COLUMN_FAVICON, saved.faviconUrl)
      values.put(COLUMN_CANONICAL, saved.canonicalUrl)
      values.put(COLUMN_COLOR, saved.themeColor)
      values.put(COLUMN_AMP, saved.ampUrl)
      values.put(COLUMN_BOOKMARKED, saved.bookmarked)
      values.put(COLUMN_CREATED_AT, System.currentTimeMillis())
      values.put(COLUMN_VISITED, ++saved.count)

      val whereClause = "$COLUMN_URL=?"
      val whereArgs = arrayOf(saved.url)

      if (database.update(TABLE_NAME, values, whereClause, whereArgs) > 0) {
        Timber.d("Updated %s in db", website.url)
        saved
      } else {
        Timber.e("Update failed for %s", website.url)
        website
      }
    } else {
      website
    }
    broadcastChanges()
    result
  }

  override suspend fun delete(website: Website): Website = withContext(Dispatchers.IO) {
    open()
    val whereClause = "$COLUMN_URL=?"
    val whereArgs = arrayOf(website.url)
    if (database.delete(TABLE_NAME, whereClause, whereArgs) > 0) {
      Timber.d("Deletion successful for %s", website.url)
    } else {
      Timber.e("Deletion failed for %s", website.url)
    }
    broadcastChanges()
    website
  }

  override suspend fun exists(website: Website): Boolean = withContext(Dispatchers.IO) {
    open()
    val selection = " $COLUMN_URL=?"
    val selectionArgs = arrayOf(website.url)
    val cursor = database.query(
      TABLE_NAME,
      ALL_COLUMN_PROJECTION,
      selection,
      selectionArgs, null, null, null
    )
    var exists = false
    if (cursor != null && cursor.count > 0) {
      exists = true
    }
    cursor?.close()
    exists
  }

  override suspend fun deleteAll(): Int = withContext(Dispatchers.IO) {
    open()
    val count = database.delete(TABLE_NAME, "1", null)
    broadcastChanges()
    count
  }

  override fun recents(): Flow<List<Website>> = flow {
    suspend fun loadRecents(): List<Website> = withContext(Dispatchers.IO) {
      open()
      val websites = ArrayList<Website>()
      database.query(
        TABLE_NAME,
        ALL_COLUMN_PROJECTION,
        null,
        null,
        null,
        null,
        ORDER_BY_TIME_DESC,
        "8"
      )?.use {
        while (it.moveToNext()) {
          websites.add(Website.fromCursor(it))
        }
      }
      websites
    }

    changes().onStart { emit(0) }.collect {
      emit(loadRecents())
    }
  }

  override suspend fun search(text: String): List<Website> = withContext(Dispatchers.IO) {
    open()
    val websites = ArrayList<Website>()
    database.query(
      true,
      TABLE_NAME,
      ALL_COLUMN_PROJECTION,
      "($COLUMN_URL like '%$text%' OR $COLUMN_TITLE like '%$text%')",
      null,
      null,
      null,
      ORDER_BY_TIME_DESC,
      "5"
    )?.use { cursor ->
      while (cursor.moveToNext()) {
        websites.add(Website.fromCursor(cursor))
      }
    }
    websites
  }

  override fun loadHistoryRange(limit: Int, offset: Int): List<Website> {
    open()
    val cursor = database.rawQuery(
      "SELECT * FROM $TABLE_NAME ORDER BY $ORDER_BY_TIME_DESC LIMIT $limit OFFSET $offset",
      null
    )
    cursor.moveToFirst()
    val websites = ArrayList<Website>()
    while (!cursor.isAfterLast) {
      websites += Website.fromCursor(cursor)
      cursor.moveToNext()
    }
    cursor.close()
    return websites
  }

  override fun pagedHistory(): LiveData<PagedList<Website>> = MutableLiveData()

  companion object {
    private const val DATABASE_VERSION = 1
  }
}
