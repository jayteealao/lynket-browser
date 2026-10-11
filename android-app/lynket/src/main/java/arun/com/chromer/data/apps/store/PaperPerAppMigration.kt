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

package arun.com.chromer.data.apps.store

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import arun.com.chromer.data.common.App
import arun.com.chromer.shared.Constants
import io.paperdb.Paper
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Reads, once, the per-app rules that release 2.1.3 saved with PaperDB (one `<package>.pt`
 * record per app in the book `files/APPS/`) into the per-app rule DataStore.
 *
 * The book is copied to `files/per-app-migration/APPS-<time>/` first and PaperDB reads the copy,
 * because PaperDB's read replaces a record with its `.bak` file when one exists; the original
 * book is never opened by PaperDB and never deleted. A record that does not read is logged with
 * its key and skipped.
 */
class PaperPerAppMigration(
  private val context: Context,
  private val clock: () -> Long = System::currentTimeMillis,
) : DataMigration<Preferences> {

  private val book: File get() = File(context.filesDir, BOOK)

  val migrationDir: File get() = File(context.filesDir, "per-app-migration")

  override suspend fun shouldMigrate(currentData: Preferences): Boolean =
    currentData[MIGRATION_VERSION] == null && records(book).isNotEmpty()

  override suspend fun migrate(currentData: Preferences): Preferences {
    val copy = File(migrationDir, "$BOOK-${clock()}")
    // A failed copy throws; DataStore then writes nothing and runs the migration again on the
    // next read.
    if (!book.copyRecursively(copy, overwrite = true)) {
      throw IOException("Per-app backup ${copy.path} is incomplete")
    }
    Paper.init(context)
    val paperBook = Paper.bookOn(migrationDir.path, copy.name)

    val out = currentData.toMutablePreferences()
    val report = StringBuilder("key\tresult\n")
    for (key in records(copy).sorted()) {
      val app = runCatching { paperBook.read<Any>(key) as? App ?: error("not a per-app record") }
        .onFailure { Timber.e(it, "Per-app migration: entry %s not read", key) }
        .getOrNull()
      if (app == null) {
        report.append(key).append("\tfailed\n")
        continue
      }
      val pkg = app.packageName.ifEmpty { key }
      if (app.blackListed) out[AppDiskStore.blacklistedKey(pkg)] = true
      if (app.incognito) out[AppDiskStore.incognitoKey(pkg)] = true
      if (app.color != Constants.NO_COLOR) out[AppDiskStore.colorKey(pkg)] = app.color
      report.append(key)
        .append("\tmigrated blacklisted=").append(app.blackListed)
        .append(" incognito=").append(app.incognito)
        .append(" color=").append(app.color).append('\n')
    }
    out[MIGRATION_VERSION] = 1
    runCatching { File(migrationDir, REPORT).writeText(report.toString()) }
      .onFailure { Timber.e(it, "Per-app migration: report not written") }
    return out.toPreferences()
  }

  override suspend fun cleanUp() {
    // Nothing is deleted: the 2.1.3 book stays as it was.
  }

  /** Keys of the PaperDB records in [dir]. */
  private fun records(dir: File): List<String> =
    dir.listFiles { file -> file.isFile && file.name.endsWith(RECORD) }
      ?.map { it.name.removeSuffix(RECORD) }
      .orEmpty()

  companion object {
    const val BOOK = "APPS"
    const val REPORT = "report.tsv"
    private const val RECORD = ".pt"
    val MIGRATION_VERSION = intPreferencesKey("per_app_migration_version")
  }
}
