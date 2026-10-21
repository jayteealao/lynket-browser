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

package arun.com.chromer.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import arun.com.chromer.data.history.ModernHistoryRepository
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.history.HistoryListItem
import arun.com.chromer.history.withDayHeaders
import arun.com.chromer.util.events.Event
import arun.com.chromer.util.events.EventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Dialogs of the History screen. */
enum class HistoryDialog { None, ClearConfirm, IncognitoExplanation }

/** State of the History screen apart from the paged rows. */
data class HistoryUiState(
  val historyEnabled: Boolean = true,
  val incognito: Boolean = false,
  val providerPackage: String? = null,
  val query: String = "",
  val searchOpen: Boolean = false,
  val dialog: HistoryDialog = HistoryDialog.None,
)

/** One-shot messages: a row was removed (offer undo), or all rows were deleted. */
sealed interface HistoryEvent {
  data class Deleted(val website: Website) : HistoryEvent
  data class Cleared(val count: Int) : HistoryEvent
}

/**
 * History: the History and Incognito browsing switches (turning one on turns the other off, as
 * the old screen did), the paged rows with day headers, search over every row, remove with undo,
 * bookmark, and delete all after a confirmation.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ModernHistoryViewModel @Inject constructor(
  private val historyRepository: ModernHistoryRepository,
  private val preferences: Preferences,
  private val preferencesRepository: UserPreferencesRepository,
  private val eventBus: EventBus,
  private val tabsManager: TabsManager,
) : ViewModel() {

  private val _uiState = MutableStateFlow(settingsState(HistoryUiState()))
  val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

  private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
  val events: Flow<HistoryEvent> = _events.receiveAsFlow()

  private val query = MutableStateFlow("")

  /** All rows, or the rows matching the search, newest first, with a header before each day. */
  val items: Flow<PagingData<HistoryListItem>> = query
    .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
    .map { it.trim() }
    .distinctUntilChanged()
    .flatMapLatest { q -> if (q.isEmpty()) historyRepository.getPagedHistory() else historyRepository.getPagedSearch(q) }
    .map { it.withDayHeaders() }
    .cachedIn(viewModelScope)

  private fun settingsState(state: HistoryUiState) = state.copy(
    historyEnabled = !preferences.historyDisabled(),
    incognito = preferences.fullIncognitoMode(),
    providerPackage = preferences.customTabPackage(),
  )

  /** Reads the switches again, for example when the screen comes back from settings. */
  fun syncSettings() = _uiState.update(::settingsState)

  fun setHistoryEnabled(enabled: Boolean) {
    val incognitoWasOn = _uiState.value.incognito
    val changes = buildMap<String, Any?> {
      put(Preferences.INCOGNITO_MODE, !enabled)
      if (enabled && incognitoWasOn) put(Preferences.FULL_INCOGNITO_MODE, false)
    }
    _uiState.update { it.copy(historyEnabled = enabled, incognito = if (enabled) false else it.incognito) }
    write(changes, providerChanged = enabled && incognitoWasOn)
  }

  fun setIncognito(on: Boolean) {
    val changes = buildMap<String, Any?> {
      put(Preferences.FULL_INCOGNITO_MODE, on)
      if (on) put(Preferences.INCOGNITO_MODE, true)
    }
    _uiState.update {
      it.copy(
        incognito = on,
        historyEnabled = if (on) false else it.historyEnabled,
        dialog = if (on) HistoryDialog.IncognitoExplanation else it.dialog,
      )
    }
    write(changes, providerChanged = true)
  }

  /** Writes the switches in one store edit off the main thread, then tells the provider readers. */
  private fun write(changes: Map<String, Any?>, providerChanged: Boolean) {
    viewModelScope.launch {
      runCatching { preferencesRepository.setValues(changes) }
        .onFailure { Timber.e(it, "History switches could not be saved") }
      if (providerChanged) eventBus.tryEmit(Event.SettingsEvent.ProviderChanged)
    }
  }

  fun openSearch() = _uiState.update { it.copy(searchOpen = true) }

  fun closeSearch() {
    query.value = ""
    _uiState.update { it.copy(searchOpen = false, query = "") }
  }

  fun setQuery(text: String) {
    query.value = text
    _uiState.update { it.copy(query = text) }
  }

  /** Opens the row the way a link opens: the browsing mode, AMP and article settings apply. */
  fun open(context: Context, website: Website) = tabsManager.openUrl(context, website)

  fun openAmp(context: Context, website: Website) = tabsManager.openUrl(context, Website.Ampify(website))

  fun delete(website: Website) {
    viewModelScope.launch {
      if (historyRepository.delete(website)) _events.send(HistoryEvent.Deleted(website))
    }
  }

  fun undo(website: Website) {
    viewModelScope.launch { historyRepository.restore(website) }
  }

  fun toggleBookmark(website: Website) {
    viewModelScope.launch { historyRepository.toggleBookmark(website.url) }
  }

  fun requestClearAll() = _uiState.update { it.copy(dialog = HistoryDialog.ClearConfirm) }

  fun dismissDialog() = _uiState.update { it.copy(dialog = HistoryDialog.None) }

  fun confirmClearAll() {
    dismissDialog()
    viewModelScope.launch { _events.send(HistoryEvent.Cleared(historyRepository.deleteAll())) }
  }

  private companion object {
    const val SEARCH_DEBOUNCE_MS = 300L
  }
}
