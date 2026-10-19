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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.File
import kotlin.random.Random

/** A broken history database opens as empty history, with a logged error, no crash and the file kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryDatabaseCorruptionTest {

  private val context: Context = RuntimeEnvironment.getApplication()
  private val dbFile = context.getDatabasePath(ChromerDatabase.DATABASE_NAME)
  private val tree = RecordingTree()
  private val historyDatabase = HistoryDatabase(context, LegacyHistoryImport(context))

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

  private suspend fun assertBrokenFileHandled(original: ByteArray) =
    assertBrokenFileHandled { kept -> assertThat(kept.readBytes()).isEqualTo(original) }

  private suspend fun assertBrokenFileHandled(checkKept: (File) -> Unit) {
    val store = RoomHistoryStore(historyDatabase)
    val history = ModernHistoryRepository(historyDatabase, Dispatchers.Unconfined)

    assertThat(history.getRecents().first()).isEmpty()
    assertThat(tree.errors).isNotEmpty()
    val kept = File(context.filesDir, HistoryDatabase.BROKEN_FOLDER).walk()
      .filter { it.name == ChromerDatabase.DATABASE_NAME }.toList()
    assertThat(kept).hasSize(1)
    checkKept(kept[0])

    store.insert(Website("https://after.example/"))
    assertThat(history.getRecents().first().map { it.url }).containsExactly("https://after.example/")
  }
}
