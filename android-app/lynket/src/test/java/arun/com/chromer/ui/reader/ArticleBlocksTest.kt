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

package arun.com.chromer.ui.reader

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.browsing.article.toReaderArticle
import arun.com.chromer.data.webarticle.model.WebArticle
import arun.com.chromer.util.parser.extraction.ArticleExtractor
import com.google.common.truth.Truth.assertThat
import org.jsoup.Jsoup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Article elements become reader blocks by the legacy tag rules; the fixture article extracts. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ArticleBlocksTest {

  private fun blocks(html: String) = articleBlocks(Jsoup.parse(html).body().children())

  @Test
  fun tagsMapToBlockKinds() {
    val result = blocks(
      """
      <p>Body with <a href="https://example.com">a link</a></p>
      <h1>One</h1><h3>Three</h3><h6>Six</h6>
      <blockquote>Quoted</blockquote>
      <pre>val a = 1
      val b = 2</pre>
      <section>Other text</section>
      """.trimIndent()
    )
    assertThat(result.map { it.kind }).containsExactly(
      BlockKind.Paragraph, BlockKind.Heading(1), BlockKind.Heading(3), BlockKind.Heading(6),
      BlockKind.Quote, BlockKind.Code, BlockKind.Other,
    ).inOrder()
    assertThat(result[0].html).contains("<a href=\"https://example.com\">a link</a>")
    assertThat(result[5].html).isEqualTo("val a = 1\nval b = 2")
  }

  @Test
  fun listsGiveOneBlockPerItem() {
    val result = blocks("<ul><li>a</li><li>b</li></ul><ol><li>one</li><li> </li><li>two</li></ol>")
    assertThat(result.map { it.kind }).containsExactly(
      BlockKind.Bullet, BlockKind.Bullet, BlockKind.Numbered(1), BlockKind.Numbered(2),
    ).inOrder()
  }

  @Test
  fun imagesAndEmptyElementsAreLeftOut() {
    assertThat(blocks("<img src='a.png'><p> </p><div><img src='b.png'></div>")).isEmpty()
  }

  @Test
  fun theFixtureArticleExtractsAndMapsToBlocks() {
    // The device check's fixture, through the same extractor the app uses. A page without an AMP,
    // feed or video address made the extraction fail before (the extractor leaves those null).
    val html = File("../maestro/reader/fixtures/article.html").readText()
    val url = "http://localhost:8000/article.html"
    val article = ArticleExtractor.with(url, Jsoup.parse(html, url)).extractMetadata().extractContent().article()
    val reader = WebArticle.fromArticle(article).toReaderArticle(url)!!
    assertThat(reader.title).isEqualTo("Android 17 brings resizable apps everywhere")
    assertThat(reader.keywords).contains("android")
    assertThat(reader.blocks.map { it.kind }).containsAtLeast(BlockKind.Paragraph, BlockKind.Quote, BlockKind.Heading(3))
  }
}
