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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The one error state of the app: a blob in errorContainer, a title, a body, an outlined Retry
 * (only where retrying can help) and a filled primary action. [compact] fits it inside a card.
 */
@Composable
fun ErrorState(
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  icon: ImageVector = Icons.Outlined.ErrorOutline,
  retryLabel: String? = null,
  onRetry: (() -> Unit)? = null,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  compact: Boolean = false,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 16.dp else 32.dp)
      .testTag("error-state"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    StateBlob(
      icon = icon,
      container = MaterialTheme.colorScheme.errorContainer,
      content = MaterialTheme.colorScheme.onErrorContainer,
      size = if (compact) 64.dp else 96.dp,
    )
    Text(
      text = title,
      style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (retryLabel != null && onRetry != null) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(retryLabel) }
      }
      if (actionLabel != null && onAction != null) {
        Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
      }
    }
  }
}
