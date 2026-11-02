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

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import arun.com.chromer.ui.settings.BrowsingMode
import arun.com.chromer.ui.settings.SettingsKeyTable.NATIVE_BUBBLES
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_ENABLED

/**
 * Stores one browsing mode when the settings hold both web heads and native bubbles on, as a
 * settings file copied in from elsewhere or written by an older release can. The mode kept is the
 * one the settings screens show ([BrowsingMode.resolve]: web heads). Every other value, and every
 * set that already names one mode, is left as it is.
 *
 * It runs each time the settings store starts, not once, so a file that arrives later is
 * repaired too.
 */
object BrowsingModeMigration : DataMigration<Preferences> {

  private fun webHeads(data: Preferences) = data.readBoolean(WEB_HEAD_ENABLED, false)

  private fun nativeBubbles(data: Preferences) = data.readBoolean(NATIVE_BUBBLES, false)

  override suspend fun shouldMigrate(currentData: Preferences): Boolean =
    webHeads(currentData) && nativeBubbles(currentData)

  override suspend fun migrate(currentData: Preferences): Preferences {
    val mode = BrowsingMode.resolve(webHeads(currentData), nativeBubbles(currentData))
    return currentData.toMutablePreferences().apply {
      this[booleanPreferencesKey(WEB_HEAD_ENABLED)] = mode == BrowsingMode.WebHeads
      this[booleanPreferencesKey(NATIVE_BUBBLES)] = mode == BrowsingMode.NativeBubbles
    }.toPreferences()
  }

  override suspend fun cleanUp() {
    // Nothing to remove: the repair only rewrites the two switches.
  }
}
