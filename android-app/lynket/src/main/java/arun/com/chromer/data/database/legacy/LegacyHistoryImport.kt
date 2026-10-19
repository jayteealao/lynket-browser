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

package arun.com.chromer.data.database.legacy

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import arun.com.chromer.data.database.ChromerDatabase
import arun.com.chromer.data.database.entity.WebsiteEntity
import arun.com.chromer.data.database.entity.toEntity
import arun.com.chromer.data.website.model.Website
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings the history file of release 2.1.3 and earlier (`databases/History`) into the Room
 * database, once.
 *
 * The rows are merged by URL ([arun.com.chromer.data.database.dao.WebsiteDao.mergeLegacyRows]).
 * Only after every legacy URL is found in Room is the old file moved to `files/history-migration/`
 * with a report; nothing is deleted. When the check fails, the file stays and the next open tries
 * again (the merge takes maximums, so a repeat changes nothing). A corrupt file is moved aside as
 * `History-unreadable-<time>` so it is not retried on every start. Any other read failure (a lock,
 * a full disk, a missing column) leaves the file in place for the next start, and a single row
 * that cannot be read is skipped instead of failing the whole file.
 */
@Singleton
class LegacyHistoryImport
@Inject
constructor(@ApplicationContext private val context: Context) {

  suspend fun runOnce(db: ChromerDatabase) {
    val legacy = context.getDatabasePath(HistoryTable.TABLE_NAME)
    if (!legacy.exists()) return
    val folder = File(context.filesDir, MIGRATION_FOLDER).apply { mkdirs() }
    val now = System.currentTimeMillis()

    val rows = try {
      readRows(legacy)
    } catch (e: Exception) {
      if (!isCorrupt(e)) {
        Timber.e(e, "Old history file could not be read now; it stays for the next start")
        return
      }
      Timber.e(e, "Old history file could not be read; it is kept as History-unreadable-%d", now)
      moveWithJournal(legacy, File(folder, "History-unreadable-$now"))
      writeReport(folder, legacyRows = -1, distinct = -1, merged = 0, inserted = 0, result = "unreadable")
      return
    }

    val dao = db.websiteDao()
    val merged = dao.mergeLegacyRows(rows)
    val distinct = rows.map { it.url }.distinct()
    val missing = distinct.count { !dao.exists(it) }
    if (missing > 0) {
      Timber.e("Old history import incomplete: %d of %d URLs missing; the file stays for the next try", missing, distinct.size)
      writeReport(folder, rows.size, distinct.size, merged, rows.size - merged, result = "incomplete")
      return
    }
    moveWithJournal(legacy, File(folder, "History-$now"))
    writeReport(folder, rows.size, distinct.size, merged, rows.size - merged, result = "ok")
    Timber.i("Old history imported: %d rows, %d URLs", rows.size, distinct.size)
  }

  private fun readRows(file: File): List<WebsiteEntity> {
    // Read-write without create: a read-only open fails on a hot journal. The handler only logs,
    // because the platform default handler deletes a corrupt file.
    val handler = DatabaseErrorHandler { Timber.e("Old history file reported corrupt: %s", it.path) }
    val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE, handler)
    return database.use { db ->
      db.rawQuery("SELECT * FROM ${HistoryTable.TABLE_NAME}", null).use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            try {
              add(Website.fromCursor(cursor).toEntity())
            } catch (e: Exception) {
              if (e is SQLiteException) throw e
              Timber.e(e, "One old history row could not be read; it is skipped")
            }
          }
        }
      }
    }
  }

  private fun isCorrupt(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.take(8).any { cause ->
      cause is SQLiteDatabaseCorruptException ||
        (cause is SQLiteException && cause.message.orEmpty().let {
          it.contains("not a database", ignoreCase = true) || it.contains("malformed", ignoreCase = true)
        })
    }

  /** Moves [from] and its journal; a failed rename falls back to copy, then delete after the copy checks out. */
  private fun moveWithJournal(from: File, to: File) {
    moveFile(from, to)
    val journal = File(from.path + "-journal")
    if (journal.exists()) moveFile(journal, File(to.path + "-journal"))
  }

  private fun writeReport(folder: File, legacyRows: Int, distinct: Int, merged: Int, inserted: Int, result: String) {
    File(folder, REPORT).writeText(
      "legacy_rows\tdistinct_urls\tmerged_into_existing\tinserted\tresult\n" +
        "$legacyRows\t$distinct\t$merged\t$inserted\t$result\n"
    )
  }

  companion object {
    const val MIGRATION_FOLDER = "history-migration"
    const val REPORT = "report.tsv"

    /** Rename, or copy then delete the original only when the copy has the same length. */
    fun moveFile(from: File, to: File) {
      to.parentFile?.mkdirs()
      if (from.renameTo(to)) return
      from.copyTo(to, overwrite = true)
      if (to.length() == from.length()) from.delete()
    }
  }
}
