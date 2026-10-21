/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.browsing.article

import arun.com.chromer.util.parser.extraction.legacyText
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** What a block of the article is, from its HTML tag (the legacy reader's row types). */
sealed interface BlockKind {
  data object Paragraph : BlockKind
  data class Heading(val level: Int) : BlockKind
  data object Quote : BlockKind
  data object Code : BlockKind
  data object Bullet : BlockKind
  data class Numbered(val number: Int) : BlockKind
  data object Other : BlockKind
}

/** One block of the article: its kind and its HTML (inner HTML for text blocks). */
data class ArticleBlock(val kind: BlockKind, val html: String)

/**
 * The article's elements as blocks, in page order, by the legacy tag rules: `p`, `h1`..`h6`,
 * `blockquote`, `pre`, list items of `ul` and `ol`, anything else as plain text. A list element
 * gives one block per item. Images and elements without text are left out, as the stores and the
 * legacy reader did (inline images were never loaded). Emptiness uses jsoup 1.9.2's text rule,
 * as the legacy reader did, so a paragraph holding only a soft hyphen still shows.
 */
fun articleBlocks(elements: Iterable<Element>): List<ArticleBlock> = buildList {
  for (element in elements) {
    if (element.legacyText().isBlank()) continue
    when (val tag = element.tagName().lowercase()) {
      "p" -> add(ArticleBlock(BlockKind.Paragraph, element.html()))
      "h1", "h2", "h3", "h4", "h5", "h6" -> add(ArticleBlock(BlockKind.Heading(tag[1].digitToInt()), element.html()))
      "blockquote" -> add(ArticleBlock(BlockKind.Quote, element.html()))
      "pre" -> add(ArticleBlock(BlockKind.Code, element.preformattedText().trimEnd()))
      "img" -> Unit
      "ul", "ol" -> element.children().filter { it.tagName().equals("li", true) && it.legacyText().isNotBlank() }
        .forEachIndexed { i, li ->
          add(ArticleBlock(if (tag == "ul") BlockKind.Bullet else BlockKind.Numbered(i + 1), li.html()))
        }
      "li" -> {
        val parent = element.parent()?.tagName()?.lowercase()
        val kind = if (parent == "ol") BlockKind.Numbered(element.elementSiblingIndex() + 1) else BlockKind.Bullet
        add(ArticleBlock(kind, element.html()))
      }
      else -> add(ArticleBlock(BlockKind.Other, element.outerHtml()))
    }
  }
}

/** The text of a `pre` element with its line breaks and spaces kept. */
private fun Node.preformattedText(): String = when (this) {
  is TextNode -> wholeText
  is Element -> if (tagName().equals("br", true)) LINE_BREAK else childNodes().joinToString("") { it.preformattedText() }
  else -> ""
}

private const val LINE_BREAK = "\n"
