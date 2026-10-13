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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.ui.theme.readableContentColor

/** The primary colours of the old colour chooser (MaterialDialogs `ColorPalette.PRIMARY_COLORS`). */
val PrimaryPalette: List<Int> = listOf(
  0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4,
  0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107,
  0xFFFF9800, 0xFFFF5722, 0xFF795548, 0xFF9E9E9E, 0xFF607D8B,
).map { it.toInt() }

/** `#RRGGBB` of [colour], without alpha, as the old colour rows showed it. */
fun hexOf(colour: Int): String = String.format("#%06X", 0xFFFFFF and colour)

/** The opaque colour of a `#RRGGBB` text, or null when the text is not one. */
fun parseHex(text: String): Int? {
  val digits = text.trim().removePrefix("#")
  if (digits.length != 6 || digits.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
  return (0xFF000000 or digits.toLong(16)).toInt()
}

/**
 * The colour sheet: the primary palette of the old chooser and a hex field. Done returns the chosen
 * opaque colour; it is disabled while the hex text is not a `#RRGGBB` colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
  title: String,
  initial: Int,
  onDone: (Int) -> Unit,
  onDismiss: () -> Unit,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag("colour-sheet")) {
    ColorPickerContent(title, initial, onDone)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerContent(title: String, initial: Int, onDone: (Int) -> Unit) {
  var hex by rememberSaveable { mutableStateOf(hexOf(initial)) }
  val parsed = remember(hex) { parseHex(hex) }
  Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(
      stringResource(R.string.color_sheet_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxWidth().selectableGroup(),
    ) {
      PrimaryPalette.forEach { colour ->
        val selected = parsed != null && (parsed and 0xFFFFFF) == (colour and 0xFFFFFF)
        Swatch(colour, selected) { hex = hexOf(colour) }
      }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
      OutlinedTextField(
        value = hex,
        onValueChange = { hex = it.take(7) },
        label = { Text(stringResource(R.string.color_hex_label)) },
        singleLine = true,
        isError = parsed == null,
        supportingText = if (parsed == null) {
          { Text(stringResource(R.string.color_hex_error)) }
        } else null,
        modifier = Modifier.weight(1f).testTag("colour-hex"),
      )
      Button(onClick = { parsed?.let(onDone) }, enabled = parsed != null, modifier = Modifier.testTag("colour-done")) {
        Text(stringResource(R.string.done))
      }
    }
  }
}

@Composable
private fun Swatch(colour: Int, selected: Boolean, onClick: () -> Unit) {
  val fill = Color(colour)
  val label = hexOf(colour)
  val description = if (selected) stringResource(R.string.color_selected, label) else label
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .size(48.dp)
      // Selectable on the full 48 dp; an outline keeps pale swatches visible on a light sheet.
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
      .padding(if (selected) 5.dp else 0.dp)
      .background(fill, CircleShape)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
      .semantics { contentDescription = description },
  ) {
    if (selected) {
      Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = fill.readableContentColor(),
      )
    }
  }
}
