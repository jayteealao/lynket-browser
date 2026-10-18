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
import arun.com.chromer.util.parser.extraction.ArticleExtractor
import arun.com.chromer.util.parser.extraction.ExtractedArticle
import arun.com.chromer.util.parser.extraction.PageUrl
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
   * The article request keeps the settings it had with jsoup 1.9.2: a 3 s timeout, at most 1 MB of
   * page, and the platform's own User-Agent. Current jsoup defaults to 30 s, 2 MB and a desktop
   * Chrome User-Agent (source: jsoup 1.23.2 `helper/HttpConnection.java` lines 66-67 and 644-645),
   * which would change which pages load and which HTML a site sends.
   */
  private const val ARTICLE_TIMEOUT_MS = 3000
  private const val ARTICLE_MAX_BODY_BYTES = 1024 * 1024

  /**
   * Parses URL to extract article metadata.
   * The extraction is not performed if the given url is not a proper web url.
   *
   * @param url The URL to parse (can be null)
   * @return Pair of URL and extracted Article (Article may be null if extraction failed)
   */
  suspend fun parseUrl(url: String?): Pair<String, ExtractedArticle?> = withContext(Dispatchers.IO) {
    if (url == null) {
      return@withContext Pair(url, null)
    }

    var article: ExtractedArticle? = null
    try {
      val expanded = WebsiteUtilities.unShortenUrl(url)
      val candidateUrl = PageUrl.parse(expanded)!!
      if (candidateUrl.resolveRedirects().isLikelyArticle) {
        // We only need the head tag for meta data.
        val webSiteString = headString(candidateUrl.toString())
        article = ArticleExtractor
          .with(expanded, webSiteString)
          .extractMetadata()
          .article()
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
  suspend fun parseArticle(url: String?): Pair<String, ExtractedArticle?> = withContext(Dispatchers.IO) {
    if (url == null) {
      return@withContext Pair(url, null)
    }

    var article: ExtractedArticle? = null
    try {
      val pageUrl = PageUrl.parse(url)!!
      val isArticle = pageUrl.resolveRedirects().isLikelyArticle
      if (isArticle) {
        val connection = Jsoup.connect(pageUrl.toString())
          .timeout(ARTICLE_TIMEOUT_MS)
          .maxBodySize(ARTICLE_MAX_BODY_BYTES)
        // The agent HttpURLConnection sends when none is set; absent on a plain JVM.
        System.getProperty("http.agent")?.let { connection.userAgent(it) }
        val document = connection.get()
        article = ArticleExtractor.with(pageUrl.toString(), document)
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
