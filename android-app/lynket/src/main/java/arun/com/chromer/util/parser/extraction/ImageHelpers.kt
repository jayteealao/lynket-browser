/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), com.chimbori.crux.articles.ImageHelpers.
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
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

internal object ImageHelpers {

  /** The `href` of the icon with the largest `sizes`, or "" when there is none. */
  fun findLargestIcon(iconNodes: Elements): String {
    var largestIcon: Element? = null
    var maxSize = -1L
    for (iconNode in iconNodes) {
      val size = parseSize(iconNode.attr("sizes"))
      if (size > maxSize) {
        maxSize = size
        largestIcon = iconNode
      }
    }
    return if (largestIcon != null) urlEncodeSpaceCharacter(largestIcon.attr("href")) else ""
  }

  /**
   * The largest dimension in a `sizes` value: "128x128" gives 128, "128x64" gives 128 (the larger
   * side), "24x24 48x48" gives 48, anything else 0.
   */
  fun parseSize(sizes: String?): Long {
    if (sizes == null || sizes.javaTrim().isEmpty()) return 0
    val trimmed = sizes.javaTrim().javaLowerCase()
    if (trimmed.contains(" ")) { // "16x16 24x24": each size on its own.
      var maxSize = 0L
      for (size in trimmed.javaSplit(" ")) {
        val currentSize = parseSize(size)
        if (currentSize > maxSize) maxSize = currentSize
      }
      return maxSize
    } else if (trimmed.contains("x")) { // "128x128" and the like.
      val dimen = trimmed.javaSplit("x")
      if (dimen.size == 2) {
        return try {
          val width = java.lang.Long.parseLong(dimen[0].javaTrim())
          val height = java.lang.Long.parseLong(dimen[1].javaTrim())
          maxOf(width, height)
        } catch (e: NumberFormatException) {
          0
        }
      }
    }
    return 0
  }

  /**
   * The images of the article content, highest weight first. Runs before post-processing, which
   * removes tags this step reads.
   */
  fun extractImages(topNode: Element?): List<Image> {
    val images = ArrayList<Image>()
    if (topNode == null) return images

    var imgElements = topNode.select("img")
    val parent = topNode.parent()
    if (imgElements.isEmpty() && parent != null) imgElements = parent.select("img")

    var maxWeight = 0
    var score = 1.0
    for (imgElement in imgElements) {
      val image = Image.from(imgElement)
      if (image.src.isEmpty()) continue

      // parse() gives null for an address java.net.URI cannot read, a "data:" URI for example.
      val pageUrl = PageUrl.parse(image.src)
      if (pageUrl != null && pageUrl.isAdImage) continue

      image.weight += if (image.height >= 50) 20 else -20
      image.weight += if (image.width >= 50) 20 else -20
      image.weight += if (image.src.startsWith("data:")) -50 else 0
      image.weight += if (image.src.endsWith(".gif")) -20 else 0
      image.weight += if (image.src.endsWith(".jpg")) 5 else 0
      image.weight += if (image.alt.length > 35) 20 else 0
      image.weight += if (image.title.length > 35) 20 else 0
      image.weight += if (image.noFollow) -40 else 0

      image.weight = (image.weight * score).toInt()
      if (image.weight > maxWeight) {
        maxWeight = image.weight
        score /= 2
      }

      images.add(image)
    }

    // Stable sort, highest weight first.
    images.sortWith { o1, o2 -> o2.weight - o1.weight }
    return images
  }
}
