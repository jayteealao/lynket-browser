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

package arun.com.chromer.shortcuts

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** The shortcut dialog: loading, then the site with its icon (a placeholder when the favicon fails). */
sealed interface ShortcutState {
  data object Loading : ShortcutState
  data class Ready(val website: Website, val icon: Bitmap) : ShortcutState
}

/** Loads the site and its icon for the home screen shortcut, and keeps the typed name. */
@HiltViewModel
class ShortcutCreatorViewModel @Inject constructor(
  private val savedState: SavedStateHandle,
  private val websiteRepository: WebsiteRepository,
) : ViewModel() {

  /** Where the icon is loaded; tests replace it. */
  internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

  private val _state = MutableStateFlow<ShortcutState>(ShortcutState.Loading)
  val state: StateFlow<ShortcutState> = _state.asStateFlow()

  /** The name in the field; null until the person or the loaded site sets it. */
  val name: StateFlow<String?> = savedState.getStateFlow(NAME, null)

  private var started = false

  fun start(url: String) {
    if (started) return
    started = true
    viewModelScope.launch {
      val website = runCatching { websiteRepository.getWebsite(url).first() }
        .onFailure { Timber.e(it, "Shortcut site lookup failed for %s", url) }
        .getOrNull() ?: Website(url)
      val icon = withContext(ioDispatcher) { websiteRepository.getWebsiteIconWithPlaceholderAndColor(website).first }
      if (savedState.get<String>(NAME) == null) savedState[NAME] = website.safeLabel()
      _state.value = ShortcutState.Ready(website, icon)
    }
  }

  fun onNameChange(text: String) {
    savedState[NAME] = text
  }

  /** Create is allowed once the icon is loaded and the name is not blank. */
  fun canCreate(state: ShortcutState, name: String?): Boolean =
    state is ShortcutState.Ready && !name.isNullOrBlank()

  private companion object {
    const val NAME = "name"
  }
}
