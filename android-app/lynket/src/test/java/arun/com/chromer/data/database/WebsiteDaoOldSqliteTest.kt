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

package arun.com.chromer.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import arun.com.chromer.data.database.entity.WebsiteEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.SQLiteMode

/**
 * The visit statement on an SQLite without UPSERT (older than 3.24), as on Android 7 to 10, where
 * the platform SQLite is 3.9 to 3.22. Robolectric's legacy SQLite engine stands in for it.
 */
@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class WebsiteDaoOldSqliteTest {

  private val context: Context = RuntimeEnvironment.getApplication()
  private val database = Room.inMemoryDatabaseBuilder<ChromerDatabase>(context)
    .setDriver(AndroidSQLiteDriver())
    .build()
  private val dao = database.websiteDao()

  @After
  fun close() = database.close()

  @Test
  fun theEngineIsOlderThanUpsert() {
    val version = SQLiteDatabase.create(null).use { db ->
      db.rawQuery("SELECT sqlite_version()", null).use { it.moveToFirst(); it.getString(0) }
    }
    val (major, minor) = version.split('.').map(String::toInt)

    assertThat(major * 1000 + minor).isLessThan(3 * 1000 + 24)
  }

  @Test
  fun aStoredRowKeepsItsIdAndDetailsAndANewUrlGetsOneVisit() = runTest {
    val id = dao.insert(WebsiteEntity(url = "https://a.example/", title = "Stored", bookmarked = true, createdAt = 1_000L, visitCount = 3))

    dao.recordVisit("https://a.example/", "Newer", null, null, null, null, bookmarked = false, now = 2_000L)
    dao.recordVisit("https://b.example/", "B", "https://b.example/icon.png", null, null, null, bookmarked = false, now = 3_000L)

    val a = dao.getByUrl("https://a.example/")!!
    val b = dao.getByUrl("https://b.example/")!!
    assertThat(a).isEqualTo(WebsiteEntity(id = id, url = "https://a.example/", title = "Stored", bookmarked = true, createdAt = 2_000L, visitCount = 4))
    assertThat(b.id).isGreaterThan(id)
    assertThat(b.title).isEqualTo("B")
    assertThat(b.faviconUrl).isEqualTo("https://b.example/icon.png")
    assertThat(b.createdAt).isEqualTo(3_000L)
    assertThat(b.visitCount).isEqualTo(1)
    assertThat(dao.getCount()).isEqualTo(2)
  }
}
