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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.browsing.article.ArticleBlock
import arun.com.chromer.browsing.article.BlockKind
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** A tap on a link in an article: a web link opens, a link to another app shows the message. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ArticleLinkTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val opened = mutableListOf<String>()

  private fun show() {
    val handler = object : UriHandler {
      override fun openUri(uri: String) {
        opened += uri
      }
    }
    composeRule.setContent {
      ChromerTheme {
        CompositionLocalProvider(LocalUriHandler provides handler) {
          ArticleBlockView(
            block = ArticleBlock(
              BlockKind.Paragraph,
              """Read the <a href="https://example.com/a">web</a> page or <a href="tel:123">call</a> us.""",
            ),
            bodyStyle = TextStyle(fontSize = 18.sp),
            increment = 0,
            accent = Color(0xFF3F51B5),
            measure = 600.dp,
          )
        }
      }
    }
  }

  private fun tap(url: String) {
    composeRule.onNodeWithTag("article-paragraph")
      .performFirstLinkClick { (it.item as? LinkAnnotation.Url)?.url == url }
    composeRule.waitForIdle()
  }

  @Test
  fun aWebLinkOpensThroughTheUriHandler() {
    show()
    tap("https://example.com/a")
    assertThat(opened).containsExactly("https://example.com/a")
    assertThat(ShadowToast.getTextOfLatestToast()).isNull()
  }

  @Test
  fun aLinkToAnotherAppShowsTheUnsupportedLinkMessage() {
    show()
    tap("tel:123")
    assertThat(opened).isEmpty()
    assertThat(ShadowToast.getTextOfLatestToast())
      .isEqualTo(ApplicationProvider.getApplicationContext<Application>().getString(R.string.unsupported_link))
  }
}
