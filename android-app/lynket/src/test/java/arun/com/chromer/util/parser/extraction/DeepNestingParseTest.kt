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

package arun.com.chromer.util.parser.extraction

import arun.com.chromer.ui.reader.articleBlocks
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jsoup.Jsoup
import org.junit.Test

/**
 * A hostile page, about 1 MB of deeply nested elements, parses and extracts quickly without a
 * crash. jsoup closes elements nested deeper than its parser limit, so the extractor's recursive
 * passes stay shallow.
 */
class DeepNestingParseTest {

  @Test
  fun deeplyNestedPageParsesAndExtractsWithinFiveSeconds() {
    val html = deeplyNestedPage()
    assertThat(html.length).isAtLeast(1_000_000)

    val start = System.nanoTime()
    val failure = runCatching {
      val document = Jsoup.parse(html, URL)
      val article = ArticleExtractor.with(URL, document).extractMetadata().extractContent().article()
      articleBlocks(article.document!!.children())
    }.exceptionOrNull()
    val millis = (System.nanoTime() - start) / 1_000_000
    println("Deeply nested page (${html.length} chars): parse and extraction took $millis ms")

    assertWithMessage("parse and extraction threw").that(failure).isNull()
    assertWithMessage("parse and extraction took $millis ms").that(millis).isLessThan(5_000L)
  }

  private fun deeplyNestedPage(): String = buildString {
    append("<!DOCTYPE html><html><head><title>Nested</title></head><body>")
    repeat(DIVS) { append("<div>") }
    append("<p>").append("A paragraph deep inside the page, long enough to count as article text. ".repeat(3)).append("</p>")
    repeat(DIVS) { append("</div>") }
    repeat(RUNS) { append("<table><tr><td>") }
    append("cell")
    repeat(RUNS) { append("</td></tr></table>") }
    repeat(RUNS) { append("<ul><li>") }
    append("item")
    repeat(RUNS) { append("</li></ul>") }
    append("</body></html>")
  }

  private companion object {
    const val URL = "https://example.com/nested.html"
    const val DIVS = 90_000
    const val RUNS = 1_000
  }
}
