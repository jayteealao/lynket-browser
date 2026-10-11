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

package arun.com.chromer.data.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import arun.com.chromer.data.history.model.HistoryTable
import java.io.File

/** One row of the history file release 2.1.3 wrote (`CREATED` is stored as text). */
data class LegacyRow(
  val url: String,
  val title: String?,
  val created: Long,
  val visited: Int,
  val bookmarked: Boolean = false
)

/** Writes a history file in the 2.1.3 table format at `databases/History`. */
fun writeLegacyHistory(context: Context, rows: List<LegacyRow>): File {
  val file = context.getDatabasePath(HistoryTable.TABLE_NAME)
  file.parentFile?.mkdirs()
  SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
    db.execSQL(HistoryTable.DATABASE_CREATE)
    db.version = 1
    rows.forEach { row ->
      db.insert(HistoryTable.TABLE_NAME, null, ContentValues().apply {
        put(HistoryTable.COLUMN_URL, row.url)
        put(HistoryTable.COLUMN_TITLE, row.title)
        put(HistoryTable.COLUMN_BOOKMARKED, if (row.bookmarked) 1 else 0)
        put(HistoryTable.COLUMN_CREATED_AT, row.created.toString())
        put(HistoryTable.COLUMN_VISITED, row.visited)
      })
    }
  }
  return file
}

fun migrationFolder(context: Context) = File(context.filesDir, LegacyHistoryImport.MIGRATION_FOLDER)
