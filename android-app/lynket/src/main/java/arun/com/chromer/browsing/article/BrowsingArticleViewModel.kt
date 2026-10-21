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

import arun.com.chromer.util.WebUrl
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.webarticle.WebArticleRepository
import arun.com.chromer.data.webarticle.model.WebArticle
import arun.com.chromer.di.hilt.DefaultDispatcher
import arun.com.chromer.search.provider.SearchProviders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The article as the reader shows it: metadata and the content blocks in page order. */
data class ReaderArticle(
  val url: String,
  val title: String,
  val siteName: String,
  val imageUrl: String,
  val keywords: List<String>,
  val blocks: List<ArticleBlock>,
)

/** Where the reader stands for one link. */
sealed interface ReaderState {
  /** The article is being extracted. */
  data object Loading : ReaderState

  /** The article was extracted and has at least one block. */
  data class Content(val article: ReaderArticle) : ReaderState

  /** No article: extraction failed or found nothing to read. */
  data object Error : ReaderState
}

/**
 * Extracts the article of one link for the reader. The state and the text-size increment survive
 * recreation; a failed extraction is the error state, never a silent close.
 */
@HiltViewModel
class BrowsingArticleViewModel
@Inject
constructor(
  private val webArticleRepository: WebArticleRepository,
  private val searchProviders: SearchProviders,
  private val savedStateHandle: SavedStateHandle,
  @DefaultDispatcher private val blockDispatcher: CoroutineDispatcher,
) : ViewModel() {

  private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
  val state: StateFlow<ReaderState> = _state.asStateFlow()

  /** The text-size increment in sp the slider set; null until the first [initTextSize]. */
  val textSizeIncrement: StateFlow<Int?> = savedStateHandle.getStateFlow(KEY_TEXT_SIZE, null)

  private var url: String? = null
  private var job: Job? = null

  /** Loads [url] unless it is loaded or loading already (for example after recreation). */
  fun load(url: String) {
    if (this.url == url) return
    this.url = url
    extract(url)
  }

  /** Extracts the article again (the error state's Retry). */
  fun retry() {
    url?.let(::extract)
  }

  /** Sets the increment from the stored setting once; a value the slider set wins after recreation. */
  fun initTextSize(stored: Int) {
    if (savedStateHandle.get<Int>(KEY_TEXT_SIZE) == null) savedStateHandle[KEY_TEXT_SIZE] = stored
  }

  fun setTextSize(increment: Int) {
    savedStateHandle[KEY_TEXT_SIZE] = increment.coerceIn(0, MAX_TEXT_SIZE_INCREMENT)
  }

  /** The search address of [keyword] with the selected search provider (keyword chips). */
  suspend fun searchUrl(keyword: String): String = searchProviders.selectedProvider.first().getSearchUrl(keyword)

  private fun extract(url: String) {
    job?.cancel()
    _state.value = ReaderState.Loading
    job = viewModelScope.launch {
      val article = try {
        webArticleRepository.getWebArticle(url).firstOrNull()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Article extraction failed for %s", WebUrl.hostForLog(url))
        null
      }
      // Building the blocks walks and serialises the whole article; it stays off the main thread.
      val reader = article?.let { withContext(blockDispatcher) { it.toReaderArticle(url) } }
      _state.value = reader?.let { ReaderState.Content(it) } ?: ReaderState.Error
    }
  }

  companion object {
    private const val KEY_TEXT_SIZE = "reader_text_size_increment"
    const val MAX_TEXT_SIZE_INCREMENT = 15
  }
}

/** The reader's article, or null when it has fewer than one block to show (the legacy rule). */
internal fun WebArticle.toReaderArticle(requestedUrl: String): ReaderArticle? {
  val blocks = elements?.let(::articleBlocks).orEmpty()
  if (blocks.isEmpty()) return null
  return ReaderArticle(
    url = url.ifEmpty { requestedUrl },
    title = title,
    siteName = siteName,
    imageUrl = imageUrl,
    keywords = keywords.filter { it.isNotBlank() },
    blocks = blocks,
  )
}
