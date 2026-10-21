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
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import arun.com.chromer.data.database.dao.WebsiteDao
import arun.com.chromer.data.database.legacy.HistoryTable
import arun.com.chromer.data.database.legacy.LegacyHistoryImport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the history database once, on first use, off the main thread, and survives a bad file.
 *
 * Before the first open the file header is checked; then the database is opened and one count
 * query forces Room to open, migrate and validate it. When that fails because the file is corrupt
 * or its tables do not match the schema, the error is logged, the database files are moved to
 * `files/history-broken/<time>/` (kept, not deleted) and a fresh database opens, so history starts
 * empty and the app keeps working. Any other failure (a full disk, a lock, an I/O error, the caller
 * going away) leaves the file where it is and is rethrown, so the next use opens it again. The platform's default
 * error handler would delete a corrupt file without a trace (`AndroidSQLiteDriver.open` calls
 * `SQLiteDatabase.openOrCreateDatabase(fileName, null)`; source: sqlite-framework-android 2.7.1
 * sources jar, `androidx/sqlite/driver/AndroidSQLiteDriver.android.kt`), which is why the header
 * is checked first. After a good open, the history file of release 2.1.3 is brought in once.
 */
@Singleton
class HistoryDatabase
internal constructor(
  private val context: Context,
  private val legacyImport: LegacyHistoryImport?,
  private val open: () -> ChromerDatabase
) {

  @Inject
  constructor(
    @ApplicationContext context: Context,
    legacyImport: LegacyHistoryImport
  ) : this(context, legacyImport, { ChromerDatabase.create(context) })

  private val mutex = Mutex()

  @Volatile
  private var database: ChromerDatabase? = null

  suspend fun get(): ChromerDatabase = database ?: mutex.withLock {
    database ?: withContext(Dispatchers.IO) { openGuarded() }.also { database = it }
  }

  suspend fun dao(): WebsiteDao = get().websiteDao()

  private suspend fun openGuarded(): ChromerDatabase {
    val file = context.getDatabasePath(ChromerDatabase.DATABASE_NAME)
    if (file.exists() && file.length() > 0 && !hasSqliteHeader(file)) {
      Timber.e("History database is not an SQLite file; history starts empty")
      quarantine(file)
    }
    val db = try {
      openAndCheck()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (!isBrokenFile(e)) {
        Timber.e(e, "History database could not be opened now; it stays in place for the next try")
        throw e
      }
      Timber.e(e, "History database could not be opened; history starts empty")
      quarantine(file)
      try {
        openAndCheck()
      } catch (again: Exception) {
        Timber.e(again, "A fresh history database could not be opened either")
        throw again
      }
    }
    legacyImport?.let { import ->
      try {
        import.runOnce(db)
      } catch (e: CancellationException) {
        // The caller is gone and will not keep this instance; close it so the next open is the only one.
        runCatching { db.close() }
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Old history import failed")
      }
    }
    return db
  }

  private suspend fun openAndCheck(): ChromerDatabase {
    val db = open()
    try {
      db.websiteDao().getCount()
    } catch (e: Exception) {
      runCatching { db.close() }
      throw e
    }
    return db
  }

  /**
   * Deletes the copies of history this class and [LegacyHistoryImport] kept aside: the moved
   * history file of release 2.1.3 and any database moved aside as broken. Called when the person
   * deletes all history, so no copy of it stays on the device.
   */
  fun deleteKeptCopies() {
    listOf(BROKEN_FOLDER, LegacyHistoryImport.MIGRATION_FOLDER)
      .map { File(context.filesDir, it) }
      .filter { it.exists() }
      .forEach { folder ->
        if (!folder.deleteRecursively()) Timber.e("Could not delete kept history copies in %s", folder.path)
      }
    // An old history file that has not been brought in yet would otherwise come back on the next start.
    val legacy = context.getDatabasePath(HistoryTable.TABLE_NAME)
    listOf(legacy, File(legacy.path + "-journal")).filter { it.exists() }.forEach { it.delete() }
  }

  /**
   * True when [e] says the file is corrupt or its tables do not match the schema. Room reports a
   * schema problem as an [IllegalStateException] with one of [ROOM_SCHEMA_ERRORS]; any other
   * [IllegalStateException] (a closed pool, for example) passes, and the file stays.
   */
  private fun isBrokenFile(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.take(8).any { cause ->
      cause is SQLiteDatabaseCorruptException ||
        (cause is IllegalStateException && cause.message.orEmpty().let { message ->
          ROOM_SCHEMA_ERRORS.any { message.contains(it, ignoreCase = true) }
        }) ||
        (cause is SQLiteException && cause.message.orEmpty().let {
          it.contains("not a database", ignoreCase = true) || it.contains("malformed", ignoreCase = true)
        })
    }

  private fun hasSqliteHeader(file: File): Boolean {
    val header = ByteArray(SQLITE_HEADER.size)
    val read = file.inputStream().use { it.read(header) }
    return read == header.size && header.contentEquals(SQLITE_HEADER)
  }

  /** Moves the database and its side files to `files/history-broken/<time>/`. */
  private fun quarantine(file: File) {
    val folder = File(context.filesDir, "$BROKEN_FOLDER/${System.currentTimeMillis()}")
    listOf("", "-wal", "-shm", "-journal")
      .map { File(file.path + it) }
      .filter { it.exists() }
      .forEach { LegacyHistoryImport.moveFile(it, File(folder, it.name)) }
    Timber.e("History database files kept in %s", folder.path)
  }

  companion object {
    const val BROKEN_FOLDER = "history-broken"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** How Room 3 words a missing migration and a schema that does not match (RoomConnectionManager). */
    internal val ROOM_SCHEMA_ERRORS = listOf(
      "Migration didn't properly handle",
      "was required but not found",
      "Room cannot verify the data integrity",
      "has an invalid schema",
    )
  }
}
