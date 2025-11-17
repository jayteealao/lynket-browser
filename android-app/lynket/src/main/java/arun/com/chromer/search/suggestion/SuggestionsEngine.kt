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
package arun.com.chromer.search.suggestion

import android.app.Application
import arun.com.chromer.R
import arun.com.chromer.data.history.HistoryRepository
import arun.com.chromer.search.suggestion.items.SuggestionItem
import arun.com.chromer.search.suggestion.items.SuggestionItem.*
import arun.com.chromer.search.suggestion.items.SuggestionType
import arun.com.chromer.search.suggestion.items.SuggestionType.*
import arun.com.chromer.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class that collates suggestions from multiple sources and publishes them in a single stream.
 */
@Singleton
class SuggestionsEngine
@Inject
constructor(
  private var application: Application,
  private val historyRepository: HistoryRepository
) {
  private val suggestionsDebounce = 200L

  /**
   * Trims and filters empty strings in stream.
   */
  private fun Flow<String>.emptyStringFilter(): Flow<String> = this
    .map { it.trim { query -> query <= ' ' } }
    .filter { it.isNotEmpty() }

  private fun deviceSuggestions(): Flow<List<SuggestionItem>> = flow {
    val copiedText = Utils.getClipBoardText(application) ?: ""
    if (copiedText.isEmpty()) {
      emit(emptyList())
    } else {
      val fullCopiedText = CopySuggestionItem(
        copiedText.trim(),
        application.getString(R.string.text_you_copied)
      )
      val extractedLinks = Utils.findURLs(copiedText)
        .map {
          CopySuggestionItem(it, application.getString(R.string.link_you_copied))
        }.toMutableList()
      extractedLinks.apply {
        add(fullCopiedText)
      }
      emit(extractedLinks.distinctBy { it.title.trim() })
    }
  }.flowOn(Dispatchers.Main)

  /**
   * Converts a stream of strings into stream of list of suggestions items collated from device's
   * clipboard, history and google suggestions.
   */
  fun Flow<String>.suggestionsTransformer(): Flow<Pair<SuggestionType, List<SuggestionItem>>> =
    this.emptyStringFilter()
      .flatMapLatest { query ->
        merge(
          deviceSuggestions().map { COPY to it },
          flow { emit(query) }
            .googleTransformer()
            .map { GOOGLE to it }
            .flowOn(Dispatchers.IO),
          flow { emit(query) }
            .historyTransformer()
            .map { HISTORY to it }
        )
      }
      .flowOn(Dispatchers.Default)

  /**
   * A function selector that transforms a source Flow emitted from a suggestionsTransformer
   * such that each [SuggestionType] and its [List] of [SuggestionItem]s are unique.
   */
  fun Flow<Pair<SuggestionType, List<SuggestionItem>>>.distinctSuggestionsPublish(): Flow<Pair<SuggestionType, List<SuggestionItem>>> =
    merge(
      this.filter { it.first == COPY }.distinctUntilChanged(),
      this.filter { it.first == GOOGLE }.distinctUntilChanged(),
      this.filter { it.first == HISTORY }.distinctUntilChanged()
    )

  /**
   * Fetches suggestions from Google and converts it to GoogleSuggestionItem
   */
  private fun Flow<String>.googleTransformer(): Flow<List<SuggestionItem>> {
    return if (!Utils.isOnline(application)) {
      flowOf(emptyList())
    } else {
      this.flatMapLatest { query ->
        flow {
          val suggestions = GoogleSuggestionsApi.getSuggestions(query, 5)
          emit(suggestions.map { GoogleSuggestionItem(it) })
        }.catch { e ->
          Timber.e(e)
          emit(emptyList())
        }
      }
    }
  }

  /**
   * Fetches matching items from History database and converts them to list of suggestions.
   */
  private fun Flow<String>.historyTransformer(): Flow<List<SuggestionItem>> =
    this.debounce(suggestionsDebounce)
      .flatMapLatest { query ->
        flow { emit(historyRepository.search(query)) }
      }
      .map { websites ->
        websites.asSequence()
          .map { website ->
            HistorySuggestionItem(
              website,
              website.safeLabel(),
              website.url
            )
          }.take(4).toList()
      }
      .catch { emit(emptyList()) }
}
