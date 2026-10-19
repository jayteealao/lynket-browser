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
import android.database.sqlite.SQLiteDiskIOException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** A history file that cannot be opened for a passing reason stays where it is for the next try. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryDatabaseTransientFailureTest {

  private val context: Context = RuntimeEnvironment.getApplication()
  private val dbFile = context.getDatabasePath(ChromerDatabase.DATABASE_NAME)
  private val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(84)

  @Before
  fun writeFile() {
    dbFile.parentFile?.mkdirs()
    dbFile.writeBytes(header)
  }

  private fun brokenFolder() = File(context.filesDir, HistoryDatabase.BROKEN_FOLDER)

  @Test
  fun anInputOutputErrorKeepsTheFileAndIsRethrown() {
    var opens = 0
    val history = HistoryDatabase(context, legacyImport = null) {
      opens++
      throw SQLiteDiskIOException("disk I/O error")
    }

    assertThrows(SQLiteDiskIOException::class.java) { runBlocking { history.get() } }

    assertThat(opens).isEqualTo(1)
    assertThat(dbFile.readBytes()).isEqualTo(header)
    assertThat(brokenFolder().exists()).isFalse()
  }

  @Test
  fun anIllegalStateThatIsNotASchemaErrorKeepsTheFile() {
    var opens = 0
    val history = HistoryDatabase(context, legacyImport = null) {
      opens++
      throw IllegalStateException("Cannot perform this operation because the connection pool has been closed.")
    }

    assertThrows(IllegalStateException::class.java) { runBlocking { history.get() } }

    assertThat(opens).isEqualTo(1)
    assertThat(dbFile.readBytes()).isEqualTo(header)
    assertThat(brokenFolder().exists()).isFalse()
  }

  @Test
  fun aMissingMigrationMovesTheFileAside() {
    var opens = 0
    val history = HistoryDatabase(context, legacyImport = null) {
      opens++
      throw IllegalStateException("A migration from 9 to 3 was required but not found.")
    }

    assertThrows(IllegalStateException::class.java) { runBlocking { history.get() } }

    assertThat(opens).isEqualTo(2)
    assertThat(dbFile.exists()).isFalse()
    assertThat(brokenFolder().walk().any { it.name == ChromerDatabase.DATABASE_NAME }).isTrue()
  }

  @Test
  fun aCancelledOpenKeepsTheFile() {
    val history = HistoryDatabase(context, legacyImport = null) { throw CancellationException("left") }

    assertThrows(CancellationException::class.java) { runBlocking { history.get() } }

    assertThat(dbFile.readBytes()).isEqualTo(header)
    assertThat(brokenFolder().exists()).isFalse()
  }

  @Test
  fun deleteKeptCopiesRemovesEveryKeptCopy() {
    val broken = File(brokenFolder(), "1/${ChromerDatabase.DATABASE_NAME}").apply { parentFile?.mkdirs(); writeText("x") }
    val migrated = File(context.filesDir, "history-migration/History-1").apply { parentFile?.mkdirs(); writeText("x") }
    val legacy = context.getDatabasePath("History").apply { writeText("x") }

    HistoryDatabase(context, legacyImport = null) { error("not opened") }.deleteKeptCopies()

    assertThat(broken.exists()).isFalse()
    assertThat(migrated.exists()).isFalse()
    assertThat(legacy.exists()).isFalse()
    assertThat(dbFile.exists()).isTrue()
  }
}
