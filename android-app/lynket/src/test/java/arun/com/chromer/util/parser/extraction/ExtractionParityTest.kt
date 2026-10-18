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

import android.app.Application
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.data.webarticle.model.WebArticle
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.ui.reader.articleBlocks
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * The reader and the website details show the same values for a fixed set of saved pages as they
 * did with the earlier extractor (Crux 2.0.1 on jsoup 1.9.2).
 *
 * Each fixture in `src/test/resources/extraction/fixtures/` has a golden in `../golden/`, recorded by
 * the earlier extractor, called the way the app called it: the website details from the page's head
 * (as `WebsiteUtilities.headString` reads it), the reader from the whole page, cleaned up as
 * `WebArticleNetworkStore` does, and turned into reader blocks. The app's extractor gave the same
 * values and cache bytes as the earlier one on jsoup 1.9.2, so a golden may only be recorded while
 * jsoup 1.9.2 is on the classpath; on a current jsoup, a missing golden fails.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ExtractionParityTest {

  @Test
  fun everyFixtureMatchesItsGolden() {
    for (fixture in FIXTURES) {
      val html = fixture.file.readText(Charsets.UTF_8)
      val golden = File(GOLDEN_DIR, "${fixture.name}.golden.txt")
      if (recordIfMissing(golden) { snapshot(fixture.url, html) }) continue
      assertWithMessage(fixture.name).that(snapshot(fixture.url, html)).isEqualTo(golden.readText(Charsets.UTF_8))
    }
  }

  @Test
  fun everyAddressMatchesItsGolden() {
    val golden = File(GOLDEN_DIR, "urls.golden.txt")
    if (recordIfMissing(golden) { urlSnapshot() }) return
    assertThat(urlSnapshot()).isEqualTo(golden.readText(Charsets.UTF_8))
  }

  /**
   * Article cache entries written by the earlier code still open, and give the same reader blocks
   * as the earlier code gave when it read them.
   */
  @Test
  fun recordedArticleParcelsReadBack() {
    for (fixture in FIXTURES) {
      val html = fixture.file.readText(Charsets.UTF_8)
      val golden = File(GOLDEN_DIR, "${fixture.name}.parcel")
      val blocksGolden = File(GOLDEN_DIR, "${fixture.name}.parcel-blocks.txt")
      if (recordIfMissing(golden) { null.also { golden.writeBytes(marshall(article(fixture.url, html))) } }) {
        blocksGolden.writeText(restoredBlocks(golden.readBytes()), Charsets.UTF_8)
        continue
      }
      val bytes = golden.readBytes()
      assertWithMessage("parcel: ${fixture.name}").that(restoredBlocks(bytes)).isEqualTo(blocksGolden.readText(Charsets.UTF_8))
      if (OLD_JSOUP) {
        // On the earlier jsoup the bytes are the same as well.
        assertWithMessage("parcel bytes: ${fixture.name}").that(marshall(article(fixture.url, html))).isEqualTo(bytes)
      }
    }
  }

  private data class Fixture(val name: String, val file: File, val url: String)

  companion object {
    private val FIXTURE_DIR = File("src/test/resources/extraction/fixtures")
    private val GOLDEN_DIR = File("src/test/resources/extraction/golden")
    private val READER_FIXTURE_DIR = File("../maestro/reader/fixtures")

    private val FIXTURES = listOf(
      Fixture("news", File(FIXTURE_DIR, "news.html"), "https://en.wikinews.org/wiki/Grateful_Dead_rhythm_guitarist_Bob_Weir_dies,_aged_78"),
      Fixture("blog", File(FIXTURE_DIR, "blog.html"), "https://creativecommons.org/2026/09/21/a-principled-approach-as-the-sands-shift/"),
      Fixture("no-article", File(FIXTURE_DIR, "no-article.html"), "https://www.wikipedia.org/"),
      Fixture("images", File(FIXTURE_DIR, "images.html"), "https://en.wikipedia.org/wiki/Lighthouse"),
      Fixture("non-english", File(FIXTURE_DIR, "non-english.html"), "https://de.wikipedia.org/wiki/Leuchtturm"),
      Fixture("metadata", File(FIXTURE_DIR, "metadata.html"), "https://news.example.com/2026/10/harbour-lights.html"),
      Fixture("reader-article", File(READER_FIXTURE_DIR, "article.html"), "http://localhost:8000/article.html"),
      Fixture("reader-no-article", File(READER_FIXTURE_DIR, "no-article.html"), "http://localhost:8000/no-article.html"),
    )

    /** jsoup 1.9.2, which the earlier extractor bundled, has no `StreamParser` (added in 1.18.1). */
    private val OLD_JSOUP: Boolean = try {
      Class.forName("org.jsoup.parser.StreamParser")
      false
    } catch (e: ClassNotFoundException) {
      true
    }

    /** The website details as the app reads them, from the page's head. */
    private fun website(url: String, headHtml: String): Website =
      Website.fromArticle(ArticleExtractor.with(url, headHtml).extractMetadata().article())

    /** The reader article as the app reads it, from the whole page, cleaned up as the article store does. */
    private fun article(url: String, html: String): WebArticle = WebArticle.fromArticle(
      ArticleExtractor.with(url, Jsoup.parse(html, url)).extractMetadata().extractContent().article()
    ).also(::removeEmptyElements)

    /** The address after redirect unwrapping, and whether it is likely an article, as `RxParser` checks it. */
    private fun address(input: String): String = try {
      val parsed = PageUrl.parse(input)
      if (parsed == null) "null" else {
        val likely = parsed.resolveRedirects().isLikelyArticle
        "$parsed article=$likely"
      }
    } catch (e: Exception) {
      "error ${e.javaClass.name}"
    }

    /**
     * Writes [golden] from [record] when it is missing and jsoup 1.9.2 is present, and says so;
     * fails when it is missing on a current jsoup.
     */
    private fun recordIfMissing(golden: File, record: () -> String?): Boolean {
      if (golden.exists()) return false
      assertWithMessage("missing golden ${golden.path}; goldens are only recorded on jsoup 1.9.2").that(OLD_JSOUP).isTrue()
      golden.parentFile!!.mkdirs()
      record()?.let { golden.writeText(it, Charsets.UTF_8) }
      println("Recorded ${golden.path}")
      return true
    }

    /** The head as `WebsiteUtilities.headString` reads it: the lines from `<head` to `</head>`, joined. */
    private fun headString(html: String): String = buildString {
      var insideHeadTag = false
      for (line in html.lineSequence()) {
        if (line.contains("<head")) insideHeadTag = true
        if (insideHeadTag) append(line)
        if (line.contains("</head>")) break
      }
    }

    private fun removeEmptyElements(article: WebArticle) {
      val iterator = article.elements?.iterator() ?: return
      while (iterator.hasNext()) {
        if (iterator.next().text().isEmpty()) iterator.remove()
      }
    }

    private fun marshall(article: WebArticle): ByteArray {
      val parcel = Parcel.obtain()
      try {
        article.writeToParcel(parcel, 0)
        return parcel.marshall()
      } finally {
        parcel.recycle()
      }
    }

    private fun String?.oneLine(): String = this?.replace("\\", "\\\\")?.replace("\r", "\\r")?.replace("\n", "\\n") ?: "<null>"

    /** The reader blocks of a recorded parcel as the current code reads it, one `kind | text` line each. */
    private fun restoredBlocks(bytes: ByteArray): String {
      val parcel = Parcel.obtain()
      try {
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        val restored = WebArticle.CREATOR.createFromParcel(parcel)
        return articleBlocks(restored.elements ?: Elements()).joinToString("") {
          "${it.kind} | ${Jsoup.parse(it.html).text().oneLine()}\n"
        }
      } finally {
        parcel.recycle()
      }
    }

    /** Every value the website details and the reader receive, one `key: value` line each. */
    private fun snapshot(url: String, html: String): String = buildString {
      fun line(key: String, value: Any?) = append(key).append(": ").append(value?.toString().oneLine()).append('\n')

      val website = website(url, headString(html))
      line("website.title", website.title)
      line("website.url", website.url)
      line("website.canonicalUrl", website.canonicalUrl)
      line("website.faviconUrl", website.faviconUrl)
      line("website.themeColor", website.themeColor)
      line("website.ampUrl", website.ampUrl)

      val article = article(url, html)
      line("reader.url", article.url)
      line("reader.originalUrl", article.originalUrl)
      line("reader.title", article.title)
      line("reader.description", article.description)
      line("reader.siteName", article.siteName)
      line("reader.themeColor", article.themeColor)
      line("reader.ampUrl", article.ampUrl)
      line("reader.canonicalUrl", article.canonicalUrl)
      line("reader.leadImage", article.imageUrl)
      line("reader.videoUrl", article.videoUrl)
      line("reader.feedUrl", article.feedUrl)
      line("reader.faviconUrl", article.faviconUrl)
      line("reader.keywords", article.keywords.joinToString(" | "))
      val blocks = articleBlocks(article.elements ?: Elements())
      line("reader.blocks", blocks.size)
      blocks.forEachIndexed { i, block ->
        line("reader.block.$i.kind", block.kind)
        line("reader.block.$i.text", Jsoup.parse(block.html).text())
        line("reader.block.$i.html", block.html)
      }
    }

    private fun urlSnapshot(): String = buildString {
      for (input in File(FIXTURE_DIR, "urls.txt").readLines(Charsets.UTF_8).filter { it.isNotBlank() }) {
        append(input).append(" -> ").append(address(input)).append('\n')
      }
    }
  }
}
