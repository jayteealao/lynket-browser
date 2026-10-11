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
import arun.com.chromer.data.database.entity.WebsiteEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A history database written at version 2 opens with Room 3 at version 3: every row is there and
 * new rows insert.
 *
 * The version 2 file is built from the schema Room 2.8.5 exported (`schemas/.../2.json`: its
 * `createSql`, indices and identity row, `user_version` 2), as Room 2 wrote it. Room's
 * `MigrationTestHelper` cannot be used here: it reads the schema from instrumentation assets,
 * which the JVM test task does not have (repro: `FileNotFoundException ... Missing file:
 * arun.com.chromer.data.database.ChromerDatabase/2.json`, also with the schema folder added to the
 * test source set's assets). Opening the file with the app's builder runs the 2 to 3 migration and
 * Room's validation of every table, the same path a person's update takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChromerDatabaseMigrationTest {

  private val context: Context = RuntimeEnvironment.getApplication()
  private val file = context.getDatabasePath(NAME)

  @Before
  fun writeVersionTwoFile() {
    file.parentFile?.mkdirs()
    val schema = JSONObject(File(SCHEMA).readText()).getJSONObject("database")
    assertThat(schema.getInt("version")).isEqualTo(2)
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      val entities = schema.getJSONArray("entities")
      for (i in 0 until entities.length()) {
        val entity = entities.getJSONObject(i)
        val table = entity.getString("tableName")
        db.execSQL(entity.getString("createSql").replace(TABLE_PLACEHOLDER, table))
        val indices = entity.getJSONArray("indices")
        for (j in 0 until indices.length()) {
          db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_PLACEHOLDER, table))
        }
      }
      val setup = schema.getJSONArray("setupQueries")
      for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
      db.version = 2
      insertRows(db)
    }
  }

  private val rows = listOf(
    WebsiteEntity(
      url = "https://example.com/caf%C3%A9/über-straße?q=a%20b&x=1#top",
      title = "Café ? # %",
      faviconUrl = "https://example.com/favicon.ico",
      canonicalUrl = "https://example.com/cafe",
      themeColor = "#3F51B5",
      ampUrl = "https://example.com/amp",
      bookmarked = false,
      createdAt = 1_791_722_372_673,
      visitCount = 4
    ),
    WebsiteEntity(url = "https://www.wikipedia.org/", title = "Wikipedia", bookmarked = true, createdAt = 1_791_722_360_793, visitCount = 1),
    WebsiteEntity(url = "https://example.org/docs/page.html", title = null, createdAt = 1_791_722_355_862, visitCount = 2)
  )

  @Test
  fun everyRowAndColumnSurvivesTheMoveToVersionThree() = runTest {
    val db = ChromerDatabase.create(context, NAME)
    try {
      val stored = db.websiteDao().getAllFlow().first()
      assertThat(stored.map { it.withoutId() }).containsExactlyElementsIn(rows)
    } finally {
      db.close()
    }
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
      assertThat(raw.version).isEqualTo(3)
      raw.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use {
        assertThat(it.moveToFirst()).isTrue()
        assertThat(it.getString(0)).isNotEmpty()
      }
    }
  }

  @Test
  fun theAppDatabaseOpensAVersionTwoFileWithEveryRowAndInsertsNewRows() = runTest {
    val db = ChromerDatabase.create(context, NAME)
    try {
      val dao = db.websiteDao()
      assertThat(dao.getCount()).isEqualTo(3)
      rows.forEach { assertThat(dao.getByUrl(it.url)?.withoutId()).isEqualTo(it) }

      dao.insert(WebsiteEntity(url = "https://example.net/new", title = "New", createdAt = 1_800_000_000_000))
      assertThat(dao.getCount()).isEqualTo(4)
      assertThat(dao.getByUrl("https://example.net/new")?.title).isEqualTo("New")
    } finally {
      db.close()
    }
  }

  private fun insertRows(db: SQLiteDatabase) {
    rows.forEach { row ->
      db.execSQL(
        "INSERT INTO history (url, title, favicon_url, canonical_url, theme_color, amp_url, bookmarked, created_at, visit_count) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(
          row.url, row.title, row.faviconUrl, row.canonicalUrl, row.themeColor, row.ampUrl,
          if (row.bookmarked) 1 else 0, row.createdAt, row.visitCount
        )
      )
    }
  }

  private fun WebsiteEntity.withoutId() = copy(id = 0)

  private companion object {
    const val NAME = "migration-test"
    const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
    const val SCHEMA = "schemas/arun.com.chromer.data.database.ChromerDatabase/2.json"
  }
}
