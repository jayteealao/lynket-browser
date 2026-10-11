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

import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.readBoolean
import arun.com.chromer.data.preferences.readInt
import arun.com.chromer.data.preferences.readString
import arun.com.chromer.search.provider.SearchProviders
import androidx.datastore.preferences.core.Preferences as StoredPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

const val SEARCH_ENGINE_PREFERENCE = "search_engine_preference"
const val NATIVE_BUBBLES_PREFERENCE = "native_bubbles_preference"

@Singleton
class RxPreferences
@Inject
constructor(repository: UserPreferencesRepository) {

  val customTabProviderPref: PreferenceItem<String> by lazy {
    PreferenceItem(repository, Preferences.PREFERRED_CUSTOM_TAB_PACKAGE, "")
  }

  val incognitoPref: PreferenceItem<Boolean> by lazy {
    PreferenceItem(repository, Preferences.FULL_INCOGNITO_MODE, false)
  }

  val webviewPref: PreferenceItem<Boolean> by lazy {
    PreferenceItem(repository, Preferences.USE_WEBVIEW_PREF, false)
  }

  val searchEngine: PreferenceItem<String> by lazy {
    PreferenceItem(repository, SEARCH_ENGINE_PREFERENCE, SearchProviders.GOOGLE)
  }

  val nativeBubbles: PreferenceItem<Boolean> by lazy {
    PreferenceItem(repository, NATIVE_BUBBLES_PREFERENCE, false)
  }
}

/**
 * One setting read, written and observed through the settings DataStore.
 */
class PreferenceItem<T : Any>(
  private val repository: UserPreferencesRepository,
  private val key: String,
  private val defaultValue: T
) {

  /** The current value. */
  fun get(): T = read(repository.current())

  /** Writes the value; the write is finished when this returns. */
  fun set(value: T) {
    runBlocking(Dispatchers.IO) { repository.setValue(key, value) }
  }

  /** The value now and after each change. */
  fun observe(): Flow<T> = repository.snapshot
    .filterNotNull()
    .map { read(it) }
    .distinctUntilChanged()

  // sdlc-debt: unchecked cast to T; each branch reads the type of defaultValue, which is T, and only
  // Boolean, String and Int items work (ceiling); upgrade path: typed repository flows replace
  // PreferenceItem when the legacy settings screens are rebuilt.
  @Suppress("UNCHECKED_CAST")
  private fun read(stored: StoredPreferences): T = when (defaultValue) {
    is Boolean -> stored.readBoolean(key, defaultValue) as T
    is String -> (stored.readString(key, defaultValue) ?: defaultValue) as T
    is Int -> stored.readInt(key, defaultValue) as T
    else -> throw IllegalArgumentException("Unsupported preference type")
  }
}
