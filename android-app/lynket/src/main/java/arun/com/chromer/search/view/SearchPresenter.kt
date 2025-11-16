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

// Phase 8: Converted from RxJava to Kotlin Coroutines
package arun.com.chromer.search.view

import arun.com.chromer.di.view.Detaches
import arun.com.chromer.search.provider.SearchProvider
import arun.com.chromer.search.provider.SearchProviders
import arun.com.chromer.search.suggestion.SuggestionsEngine
import arun.com.chromer.search.suggestion.items.SuggestionItem
import arun.com.chromer.search.suggestion.items.SuggestionType
import arun.com.chromer.settings.RxPreferences
import dev.arunkumar.android.dagger.view.PerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject

data class SuggestionResult(
  val query: String,
  val suggestionType: SuggestionType,
  val suggestions: List<SuggestionItem>
)

@PerView
class SearchPresenter
@Inject
constructor(
  private val suggestionsEngine: SuggestionsEngine,
  @param:Detaches
  private val detaches: Flow<Unit>,
  searchProviders: SearchProviders,
  private val rxPreferences: RxPreferences
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val suggestionsFlow = MutableSharedFlow<SuggestionResult>(extraBufferCapacity = 1)
  val suggestions: Flow<SuggestionResult> = suggestionsFlow.asSharedFlow()

  init {
    // Cancel scope when view detaches
    scope.launch {
      detaches.first()
      scope.cancel()
    }
  }

  fun registerSearch(queryFlow: Flow<String>) {
    scope.launch {
      queryFlow
        .debounce(200)
        .onEach { Timber.d(it) }
        .flatMapLatest { query ->
          with(suggestionsEngine) {
            flow { emit(query) }
              .suggestionsTransformer()
              .distinctSuggestionsPublish()
              .map { SuggestionResult(query, it.first, it.second) }
          }
        }
        .flowOn(Dispatchers.Default)
        .collect { suggestionsFlow.tryEmit(it) }
    }
  }

  fun registerSearchProviderClicks(searchProviderClicks: Flow<SearchProvider>) {
    scope.launch {
      searchProviderClicks
        .map { it.name }
        .flowOn(Dispatchers.Default)
        .collect { rxPreferences.searchEngine.set(it) }
    }
  }

  val searchEngines: Flow<List<SearchProvider>> = flowOf(searchProviders.availableProviders)
    .shareIn(scope, SharingStarted.Lazily, replay = 1)

  val selectedSearchProvider: Flow<SearchProvider> = searchProviders.selectedProvider

  suspend fun getSearchUrl(searchUrl: String): String {
    return selectedSearchProvider.first().getSearchUrl(searchUrl)
  }
}
