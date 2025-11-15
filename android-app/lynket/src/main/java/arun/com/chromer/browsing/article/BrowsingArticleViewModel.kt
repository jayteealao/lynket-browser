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

package arun.com.chromer.browsing.article

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.Result
import arun.com.chromer.data.webarticle.WebArticleRepository
import arun.com.chromer.data.webarticle.model.WebArticle
import arun.com.chromer.search.provider.SearchProvider
import arun.com.chromer.search.provider.SearchProviders
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A simple view model delivering a WebArticle from repo.
 *
 * Migrated to Hilt: Uses @HiltViewModel annotation for automatic ViewModel injection.
 * Converted from RxJava to Kotlin Coroutines in Phase 8.
 */
@HiltViewModel
class BrowsingArticleViewModel
@Inject
constructor(
  private val webArticleRepository: WebArticleRepository,
  searchProviders: SearchProviders
) : ViewModel() {

  private val loadingQueue = MutableSharedFlow<String>(extraBufferCapacity = Int.MAX_VALUE)

  val articleLiveData = MutableLiveData<Result<WebArticle>>()

  init {
    viewModelScope.launch {
      loadingQueue
        .flatMapMerge { url ->
          webArticleRepository
            .getWebArticle(url)
            .map { Result.Success(it) as Result<WebArticle> }
            .catch { e -> emit(Result.Failure(e)) }
            .flowOn(Dispatchers.IO)
        }
        .collect { articleLiveData.value = it }
    }
  }

  val selectedSearchProvider: Observable<SearchProvider> = searchProviders.selectedProvider

  fun loadArticle(url: String) {
    loadingQueue.tryEmit(url)
  }
}
