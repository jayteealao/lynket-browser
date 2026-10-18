/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), class articles.ArticleExtractor.
 * Copyright Chimbori. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: translated to Kotlin; the reading time
 * estimate is left out.
 */
/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
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

package arun.com.chromer.util.parser.extraction

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Reads a page's metadata ([extractMetadata]) and its article content ([extractContent]) into an
 * [ExtractedArticle]. The rules are the ones the app has always used for the reader and for
 * website details.
 */
class ArticleExtractor private constructor(url: String, private val document: Document) {
  private val article = ExtractedArticle(url)

  fun extractMetadata(): ArticleExtractor {
    article.title = MetadataHelpers.extractTitle(document)
    article.description = MetadataHelpers.extractDescription(document)
    article.siteName = MetadataHelpers.extractSiteName(document)
    article.themeColor = MetadataHelpers.extractThemeColor(document)
    article.canonicalUrl = makeAbsoluteUrl(article.url, MetadataHelpers.extractCanonicalUrl(document))
    article.ampUrl = makeAbsoluteUrl(article.url, MetadataHelpers.extractAmpUrl(document))
    article.feedUrl = makeAbsoluteUrl(article.url, MetadataHelpers.extractFeedUrl(document))
    article.videoUrl = makeAbsoluteUrl(article.url, MetadataHelpers.extractVideoUrl(document))
    article.faviconUrl = makeAbsoluteUrl(article.url, MetadataHelpers.extractFaviconUrl(document))
    article.keywords = MetadataHelpers.extractKeywords(document)
    return this
  }

  fun extractContent(): ArticleExtractor {
    PreprocessHelpers.preprocess(document)

    val nodes = ExtractionHelpers.getNodes(document)
    var maxWeight = 0
    var bestMatchElement: Element? = null
    for (element in nodes) {
      val currentWeight = ExtractionHelpers.getWeight(element)
      if (currentWeight > maxWeight) {
        maxWeight = currentWeight
        bestMatchElement = element
        if (maxWeight > 200) break
      }
    }

    // Images first: post-processing may remove them.
    article.images = ImageHelpers.extractImages(bestMatchElement)
    article.document = PostprocessHelpers.postprocess(bestMatchElement)
    article.imageUrl = makeAbsoluteUrl(article.url, MetadataHelpers.extractImageUrl(document, article.images))
    return this
  }

  fun article(): ExtractedArticle = article

  companion object {
    /** An extractor over raw HTML, which must not be empty. */
    @JvmStatic
    fun with(url: String, html: String): ArticleExtractor {
      if (html.isEmpty()) throw IllegalArgumentException()
      return ArticleExtractor(url, Jsoup.parse(html))
    }

    /** An extractor over a document parsed elsewhere, which saves a second parse. */
    @JvmStatic
    fun with(url: String, document: Document): ArticleExtractor = ArticleExtractor(url, document)
  }
}
