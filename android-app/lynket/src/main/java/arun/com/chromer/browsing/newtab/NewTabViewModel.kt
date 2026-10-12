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

package arun.com.chromer.browsing.newtab

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.provider.SearchProviders
import arun.com.chromer.search.suggestion.SuggestionsEngine
import arun.com.chromer.search.suggestion.items.SuggestionType
import arun.com.chromer.tabs.TabsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The New tab dialog: the typed text (kept across recreation), suggestions for it as the home
 * search bar lists them, and opening the text as a page or a search.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewTabViewModel @Inject constructor(
  private val savedState: SavedStateHandle,
  private val suggestionsEngine: SuggestionsEngine,
  private val searchProviders: SearchProviders,
  private val tabsManager: TabsManager,
) : ViewModel() {

  val query: StateFlow<String> = savedState.getStateFlow(QUERY, "")

  val suggestions: StateFlow<Suggestions> = query
    .debounce(SUGGESTIONS_DEBOUNCE_MS)
    .flatMapLatest { text ->
      if (text.isBlank()) {
        flowOf(Suggestions(query = text))
      } else {
        with(suggestionsEngine) {
          flowOf(text)
            .suggestionsTransformer()
            .distinctSuggestionsPublish()
            .runningFold(Suggestions(query = text)) { acc, (type, items) ->
              when (type) {
                SuggestionType.COPY -> acc.copy(copy = items)
                SuggestionType.GOOGLE -> acc.copy(google = items)
                SuggestionType.HISTORY -> acc.copy(history = items)
              }
            }
        }
      }
    }
    .catch { e ->
      Timber.e(e, "New tab suggestions failed")
      emit(Suggestions())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Suggestions())

  fun onQueryChange(text: String) {
    savedState[QUERY] = text
  }

  /**
   * Opens [text]: a web address as is, anything else as a search with the chosen engine, through
   * the same launch path the old dialog used (from the app, as a new tab). Calls [onOpened] after.
   */
  fun submit(context: Context, text: String, onOpened: () -> Unit) {
    if (text.isBlank()) return
    viewModelScope.launch {
      val url = runCatching { searchProviders.selectedProvider.first() }
        .getOrDefault(SearchProviders.GOOGLE_SEARCH_PROVIDER)
        .getSearchUrl(text.trim())
      tabsManager.openUrl(context, Website(url), fromApp = true, fromWebHeads = false, fromNewTab = true)
      onOpened()
    }
  }

  private companion object {
    const val QUERY = "query"
    const val SUGGESTIONS_DEBOUNCE_MS = 200L
  }
}
