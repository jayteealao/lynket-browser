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

package arun.com.chromer.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import arun.com.chromer.data.apps.store.AppDiskStore
import arun.com.chromer.data.apps.store.PaperPerAppMigration
import arun.com.chromer.data.preferences.LegacySettingsMigration
import arun.com.chromer.data.preferences.SettingType
import arun.com.chromer.data.preferences.SettingsSchema
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.raw
import arun.com.chromer.data.preferences.readBoolean
import arun.com.chromer.data.preferences.readInt
import arun.com.chromer.data.preferences.readString
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Debug build only. Writes both settings stores to `files/debug-dump/`:
 * - `settings.tsv`: key, stored type, stored value, value a reader gets (schema keys first, then
 *   every other stored key);
 * - `per-app.tsv`: package, blacklisted, incognito, colour;
 * - `settings-report.tsv` and `per-app-report.tsv`: the migration reports, when they exist.
 *
 * `adb shell am broadcast -n arun.com.chromer.debug/arun.com.chromer.debug.StoreDumpReceiver`,
 * then `adb exec-out run-as arun.com.chromer.debug cat files/debug-dump/<file>`.
 */
@AndroidEntryPoint
class StoreDumpReceiver : BroadcastReceiver() {

  @Inject
  lateinit var repository: UserPreferencesRepository

  @Inject
  lateinit var appDiskStore: AppDiskStore

  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    val app = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        dump(app)
        Timber.tag(TAG).i("done")
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "failed")
      } finally {
        pending.finish()
      }
    }
  }

  private suspend fun dump(context: Context) {
    val out = File(context.filesDir, "debug-dump").apply { mkdirs() }
    val stored = repository.fresh()

    val settings = StringBuilder("key\tstored_type\tstored_value\treader_value\n")
    for (key in SettingsSchema.keys) {
      val raw = stored.raw(key.name)
      val reader: Any? = when (key.type) {
        SettingType.BOOLEAN -> stored.readBoolean(key.name, key.default(context) as Boolean)
        SettingType.INT -> stored.readInt(key.name, key.default(context) as Int)
        SettingType.STRING -> stored.readString(key.name, key.default(context) as String?)
      }
      settings.row(key.name, typeOf(raw), raw, reader)
    }
    stored.asMap().keys.map { it.name }.filter { SettingsSchema[it] == null }.sorted().forEach { name ->
      val raw = stored.raw(name)
      settings.row(name, typeOf(raw), raw, "")
    }
    File(out, "settings.tsv").writeText(settings.toString())

    val perApp = StringBuilder("package\tblacklisted\tincognito\tcolor\n")
    appDiskStore.storedRules().forEach { perApp.row(it.packageName, it.blackListed, it.incognito, it.color) }
    File(out, "per-app.tsv").writeText(perApp.toString())

    copyIfPresent(File(context.filesDir, "settings-migration/${LegacySettingsMigration.REPORT}"), File(out, "settings-report.tsv"))
    copyIfPresent(File(context.filesDir, "per-app-migration/${PaperPerAppMigration.REPORT}"), File(out, "per-app-report.tsv"))
  }

  private fun typeOf(value: Any?): String = when (value) {
    null -> "absent"
    is Boolean -> "boolean"
    is Int -> "int"
    is String -> "string"
    else -> value.javaClass.simpleName.lowercase()
  }

  private fun StringBuilder.row(vararg cells: Any?) {
    append(cells.joinToString("\t") { it?.toString() ?: "null" }).append('\n')
  }

  private fun copyIfPresent(from: File, to: File) {
    if (from.exists()) from.copyTo(to, overwrite = true) else to.delete()
  }

  private companion object {
    const val TAG = "StoreDump"
  }
}
