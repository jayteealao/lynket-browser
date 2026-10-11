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

package arun.com.chromer.data.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Moves the settings that earlier releases kept in the default SharedPreferences file into
 * DataStore, once.
 *
 * - The old file is copied byte for byte before anything is read; the old file is never changed
 *   or deleted.
 * - The six list settings that release 2.1.3 stored as text become ints; every other value keeps
 *   its type.
 * - A value that does not convert is logged with its key, is not written under its key, and its
 *   raw text is kept under [SettingsSchema.UNCONVERTED_PREFIX] plus the key name.
 * - A value already in DataStore wins over the old file; one stored with the wrong type (left by
 *   the earlier built-in migration) is converted in place.
 */
class LegacySettingsMigration(
  private val context: Context,
  private val clock: () -> Long = System::currentTimeMillis,
) : DataMigration<Preferences> {

  private val prefsName get() = "${context.packageName}_preferences"

  /** The file the default SharedPreferences of this app live in. */
  val oldFile: File get() = File(context.dataDir, "shared_prefs/$prefsName.xml")

  val migrationDir: File get() = File(context.filesDir, "settings-migration")

  override suspend fun shouldMigrate(currentData: Preferences): Boolean =
    (currentData.raw(SettingsSchema.MIGRATION_VERSION.name) as? Int ?: 0) < VERSION

  override suspend fun migrate(currentData: Preferences): Preferences {
    val out = currentData.toMutablePreferences()
    val stored = currentData.asMap().mapKeys { it.key.name }
    val report = linkedMapOf<String, String>()

    if (oldFile.exists()) {
      backUpOldFile()
      // The platform reader of the same file keeps every value with the type it was written with.
      val old = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).all
      for ((name, raw) in old) {
        if (raw == null) continue
        if (name in stored) {
          report[name] = "kept"
          continue
        }
        val key = SettingsSchema[name]
        if (key == null) {
          out[keyFor(name, raw)] = raw
          report[name] = "copied"
          continue
        }
        val value = key.convert(raw)
        if (value == null) {
          quarantine(out, name, raw)
          report[name] = "failed"
        } else {
          out[keyFor(name, value)] = value
          report[name] = if (value == raw) "copied" else "converted"
        }
      }
    }

    // Repair values stored with the wrong type before this migration existed.
    for (key in SettingsSchema.keys) {
      val value = stored[key.name] ?: continue
      if (key.hasType(value)) continue
      val converted = key.convert(value)
      out.remove(stringPreferencesKey(key.name))
      if (converted == null) {
        quarantine(out, key.name, value)
        report[key.name] = "failed"
      } else {
        out[keyFor(key.name, converted)] = converted
        report[key.name] = "repaired"
      }
    }

    out[SettingsSchema.MIGRATION_VERSION] = VERSION
    writeReport(report)
    return out.toPreferences()
  }

  override suspend fun cleanUp() {
    // Nothing is deleted: the old file stays as the source of truth for any later repair.
  }

  private fun backUpOldFile() {
    migrationDir.mkdirs()
    val backup = File(migrationDir, "$prefsName-${clock()}.xml")
    // A failed copy throws; DataStore then writes nothing and runs the migration again on the
    // next read.
    oldFile.copyTo(backup, overwrite = true)
    if (backup.length() != oldFile.length()) {
      throw IOException("Settings backup ${backup.path} is incomplete")
    }
  }

  private fun quarantine(out: MutablePreferences, name: String, raw: Any) {
    Timber.e("Settings migration: key %s value %s not converted", name, raw)
    out[stringPreferencesKey(SettingsSchema.UNCONVERTED_PREFIX + name)] = raw.toString()
  }

  private fun writeReport(report: Map<String, String>) {
    runCatching {
      migrationDir.mkdirs()
      File(migrationDir, REPORT).writeText(
        buildString {
          append("key\tresult\n")
          report.forEach { (key, result) -> append(key).append('\t').append(result).append('\n') }
        },
      )
    }.onFailure { Timber.e(it, "Settings migration: report not written") }
  }

  companion object {
    const val VERSION = 1
    const val REPORT = "report.tsv"
  }
}
