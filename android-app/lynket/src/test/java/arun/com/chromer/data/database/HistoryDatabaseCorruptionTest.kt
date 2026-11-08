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
import arun.com.chromer.data.database.legacy.LegacyHistoryImport
import arun.com.chromer.data.history.ModernHistoryRepository
import arun.com.chromer.data.history.RoomHistoryStore
import arun.com.chromer.data.preferences.RecordingTree
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.util.TestClock
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import timber.log.Timber
import java.io.File
import kotlin.random.Random

/** A broken history database opens as empty history, with a logged error, no crash and the file kept. */
@RunWith(RobolectricTestRunner::class)
class HistoryDatabaseCorruptionTest {

  private val context: Context = RuntimeEnvironment.getApplication()
  private val dbFile = context.getDatabasePath(ChromerDatabase.DATABASE_NAME)
  private val tree = RecordingTree()
  private val clock = TestClock(1_234_567L)
  private val historyDatabase = HistoryDatabase(context, LegacyHistoryImport(context, clock), clock)

  @Before
  fun plant() {
    Timber.plant(tree)
    dbFile.parentFile?.mkdirs()
  }

  @After
  fun tearDown() = runTest {
    Timber.uproot(tree)
    historyDatabase.get().close()
  }

  @Test
  fun aFileOfRandomBytesOpensAsEmptyHistory() = runTest {
    val garbage = Random(11).nextBytes(16_384)
    dbFile.writeBytes(garbage)

    assertBrokenFileHandled(garbage)
  }

  @Test
  fun aDatabaseWithAWrongHistoryTableOpensAsEmptyHistory() = runTest {
    SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
      db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, url TEXT NOT NULL)")
      db.execSQL("INSERT INTO history (url) VALUES ('https://broken.example/')")
      db.version = 2
    }

    // Room opened the file in WAL mode before its check failed and rolled back, so the kept copy
    // differs in header bytes; what must hold is that the person's rows are still in it.
    assertBrokenFileHandled { kept ->
      SQLiteDatabase.openDatabase(kept.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT url FROM history", null).use {
          assertThat(it.moveToFirst()).isTrue()
          assertThat(it.getString(0)).isEqualTo("https://broken.example/")
        }
      }
    }
  }

  /**
   * Checks that opening the file failed with Room's own schema error, that its text is the
   * [expectedEntry] of [HistoryDatabase.ROOM_SCHEMA_ERRORS], that the file was treated as broken
   * (not left in place for a retry), and that the kept copy still holds the row of [keptRowUrl].
   */
  private suspend fun assertRoomSchemaErrorHandled(expectedEntry: String, keptRowUrl: String) {
    assertBrokenFileHandled { kept ->
      SQLiteDatabase.openDatabase(kept.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT url FROM history", null).use {
          assertThat(it.moveToFirst()).isTrue()
          assertThat(it.getString(0)).isEqualTo(keptRowUrl)
        }
      }
    }

    val roomError = tree.throwables
      .flatMap { logged -> generateSequence(logged) { it.cause }.toList() }
      .firstOrNull { cause ->
        cause is IllegalStateException && cause.stackTrace.any { it.className.startsWith("androidx.room3.") }
      }
    assertWithMessage("Room's IllegalStateException among the logged errors: %s", tree.throwables)
      .that(roomError).isNotNull()
    val message = roomError!!.message.orEmpty()
    assertThat(message.lowercase()).contains(expectedEntry.lowercase())
    assertThat(HistoryDatabase.ROOM_SCHEMA_ERRORS).contains(expectedEntry)
    assertWithMessage("Room's text \"%s\" matches an entry of ROOM_SCHEMA_ERRORS", message)
      .that(HistoryDatabase.ROOM_SCHEMA_ERRORS.any { message.contains(it, ignoreCase = true) }).isTrue()

    assertThat(tree.errors.any { it.startsWith("History database could not be opened; history starts empty") })
      .isTrue()
    assertThat(tree.errors.none { it.contains("stays in place") }).isTrue()
  }

  /** Writes the tables and indexes of schema version 3 (`schemas/.../3.json`), with no identity row. */
  private fun createVersion3Tables(db: SQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`url` TEXT NOT NULL, `title` TEXT, `favicon_url` TEXT, `canonical_url` TEXT, `theme_color` TEXT, " +
        "`amp_url` TEXT, `bookmarked` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `visit_count` INTEGER NOT NULL)",
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_history_url` ON `history` (`url`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_created_at` ON `history` (`created_at`)")
  }

  private suspend fun assertBrokenFileHandled(original: ByteArray) =
    assertBrokenFileHandled { kept -> assertThat(kept.readBytes()).isEqualTo(original) }

  private suspend fun assertBrokenFileHandled(checkKept: (File) -> Unit) {
    val store = RoomHistoryStore(historyDatabase, clock)
    val history = ModernHistoryRepository(historyDatabase, Dispatchers.Unconfined)

    assertThat(history.getRecents().first()).isEmpty()
    assertThat(tree.errors).isNotEmpty()
    val kept = File(context.filesDir, HistoryDatabase.BROKEN_FOLDER).walk()
      .filter { it.name == ChromerDatabase.DATABASE_NAME }.toList()
    assertThat(kept).hasSize(1)
    assertThat(kept[0].parentFile).isEqualTo(File(context.filesDir, "${HistoryDatabase.BROKEN_FOLDER}/1234567"))
    checkKept(kept[0])

    store.insert(Website("https://after.example/"))
    assertThat(history.getRecents().first().map { it.url }).containsExactly("https://after.example/")
  }
}
