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

package arun.com.chromer.util.parser

import androidx.core.util.Pair
import arun.com.chromer.util.parser.WebsiteUtilities.headString
import com.chimbori.crux.articles.Article
import com.chimbori.crux.articles.ArticleExtractor
import com.chimbori.crux.urls.CruxURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import timber.log.Timber

/**
 * Parser utilities for extracting article metadata and content from URLs.
 * Converted from RxJava Observable-based API to Kotlin Coroutines suspend functions.
 */
object RxParser {
  /**
   * Parses URL to extract article metadata.
   * The extraction is not performed if the given url is not a proper web url.
   *
   * @param url The URL to parse (can be null)
   * @return Pair of URL and extracted Article (Article may be null if extraction failed)
   */
  suspend fun parseUrl(url: String?): Pair<String, Article?> = withContext(Dispatchers.IO) {
    if (url == null) {
      return@withContext Pair(url, null)
    }

    var article: Article? = null
    try {
      val expanded = WebsiteUtilities.unShortenUrl(url)
      val candidateUrl = CruxURL.parse(expanded)
      if (candidateUrl.resolveRedirects().isLikelyArticle) {
        // We only need the head tag for meta data.
        var webSiteString: String? = headString(candidateUrl.toString())
        article = ArticleExtractor
          .with(expanded, webSiteString)
          .extractMetadata()
          .article()
        @Suppress("UNUSED_VALUE")
        webSiteString = null
      }
    } catch (e: Exception) {
      Timber.e(e)
    } catch (e: OutOfMemoryError) {
      Timber.e(e)
    }
    Pair(url, article)
  }

  /**
   * Parses URL to extract full article content including metadata.
   * The extraction is not performed if the given url is not a proper web url.
   *
   * @param url The URL to parse (can be null)
   * @return Pair of URL and extracted Article with full content (Article may be null if extraction failed)
   */
  suspend fun parseArticle(url: String?): Pair<String, Article?> = withContext(Dispatchers.IO) {
    if (url == null) {
      return@withContext Pair(url, null)
    }

    var article: Article? = null
    try {
      val cruxURL = CruxURL.parse(url)
      val isArticle = cruxURL.resolveRedirects().isLikelyArticle
      if (isArticle) {
        val document = Jsoup.connect(cruxURL.toString()).get()
        article = ArticleExtractor.with(cruxURL.toString(), document)
          .extractMetadata()
          .extractContent()
          .article()
      }
    } catch (e: Exception) {
      Timber.e(e)
    } catch (e: OutOfMemoryError) {
      Timber.e(e)
    }
    Pair(url, article)
  }
}
