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

import arun.com.chromer.browsing.article.BlockKind
import arun.com.chromer.browsing.article.ArticleBlock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import arun.com.chromer.R
import timber.log.Timber

/** Reader body size at the default text size: 18 sp with a 30 sp line height (visual contract). */
val ReaderBodySize: TextUnit = 18.sp
private const val LINE_HEIGHT_RATIO = 30f / 18f

/** At most this many characters on a line of body text. */
const val READER_MEASURE_CHARS = 65

/**
 * [READER_MEASURE_CHARS] narrow characters (short words, many spaces) that size the measure: a line
 * of ordinary text is wider per character, so it breaks before 65 characters.
 */
internal val MEASURE_SAMPLE = "it is a little list, if it is still in it then i will fill it in till it is"
  .take(READER_MEASURE_CHARS)

/** The body style for a text-size [increment] in sp: 18 sp plus the increment, line height in proportion. */
fun readerBodyStyle(base: TextStyle, increment: Int): TextStyle {
  val size = ReaderBodySize.value + increment
  return base.copy(fontSize = size.sp, lineHeight = (size * LINE_HEIGHT_RATIO).sp, letterSpacing = 0.sp)
}

/** The widest a text block may be so a line holds at most [READER_MEASURE_CHARS] characters. */
@Composable
fun rememberReaderMeasure(bodyStyle: TextStyle): Dp {
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  return remember(bodyStyle, density) {
    with(density) { measurer.measure(MEASURE_SAMPLE, bodyStyle, softWrap = false).size.width.toDp() }
  }
}

/** The link schemes the reader opens; any other link shows the unsupported-link message. */
internal val OPENABLE_LINK_SCHEMES = setOf("http", "https", "mailto")

/** HTML of a block as styled text with links in [linkColour]; a web or mail link opens with the URI handler. */
@Composable
fun rememberArticleText(html: String, linkColour: Color): AnnotatedString {
  val uriHandler = LocalUriHandler.current
  val context = LocalContext.current
  return remember(html, linkColour, uriHandler) {
    AnnotatedString.fromHtml(
      htmlString = html,
      linkStyles = TextLinkStyles(SpanStyle(color = linkColour, textDecoration = TextDecoration.Underline)),
      linkInteractionListener = { link ->
        val url = (link as? LinkAnnotation.Url)?.url
        try {
          if (url == null) error("no address")
          // A page can plant links to any app (tel:, market:, content:, intent schemes); the
          // reader opens only web and mail links and shows the unsupported-link message otherwise.
          if (url.toUri().scheme?.lowercase() !in OPENABLE_LINK_SCHEMES) error("not a web link")
          uriHandler.openUri(url)
        } catch (e: Exception) {
          Timber.w(e, "Could not open %s", url)
          Toast.makeText(context, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
        }
      },
    )
  }
}

/**
 * One article block. Body text uses [bodyStyle]; headings follow the type scale plus the same
 * increment; the quote is in [accent] after a 1 dp rule; code is monospace on a tonal surface.
 * Text is capped at [measure] so a line stays within the reading measure.
 */
@Composable
fun ArticleBlockView(block: ArticleBlock, bodyStyle: TextStyle, increment: Int, accent: Color, measure: Dp, modifier: Modifier = Modifier) {
  val colours = MaterialTheme.colorScheme
  val type = MaterialTheme.typography
  val textModifier = modifier.widthIn(max = measure).fillMaxWidth()
  when (val kind = block.kind) {
    BlockKind.Paragraph, BlockKind.Other -> Text(
      rememberArticleText(block.html, accent),
      style = bodyStyle,
      color = colours.onSurface,
      modifier = textModifier.testTag("article-paragraph"),
    )
    is BlockKind.Heading -> {
      val base = when (kind.level) {
        1 -> type.headlineMedium
        2 -> type.headlineSmall
        3 -> type.titleLarge
        4 -> type.titleMedium
        else -> type.titleSmall
      }
      Text(
        rememberArticleText(block.html, accent),
        style = base.copy(fontSize = base.fontSize.plus(increment), fontWeight = FontWeight.SemiBold),
        color = colours.onSurface,
        modifier = textModifier.padding(top = 8.dp).semantics { heading() }.testTag("article-heading"),
      )
    }
    BlockKind.Quote -> Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("article-quote")) {
      Box(Modifier.width(1.dp).fillMaxHeight().background(colours.outlineVariant))
      Text(
        rememberArticleText(block.html, accent),
        style = bodyStyle,
        color = accent,
        modifier = Modifier.padding(start = 16.dp).widthIn(max = measure),
      )
    }
    BlockKind.Code -> Text(
      block.html,
      style = bodyStyle.copy(fontFamily = FontFamily.Monospace, fontSize = bodyStyle.fontSize.times(0.85f)),
      color = colours.onSurface,
      softWrap = false,
      modifier = modifier
        .fillMaxWidth()
        .background(colours.surfaceContainerHighest, MaterialTheme.shapes.small)
        .horizontalScroll(rememberScrollState())
        .padding(12.dp)
        .testTag("article-code"),
    )
    BlockKind.Bullet, is BlockKind.Numbered -> Row(modifier.fillMaxWidth().testTag("article-list-item")) {
      Text(
        if (kind is BlockKind.Numbered) "${kind.number}." else "•",
        style = bodyStyle,
        color = colours.onSurfaceVariant,
        modifier = Modifier.width(28.dp),
      )
      Text(
        rememberArticleText(block.html, accent),
        style = bodyStyle,
        color = colours.onSurface,
        modifier = Modifier.widthIn(max = measure),
      )
    }
  }
}

private operator fun TextUnit.plus(increment: Int): TextUnit = (value + increment).sp
