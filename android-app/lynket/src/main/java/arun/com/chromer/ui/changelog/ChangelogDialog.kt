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

package arun.com.chromer.ui.changelog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import arun.com.chromer.R

/**
 * The changelog, shown as a dialog destination over home: the release notes from
 * `R.string.changelog_text` with working links, and the old dialog's buttons, Rate on Play Store and
 * OK. Text renders as Compose text; no web view.
 */
@Composable
fun ChangelogDialog(onOk: () -> Unit, onRate: () -> Unit) {
  val html = stringResource(R.string.changelog_text)
  val linkColor = MaterialTheme.colorScheme.primary
  val text = remember(html, linkColor) {
    runCatching {
      AnnotatedString.fromHtml(
        // The notes carry a <style> block written for the old web view; Compose text would print it.
        html.replace(STYLE_BLOCK, ""),
        linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
      )
    }.getOrElse { AnnotatedString(html) }
  }
  Surface(
    shape = RoundedCornerShape(28.dp),
    color = AlertDialogDefaults.containerColor,
    tonalElevation = AlertDialogDefaults.TonalElevation,
    modifier = Modifier.widthIn(max = 560.dp).testTag("changelog"),
  ) {
    Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
      Text(
        text = stringResource(R.string.changelog),
        style = MaterialTheme.typography.headlineSmall,
        color = AlertDialogDefaults.titleContentColor,
        modifier = Modifier.padding(horizontal = 24.dp),
      )
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = AlertDialogDefaults.textContentColor,
        modifier = Modifier
          .padding(horizontal = 24.dp, vertical = 16.dp)
          .heightIn(max = 440.dp)
          .verticalScroll(rememberScrollState()),
      )
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      ) {
        TextButton(onClick = onRate, modifier = Modifier.heightIn(min = 48.dp)) {
          Text(stringResource(R.string.rate_play_store))
        }
        TextButton(onClick = onOk, modifier = Modifier.heightIn(min = 48.dp).testTag("changelog-ok")) {
          Text(stringResource(android.R.string.ok))
        }
      }
    }
  }
}

private val STYLE_BLOCK = Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
