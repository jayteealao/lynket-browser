/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), class articles.MetadataHelpers.
 * Copyright Chimbori. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: translated to Kotlin.
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

import arun.com.chromer.util.parser.extraction.ExtractedArticle.Image
import org.jsoup.nodes.Document

internal object MetadataHelpers {

  fun extractTitle(doc: Document): String = cleanTitle(
    heuristicString(
      doc.title(),
      { innerTrim(doc.select("head title").text()) },
      { innerTrim(doc.select("head meta[name=title]").attr("content")) },
      { innerTrim(doc.select("head meta[property=og:title]").attr("content")) },
      { innerTrim(doc.select("head meta[name=twitter:title]").attr("content")) },
    )!!
  )

  fun extractAmpUrl(doc: Document): String? =
    heuristicString(urlEncodeSpaceCharacter(doc.select("link[rel=amphtml]").attr("href")))

  fun extractCanonicalUrl(doc: Document): String? = heuristicString(
    null,
    { urlEncodeSpaceCharacter(doc.select("head link[rel=canonical]").attr("href")) },
    { urlEncodeSpaceCharacter(doc.select("head meta[property=og:url]").attr("content")) },
    { urlEncodeSpaceCharacter(doc.select("head meta[name=twitter:url]").attr("content")) },
  )

  fun extractDescription(doc: Document): String? = heuristicString(
    null,
    { innerTrim(doc.select("head meta[name=description]").attr("content")) },
    { innerTrim(doc.select("head meta[property=og:description]").attr("content")) },
    { innerTrim(doc.select("head meta[name=twitter:description]").attr("content")) },
  )

  fun extractSiteName(doc: Document): String? = heuristicString(
    null,
    { innerTrim(doc.select("head meta[property=og:site_name]").attr("content")) },
    { innerTrim(doc.select("head meta[name=application-name]").attr("content")) },
  )

  fun extractThemeColor(doc: Document): String = doc.select("meta[name=theme-color]").attr("content")

  fun extractImageUrl(doc: Document, images: List<Image>?): String? = heuristicString(
    null,
    // Twitter Cards and Open Graph images are usually of higher quality, so they rank first.
    { urlEncodeSpaceCharacter(doc.select("head meta[name=twitter:image]").attr("content")) },
    { urlEncodeSpaceCharacter(doc.select("head meta[property=og:image]").attr("content")) },
    // Then a hero image of the article itself.
    { if (images != null && images.isNotEmpty()) urlEncodeSpaceCharacter(images[0].src) else null },
    // image_src and thumbnails are usually of low quality, so they come after article images.
    { urlEncodeSpaceCharacter(doc.select("link[rel=image_src]").attr("href")) },
    { urlEncodeSpaceCharacter(doc.select("head meta[name=thumbnail]").attr("content")) },
  )

  fun extractFeedUrl(doc: Document): String? = heuristicString(
    null,
    { doc.select("link[rel=alternate]").select("link[type=application/rss+xml]").attr("href") },
    { doc.select("link[rel=alternate]").select("link[type=application/atom+xml]").attr("href") },
  )

  fun extractVideoUrl(doc: Document): String =
    urlEncodeSpaceCharacter(doc.select("head meta[property=og:video]").attr("content"))

  fun extractFaviconUrl(doc: Document): String? = heuristicString(
    null,
    { urlEncodeSpaceCharacter(ImageHelpers.findLargestIcon(doc.select("head link[rel=icon]"))) },
    { urlEncodeSpaceCharacter(ImageHelpers.findLargestIcon(doc.select("head link[rel^=apple-touch-icon]"))) },
    { urlEncodeSpaceCharacter(doc.select("head link[rel^=shortcut],link[rel$=icon]").attr("href")) },
  )

  fun extractKeywords(doc: Document): Collection<String> {
    var content = innerTrim(doc.select("head meta[name=keywords]").attr("content"))

    if (content.startsWith("[") && content.endsWith("]")) {
      content = content.substring(1, content.length - 1)
    }

    val split = content.javaSplit("\\s*,\\s*")
    if (split.size > 1 || (split.isNotEmpty() && "" != split[0])) {
      return split.asList()
    }
    return emptyList()
  }
}
