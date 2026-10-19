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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import arun.com.chromer.util.blockingWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

/**
 * A hot copy of a Preferences DataStore for callers that read synchronously (tiles, receivers,
 * the link path). Collection starts on [scope] when the object is created; [edit] publishes its
 * result before it returns, so a caller reads its own write at once.
 */
class PreferencesSnapshot(
  private val dataStore: DataStore<Preferences>,
  scope: CoroutineScope,
) {
  private val state = MutableStateFlow<Preferences?>(null)

  /** The latest stored values; null until the first read finishes. */
  val flow: StateFlow<Preferences?> = state.asStateFlow()

  init {
    scope.launch {
      dataStore.data
        .catch { e -> if (e is IOException) emit(readFailed(e)) else throw e }
        .collect { state.value = it }
    }
  }

  /**
   * The latest stored values. Before the first read finishes, waits for it once on the IO
   * dispatcher (on the first start after an update this includes the settings migration).
   */
  fun current(): Preferences = state.value ?: blockingWait("PreferencesSnapshot.current", Dispatchers.IO) { fresh() }

  /** Reads the store itself, bypassing the hot copy. */
  suspend fun fresh(): Preferences =
    try {
      dataStore.data.first()
    } catch (e: IOException) {
      readFailed(e)
    }

  suspend fun edit(block: (MutablePreferences) -> Unit): Preferences =
    dataStore.edit { block(it) }.also { state.value = it }

  private fun readFailed(e: IOException): Preferences {
    Timber.e(e, "Error reading preferences")
    return emptyPreferences()
  }
}
