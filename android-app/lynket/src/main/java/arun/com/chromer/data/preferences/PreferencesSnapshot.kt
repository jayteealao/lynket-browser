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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException

/**
 * A hot copy of a Preferences DataStore for callers that read synchronously (tiles, receivers,
 * the link path). Collection starts on [scope] when the object is created; [edit] publishes its
 * result before it returns, so a caller reads its own write at once. Edits run one at a time, so
 * an older edit never publishes over a newer one.
 */
class PreferencesSnapshot(
  private val dataStore: DataStore<Preferences>,
  scope: CoroutineScope,
) {
  private val state = MutableStateFlow<Preferences?>(null)
  private val editLock = Mutex()

  /** The latest stored values; null until the first read finishes. */
  val flow: StateFlow<Preferences?> = state.asStateFlow()

  init {
    scope.launch {
      // A failed read publishes nothing: the hot copy stays empty (callers read the store, and get
      // the defaults, while it fails) and the collection starts again, so it follows the store
      // once the read works. Publishing the defaults would end the collection for good.
      dataStore.data
        .retryWhen { e, attempt ->
          if (e !is IOException) return@retryWhen false
          Timber.e(e, "Error reading preferences; reading again")
          delay(retryDelayMs(attempt))
          true
        }
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
    editLock.withLock { dataStore.edit { block(it) }.also { state.value = it } }

  private fun readFailed(e: IOException): Preferences {
    Timber.e(e, "Error reading preferences")
    return emptyPreferences()
  }

  private fun retryDelayMs(attempt: Long): Long =
    (FIRST_RETRY_MS shl attempt.coerceAtMost(5).toInt()).coerceAtMost(MAX_RETRY_MS)

  private companion object {
    const val FIRST_RETRY_MS = 1_000L
    const val MAX_RETRY_MS = 30_000L
  }
}
