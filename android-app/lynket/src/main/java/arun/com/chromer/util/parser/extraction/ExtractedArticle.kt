/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), class articles.Article.
 * Copyright Chimbori. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: translated to Kotlin; the reading time
 * estimate, which the app never asked for, is left out.
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

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * What [ArticleExtractor] found on a page: metadata, and after content extraction the article
 * [document] and its [images]. A field that was not found is null or "", as the extractor left it.
 */
class ExtractedArticle internal constructor(val url: String) {
  val originalUrl: String = ""

  var title: String? = ""
  var description: String? = ""
  var siteName: String? = ""
  var themeColor: String? = ""

  var ampUrl: String? = ""

  /** Starts as [url]; metadata extraction replaces it. */
  var canonicalUrl: String? = url
  var imageUrl: String? = ""
  var videoUrl: String? = ""
  var feedUrl: String? = ""
  var faviconUrl: String? = ""

  var document: Document? = null
  var keywords: Collection<String>? = null
  var images: List<Image> = ArrayList()

  /** An image found in the article content. */
  class Image private constructor(val element: Element) {
    var src: String = ""
    var weight: Int = 0
    var title: String = ""
    var height: Int = 0
    var width: Int = 0
    var alt: String = ""
    var noFollow: Boolean = false

    companion object {
      internal fun from(imgElement: Element): Image {
        val image = Image(imgElement)
        // Some sites load images lazily from data-src, so prefer it when it exists.
        image.src = if (imgElement.attr("data-src").isNotEmpty()) imgElement.attr("data-src") else imgElement.attr("src")
        image.width = parseAttrAsInt(imgElement, "width")
        image.height = parseAttrAsInt(imgElement, "height")
        image.alt = imgElement.attr("alt")
        image.title = imgElement.attr("title")
        val parent = imgElement.parent()
        image.noFollow = parent != null && parent.attr("rel").contains("nofollow")
        return image
      }
    }
  }
}
