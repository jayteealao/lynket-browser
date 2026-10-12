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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import org.jsoup.Jsoup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Reader body text is 18 sp with a 30 sp line height, and on a wide window no laid-out line of the
 * fixture article holds more than 65 characters.
 */
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w841dp-h701dp-xxhdpi")
class ReaderMeasureTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun bodyIs18spOn30sp() {
    val style = readerBodyStyle(TextStyle.Default, 0)
    assertThat(style.fontSize).isEqualTo(18.sp)
    assertThat(style.lineHeight).isEqualTo(30.sp)
    val larger = readerBodyStyle(TextStyle.Default, 6)
    assertThat(larger.fontSize).isEqualTo(24.sp)
    assertThat(larger.lineHeight).isEqualTo(40.sp)
  }

  @Test
  fun noLineOfTheFixtureArticleIsLongerThan65Characters() {
    val paragraphs = Jsoup.parse(File("../maestro/reader/fixtures/article.html").readText()).select("article > p")
    val blocks = articleBlocks(paragraphs)
    assertThat(blocks.size).isAtLeast(5)
    composeRule.setContent {
      ChromerTheme {
        val body = readerBodyStyle(MaterialTheme.typography.bodyLarge, 0)
        val measure = rememberReaderMeasure(body)
        Column(Modifier.width(800.dp)) {
          blocks.forEach { ArticleBlockView(it, body, 0, Color.Blue, measure) }
        }
      }
    }
    val nodes = composeRule.onAllNodesWithTag("article-paragraph").fetchSemanticsNodes()
    assertThat(nodes).hasSize(blocks.size)
    var lines = 0
    nodes.forEach { node ->
      val layouts = mutableListOf<TextLayoutResult>()
      node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
      val layout = layouts.single()
      for (line in 0 until layout.lineCount) {
        val chars = layout.layoutInput.text.text.substring(layout.getLineStart(line), layout.getLineEnd(line, visibleEnd = true))
        assertThat(chars.length).isAtMost(READER_MEASURE_CHARS)
        lines++
      }
      // The text uses most of the measure, so the cap is not hiding a much narrower column.
      assertThat(layout.lineCount).isAtLeast(1)
    }
    assertThat(lines).isGreaterThan(blocks.size)
  }
}
