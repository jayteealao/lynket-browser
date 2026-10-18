/*
 * Adapted from jsoup 1.9.2 (https://jsoup.org), the text methods of org.jsoup.nodes.Element,
 * TextNode, Document and org.jsoup.helper.StringUtil, and its block and whitespace-preserving tag
 * lists. Copyright (c) 2009-2016 Jonathan Hedley. MIT License. Changes: translated to Kotlin over the
 * current jsoup node API.
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
import org.jsoup.select.Elements

// The extraction rules count text lengths (a paragraph is at least 50 characters, a child with fewer
// than 20 is skipped), so they measure text the way jsoup 1.9.2 did, which the rules were tuned on.
// Current jsoup text() differs: it turns a no-break space into a space, drops soft hyphens and
// zero-width spaces, and adds a space after a block element (source: jsoup 1.23.2
// internal/StringUtil.java:206-212 and nodes/Element.java:1563-1590). Measured with it, a 49-letter
// paragraph that ends in a no-break space dropped out of an article.

/** The tags jsoup 1.9.2 treated as blocks (parser/Tag.java, blockTags); any other tag is inline. */
private val LEGACY_BLOCK_TAGS = hashSetOf(
  "html", "head", "body", "frameset", "script", "noscript", "style", "meta", "link", "title", "frame",
  "noframes", "section", "nav", "aside", "hgroup", "header", "footer", "p", "h1", "h2", "h3", "h4",
  "h5", "h6", "ul", "ol", "pre", "div", "blockquote", "hr", "address", "figure", "figcaption", "form",
  "fieldset", "ins", "del", "s", "dl", "dt", "dd", "li", "table", "caption", "thead", "tfoot", "tbody",
  "colgroup", "col", "tr", "th", "td", "video", "audio", "canvas", "details", "menu", "plaintext",
  "template", "article", "main", "svg", "math",
)

/** The tags whose text jsoup 1.9.2 kept as written (parser/Tag.java, preserveWhitespaceTags). */
private val LEGACY_PRESERVE_WHITESPACE_TAGS = hashSetOf("pre", "plaintext", "title", "textarea")

private fun isLegacyWhitespace(c: Int): Boolean = c == ' '.code || c == '\t'.code || c == '\n'.code || c == 0x0C || c == '\r'.code

/** Appends [string] with each run of whitespace as one space; [stripLeading] drops leading whitespace. */
private fun appendNormalisedWhitespace(accum: StringBuilder, string: String, stripLeading: Boolean) {
  var lastWasWhite = false
  var reachedNonWhite = false
  var i = 0
  while (i < string.length) {
    val c = string.codePointAt(i)
    if (isLegacyWhitespace(c)) {
      if (!((stripLeading && !reachedNonWhite) || lastWasWhite)) {
        accum.append(' ')
        lastWasWhite = true
      }
    } else {
      accum.appendCodePoint(c)
      lastWasWhite = false
      reachedNonWhite = true
    }
    i += Character.charCount(c)
  }
}

private fun normaliseWhitespace(string: String): String =
  StringBuilder(string.length).also { appendNormalisedWhitespace(it, string, false) }.toString()

private fun lastCharIsWhitespace(sb: StringBuilder): Boolean = sb.isNotEmpty() && sb[sb.length - 1] == ' '

private fun preserveWhitespace(node: Node?): Boolean {
  if (node is Element) {
    if (node.tagName() in LEGACY_PRESERVE_WHITESPACE_TAGS) return true
    val parent = node.parent()
    return parent != null && parent.tagName() in LEGACY_PRESERVE_WHITESPACE_TAGS
  }
  return false
}

private fun appendNormalisedText(accum: StringBuilder, textNode: TextNode) {
  val text = textNode.wholeText
  if (preserveWhitespace(textNode.parentNode())) {
    accum.append(text)
  } else {
    appendNormalisedWhitespace(accum, text, lastCharIsWhitespace(accum))
  }
}

/** jsoup 1.9.2 `Element.text()`: the text of the element and its children, whitespace normalised. */
internal fun Element.legacyText(): String {
  val accum = StringBuilder()
  // Depth first, in document order, without recursion (as jsoup's NodeTraversor walks).
  val root: Node = this
  var node: Node = root
  var depth = 0
  while (true) {
    if (node is TextNode) {
      appendNormalisedText(accum, node)
    } else if (node is Element) {
      if (accum.isNotEmpty() &&
        (node.tagName() in LEGACY_BLOCK_TAGS || node.tagName() == "br") &&
        !lastCharIsWhitespace(accum)
      ) {
        accum.append(' ')
      }
    }
    if (node.childNodeSize() > 0) {
      node = node.childNode(0)
      depth++
    } else {
      while (node.nextSibling() == null && depth > 0) {
        node = node.parentNode()!!
        depth--
      }
      if (node === root) break
      node = node.nextSibling() ?: break
    }
  }
  return accum.toString().javaTrim()
}

/** jsoup 1.9.2 `Element.ownText()`: the text of the element's own text nodes. */
internal fun Element.legacyOwnText(): String {
  val accum = StringBuilder()
  for (i in 0 until childNodeSize()) {
    val child = childNode(i)
    if (child is TextNode) {
      appendNormalisedText(accum, child)
    } else if (child is Element && child.tagName() == "br" && !lastCharIsWhitespace(accum)) {
      accum.append(' ')
    }
  }
  return accum.toString().javaTrim()
}

/** jsoup 1.9.2 `TextNode.text()`: the node's text, whitespace normalised. */
internal fun TextNode.legacyText(): String = normaliseWhitespace(wholeText)

/** jsoup 1.9.2 `Elements.text()`: the text of each element, joined by spaces. */
internal fun Elements.legacyText(): String {
  val sb = StringBuilder()
  for (element in this) {
    if (sb.isNotEmpty()) sb.append(' ')
    sb.append(element.legacyText())
  }
  return sb.toString()
}

/** jsoup 1.9.2 `Document.title()`: the first title element's text, or "". */
internal fun Document.legacyTitle(): String {
  val titleEl = getElementsByTag("title").first()
  return if (titleEl != null) normaliseWhitespace(titleEl.legacyText()).javaTrim() else ""
}
