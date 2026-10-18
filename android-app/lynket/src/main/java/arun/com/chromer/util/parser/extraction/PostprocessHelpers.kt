/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), com.chimbori.crux.articles.PostprocessHelpers.
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

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.regex.Pattern

/** Cleans up the chosen article element into the document the reader shows. */
internal object PostprocessHelpers {
  /** Text shorter than this is not a paragraph. */
  private const val MIN_LENGTH_FOR_PARAGRAPHS = 50

  private val UNLIKELY_CSS_STYLES = Pattern.compile("display\\:none|visibility\\:hidden")

  /** Tags that are not kept, but whose content may be. */
  private val REMOVE_TAGS_BUT_RETAIN_CONTENT = hashSetOf(
    "font", "table", "tbody", "tr", "td", "div", "ol", "ul", "li", "span",
  )

  /** Tags kept in the output: the ones the reader can show. */
  private val RETAIN_TAGS = hashSetOf(
    "p", "b", "i", "u", "strong", "em", "a", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote",
  )

  /** Tags that are not paragraphs, so their content may be short. */
  private val TAGS_EXEMPT_FROM_MIN_LENGTH_CHECK = hashSetOf(
    "b", "i", "u", "strong", "em", "a", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote",
  )

  /** The only attribute kept in the output. */
  private val ATTRIBUTES_TO_RETAIN_IN_HTML = hashSetOf("href")

  /**
   * Tags allowed at the top level of the output, so short inline tags stay inside paragraphs but
   * never stand alone.
   */
  private val RETAIN_TAGS_TOP_LEVEL = hashSetOf(
    "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "li",
  )

  fun postprocess(topNode: Element?): Document {
    val doc = Document("")
    if (topNode == null) return doc

    removeNodesWithNegativeScores(topNode)
    replaceLineBreaksWithSpaces(topNode)
    removeUnlikelyChildNodes(topNode)
    removeTagsButRetainContent(topNode)
    removeTagsNotLikelyToBeParagraphs(topNode)
    removeTopLevelTagsNotLikelyToBeParagraphs(topNode)
    removeShortParagraphs(topNode)
    removeDisallowedAttributes(topNode)

    for (node in topNode.childNodes()) {
      doc.appendChild(node.clone())
    }
    return doc
  }

  /**
   * What the reader has always put where a line break was. The source of Crux 2.0.1 has " • ",
   * but its published jar was compiled with the wrong source encoding, so the app shipped the
   * three characters "â€¢" (U+00E2 U+20AC U+00A2) between spaces; the cached articles and the
   * reader text hold them. sdlc-debt: kept on purpose so the reader text stays the same; showing
   * " • " instead changes what the reader shows, so it is a separate change made on purpose.
   */
  private const val LINE_BREAK_SEPARATOR = " â€¢ "

  private fun replaceLineBreaksWithSpaces(topNode: Element) {
    for (brNextToBrElement in topNode.select("br + br")) brNextToBrElement.remove()
    for (brElement in topNode.select("br")) {
      val previousSibling = brElement.previousSibling()
      if (previousSibling != null) {
        previousSibling.after(LINE_BREAK_SEPARATOR)
      } else {
        brElement.parent()!!.append(LINE_BREAK_SEPARATOR)
      }
      brElement.unwrap()
    }
  }

  private fun removeTopLevelTagsNotLikelyToBeParagraphs(element: Element) {
    for (childElement in element.children()) {
      if (!RETAIN_TAGS_TOP_LEVEL.contains(childElement.tagName())) childElement.remove()
    }
  }

  private fun removeTagsNotLikelyToBeParagraphs(element: Element) {
    for (childElement in element.children()) {
      if (!RETAIN_TAGS.contains(childElement.tagName())) {
        childElement.remove()
      } else if (childElement.children().size > 0) {
        removeTagsNotLikelyToBeParagraphs(childElement)
      }
    }
  }

  private fun removeTagsButRetainContent(element: Element) {
    for (childElement in element.children()) {
      removeTagsButRetainContent(childElement)
      if (REMOVE_TAGS_BUT_RETAIN_CONTENT.contains(childElement.tagName())) {
        childElement.tagName("p") // The wrapper becomes a <p> instead of being unwrapped.
      }
    }
  }

  private fun removeShortParagraphs(topNode: Element) {
    for (i in topNode.childNodeSize() - 1 downTo 0) {
      val childNode = topNode.childNode(i)

      var text: String? = null
      var isExemptFromMinTextLengthCheck = false
      if (childNode is TextNode) {
        text = childNode.text().javaTrim()
      } else if (childNode is Element) {
        text = childNode.text().javaTrim()
        isExemptFromMinTextLengthCheck = TAGS_EXEMPT_FROM_MIN_LENGTH_CHECK.contains(childNode.tagName())
      }

      if (text == null ||
        text.isEmpty() ||
        (!isExemptFromMinTextLengthCheck && text.length < MIN_LENGTH_FOR_PARAGRAPHS) ||
        text.length > countLetters(text) * 2
      ) {
        childNode.remove()
      }
    }
  }

  private fun removeUnlikelyChildNodes(element: Element) {
    for (childElement in element.children()) {
      if (isUnlikely(childElement)) {
        childElement.remove()
      } else if (childElement.children().size > 0) {
        removeUnlikelyChildNodes(childElement)
      }
    }
  }

  private fun removeNodesWithNegativeScores(topNode: Element) {
    val elementsWithGravityScore = topNode.select(ExtractionHelpers.GRAVITY_SCORE_SELECTOR)
    for (element in elementsWithGravityScore) {
      val score = Integer.parseInt(element.attr(ExtractionHelpers.GRAVITY_SCORE_ATTRIBUTE))
      if (score < 0 || element.text().length < MIN_LENGTH_FOR_PARAGRAPHS) element.remove()
    }
  }

  private fun isUnlikely(element: Element): Boolean {
    val styleAttribute = element.attr("style")
    val classAttribute = element.attr("class")
    return classAttribute.javaLowerCase().contains("caption") ||
      UNLIKELY_CSS_STYLES.matcher(styleAttribute).find() ||
      UNLIKELY_CSS_STYLES.matcher(classAttribute).find()
  }

  private fun removeDisallowedAttributes(node: Element) {
    for (childElement in node.children()) removeDisallowedAttributes(childElement)
    val keysToRemove = ArrayList<String>()
    for (attribute in node.attributes()) {
      if (!ATTRIBUTES_TO_RETAIN_IN_HTML.contains(attribute.key)) keysToRemove.add(attribute.key)
    }
    for (key in keysToRemove) node.removeAttr(key)
  }
}
