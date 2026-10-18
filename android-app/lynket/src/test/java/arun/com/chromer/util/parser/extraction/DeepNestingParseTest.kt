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

    // Time the page, not the loading of the parser and extractor classes or the garbage the tests
    // before this one left in the shared test JVM: a small page of the same shape runs first.
    parseAndExtract(deeplyNestedPage(divs = DIVS / 100, runs = RUNS / 100))
    System.gc()

    val start = System.nanoTime()
    val failure = runCatching { parseAndExtract(html) }.exceptionOrNull()
    val millis = (System.nanoTime() - start) / 1_000_000
    println("Deeply nested page (${html.length} chars): parse and extraction took $millis ms")

    assertWithMessage("parse and extraction threw").that(failure).isNull()
    assertWithMessage("parse and extraction took $millis ms").that(millis).isLessThan(5_000L)
  }

  private fun parseAndExtract(html: String) {
    val document = Jsoup.parse(html, URL)
    val article = ArticleExtractor.with(URL, document).extractMetadata().extractContent().article()
    articleBlocks(article.document!!.children())
  }

  private fun deeplyNestedPage(divs: Int = DIVS, runs: Int = RUNS): String = buildString {
    append("<!DOCTYPE html><html><head><title>Nested</title></head><body>")
    repeat(divs) { append("<div>") }
    append("<p>").append("A paragraph deep inside the page, long enough to count as article text. ".repeat(3)).append("</p>")
    repeat(divs) { append("</div>") }
    repeat(runs) { append("<table><tr><td>") }
    append("cell")
    repeat(runs) { append("</td></tr></table>") }
    repeat(runs) { append("<ul><li>") }
    append("item")
    repeat(runs) { append("</li></ul>") }
    append("</body></html>")
  }

  private companion object {
    const val URL = "https://example.com/nested.html"
    const val DIVS = 90_000
    const val RUNS = 1_000
  }
}
