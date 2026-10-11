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

package arun.com.chromer.settings

import android.content.Context
import androidx.preference.PreferenceDataStore
import arun.com.chromer.data.preferences.SettingsSchema
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.readBoolean
import arun.com.chromer.data.preferences.readInt
import arun.com.chromer.data.preferences.readString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Storage of the legacy preference screens: they read and write the settings DataStore through
 * this bridge until the screens are rebuilt in Compose.
 *
 * - List settings are text to a `ListPreference` and ints in the store.
 * - A key with no stored value answers the schema default, not the widget's own default, so a
 *   screen shows what every reader of the setting uses.
 */
class SettingsPreferenceDataStore(
  context: Context,
  private val repository: UserPreferencesRepository,
) : PreferenceDataStore() {
  private val context = context.applicationContext

  private fun default(key: String, widgetDefault: Any?): Any? =
    SettingsSchema[key]?.default(context) ?: widgetDefault

  override fun getString(key: String, defValue: String?): String? {
    val schema = SettingsSchema[key]
    val stored = repository.current()
    if (schema?.legacyText == true) {
      return stored.readInt(key, schema.default(context) as Int).toString()
    }
    return stored.readString(key, default(key, defValue) as? String)
  }

  override fun putString(key: String, value: String?) {
    if (SettingsSchema[key]?.legacyText == true) {
      val number = value?.trim()?.toIntOrNull()
      if (number == null) {
        Timber.e("Settings screen: %s value %s is not a number; not stored", key, value)
        return
      }
      write(key, number)
    } else {
      write(key, value)
    }
  }

  override fun getBoolean(key: String, defValue: Boolean): Boolean =
    repository.current().readBoolean(key, default(key, defValue) as? Boolean ?: defValue)

  override fun putBoolean(key: String, value: Boolean) = write(key, value)

  override fun getInt(key: String, defValue: Int): Int =
    repository.current().readInt(key, default(key, defValue) as? Int ?: defValue)

  override fun putInt(key: String, value: Int) = write(key, value)

  private fun write(key: String, value: Any?) {
    runBlocking(Dispatchers.IO) { repository.setValue(key, value) }
  }
}
