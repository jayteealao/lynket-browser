/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.settings

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import arun.com.chromer.search.provider.SearchProviders
import arun.com.chromer.settings.Preferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val SEARCH_ENGINE_PREFERENCE = "search_engine_preference"
const val NATIVE_BUBBLES_PREFERENCE = "native_bubbles_preference"

@Singleton
class RxPreferences
@Inject
constructor(application: Application) {

  private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

  val customTabProviderPref: PreferenceItem<String> by lazy {
    PreferenceItem(prefs, Preferences.PREFERRED_CUSTOM_TAB_PACKAGE, "")
  }

  val incognitoPref: PreferenceItem<Boolean> by lazy {
    PreferenceItem(prefs, Preferences.FULL_INCOGNITO_MODE, false)
  }

  val webviewPref: PreferenceItem<Boolean> by lazy {
    PreferenceItem(prefs, Preferences.USE_WEBVIEW_PREF, false)
  }

  val searchEngine: PreferenceItem<String> by lazy {
    PreferenceItem(prefs, SEARCH_ENGINE_PREFERENCE, SearchProviders.GOOGLE)
  }

  val nativeBubbles: PreferenceItem<Boolean> by lazy {
    PreferenceItem(prefs, NATIVE_BUBBLES_PREFERENCE, false)
  }
}

/**
 * Wrapper class for a preference item that provides Flow-based observation
 */
class PreferenceItem<T>(
  private val prefs: SharedPreferences,
  private val key: String,
  private val defaultValue: T
) {

  /**
   * Get the current value of the preference
   */
  @Suppress("UNCHECKED_CAST")
  fun get(): T = when (defaultValue) {
    is Boolean -> prefs.getBoolean(key, defaultValue) as T
    is String -> prefs.getString(key, defaultValue) as T
    is Int -> prefs.getInt(key, defaultValue) as T
    is Long -> prefs.getLong(key, defaultValue) as T
    is Float -> prefs.getFloat(key, defaultValue) as T
    else -> throw IllegalArgumentException("Unsupported preference type")
  }

  /**
   * Set the value of the preference
   */
  fun set(value: T) {
    with(prefs.edit()) {
      when (value) {
        is Boolean -> putBoolean(key, value)
        is String -> putString(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        else -> throw IllegalArgumentException("Unsupported preference type")
      }
      apply()
    }
  }

  /**
   * Observe changes to this preference as a Flow
   */
  fun observe(): Flow<T> = callbackFlow {
    // Emit current value first
    trySend(get())

    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
      if (changedKey == key) {
        trySend(get())
      }
    }

    prefs.registerOnSharedPreferenceChangeListener(listener)

    awaitClose {
      prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
  }.distinctUntilChanged()
}
