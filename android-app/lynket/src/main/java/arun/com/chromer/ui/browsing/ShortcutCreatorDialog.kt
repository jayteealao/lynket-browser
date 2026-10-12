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

package arun.com.chromer.ui.browsing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.shortcuts.ShortcutState

/**
 * The home screen shortcut dialog (board shortcut-creator--default) with the old title and button:
 * the site's icon (progress while it loads), the name field with the old "Name cannot be empty"
 * error, the address, Cancel and Create.
 */
@Composable
fun ShortcutCreatorDialog(
  state: ShortcutState,
  name: String,
  canCreate: Boolean,
  onNameChange: (String) -> Unit,
  onCreate: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.create_shorcut)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(MaterialTheme.shapes.medium)
              .background(MaterialTheme.colorScheme.secondaryContainer)
              .testTag("shortcut-icon"),
            contentAlignment = Alignment.Center,
          ) {
            when (state) {
              ShortcutState.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
              is ShortcutState.Ready -> Image(state.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
            }
          }
          val loading = state == ShortcutState.Loading
          OutlinedTextField(
            value = if (loading) stringResource(R.string.loading) else name,
            onValueChange = onNameChange,
            enabled = !loading,
            singleLine = true,
            label = { Text(stringResource(R.string.shortcut_name)) },
            isError = !loading && name.isBlank(),
            supportingText = if (!loading && name.isBlank()) {
              { Text(stringResource(R.string.name_cannot_be_empty)) }
            } else {
              null
            },
            modifier = Modifier.weight(1f).testTag("shortcut-name"),
          )
        }
        if (state is ShortcutState.Ready) {
          Text(
            displayAddress(state.website.preferredUrl()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("shortcut-url"),
          )
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("shortcut-cancel")) {
        Text(stringResource(android.R.string.cancel))
      }
    },
    confirmButton = {
      Button(onClick = onCreate, enabled = canCreate, modifier = Modifier.testTag("shortcut-create")) {
        Text(stringResource(R.string.create))
      }
    },
    modifier = Modifier.testTag("shortcut-dialog"),
  )
}
