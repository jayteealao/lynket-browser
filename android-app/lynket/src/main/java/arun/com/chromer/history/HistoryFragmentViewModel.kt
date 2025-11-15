// Phase 8: Converted from RxJava to Kotlin Coroutines
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

package arun.com.chromer.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.history.HistoryRepository
import arun.com.chromer.data.website.model.Website
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Legacy ViewModel for HistoryFragment (XML-based UI).
 *
 * Migrated to Hilt: Uses @HiltViewModel annotation for automatic ViewModel injection.
 * Converted to Kotlin Coroutines and StateFlow.
 *
 * Note: Modern Compose UI uses ModernHistoryViewModel instead.
 */
@HiltViewModel
class HistoryFragmentViewModel
@Inject
constructor(
  private val historyRepository: HistoryRepository
) : ViewModel() {

  private val _loadingState = MutableStateFlow(false)
  val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

  var historyPagedListLiveData = historyRepository.pagedHistory()

  fun loadHistory() {
    // Currently using pagedHistory LiveData, so no additional loading needed
    // This method is kept for compatibility with existing code
  }

  fun deleteAll(onSuccess: (rows: Int) -> Unit) {
    viewModelScope.launch {
      try {
        val rows = historyRepository.deleteAll()
        loadHistory()
        onSuccess(rows)
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }

  fun deleteHistory(website: Website?) {
    viewModelScope.launch {
      try {
        if (website?.url != null) {
          historyRepository.delete(website)
          loadHistory()
        }
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }
}
