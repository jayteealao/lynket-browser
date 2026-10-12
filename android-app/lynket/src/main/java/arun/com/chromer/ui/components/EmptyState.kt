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

package arun.com.chromer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The organic blob behind state icons: uneven corners read as a soft, hand-drawn shape. */
internal val StateBlobShape = RoundedCornerShape(
  topStartPercent = 46,
  topEndPercent = 38,
  bottomEndPercent = 50,
  bottomStartPercent = 34,
)

@Composable
internal fun StateBlob(icon: ImageVector, container: Color, content: Color, size: Dp = 96.dp) {
  Box(
    modifier = Modifier.size(size).background(container, StateBlobShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.42f))
  }
}

/**
 * The one empty state of the app: a blob with an icon, a title and one line that says what fills
 * this place, plus at most one action.
 */
@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 32.dp)
      .testTag("empty-state"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    StateBlob(
      icon = icon,
      container = MaterialTheme.colorScheme.surfaceContainerHighest,
      content = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(max = 360.dp),
    )
    if (actionLabel != null && onAction != null) {
      TextButton(onClick = onAction) { Text(actionLabel) }
    }
  }
}
