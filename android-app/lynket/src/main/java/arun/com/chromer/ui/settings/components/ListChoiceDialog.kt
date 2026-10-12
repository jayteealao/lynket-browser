/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One choice of a list setting: the stored value and its label. */
data class ListChoice<T>(val value: T, val label: String)

/**
 * The single-choice dialog of a list setting (preferred action, animation, speed, web head spawn
 * and size, article theme, minimize behaviour). A tap on a choice picks it and closes the dialog,
 * as the old list dialogs did.
 */
@Composable
fun <T> ListChoiceDialog(
  title: String,
  choices: List<ListChoice<T>>,
  selected: T,
  onSelect: (T) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("list-dialog"),
    title = { Text(title) },
    text = {
      Column(Modifier.selectableGroup()) {
        choices.forEach { choice ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .selectable(choice.value == selected, role = Role.RadioButton) { onSelect(choice.value) },
          ) {
            RadioButton(selected = choice.value == selected, onClick = null)
            Text(choice.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
    },
  )
}
