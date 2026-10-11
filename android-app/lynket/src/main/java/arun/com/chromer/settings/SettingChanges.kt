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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import arun.com.chromer.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Calls [block] with the name of each setting that changes while this owner is started. It
 * replaces `OnSharedPreferenceChangeListener`, which a `PreferenceDataStore` never calls.
 */
fun LifecycleOwner.onSettingChanges(
  repository: UserPreferencesRepository,
  block: (String) -> Unit,
) {
  lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
      var previous: Map<String, Any>? = null
      repository.snapshot.filterNotNull().collect { stored ->
        val now = stored.asMap().mapKeys { it.key.name }
        val before = previous
        previous = now
        if (before != null) {
          (before.keys + now.keys).filter { before[it] != now[it] }.forEach(block)
        }
      }
    }
  }
}
