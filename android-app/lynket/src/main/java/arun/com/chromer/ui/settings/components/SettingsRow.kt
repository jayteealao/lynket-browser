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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arun.com.chromer.ui.theme.ChromerTheme

/** The trailing part of a [SettingsRow]. */
sealed interface RowControl {
  data object None : RowControl
  data class Switch(val checked: Boolean) : RowControl
  data class Checkbox(val checked: Boolean) : RowControl
  data class Radio(val selected: Boolean) : RowControl
  data class Colour(val colour: Color, val hex: String) : RowControl

  /** A row that opens another screen. */
  data object Chevron : RowControl
}

/** Leading icon of a row: the glyph in a tinted 40 dp disc. */
data class RowIcon(val icon: ImageVector, val container: Color? = null, val tint: Color? = null)

/**
 * The one settings row: optional leading icon, title, summary and a trailing switch, checkbox,
 * radio button, colour swatch or arrow. The whole row is one touch target of at least 56 dp and one
 * accessibility node that states its role and its on, off or selected state. A disabled row keeps
 * its place, takes no input and is drawn at 38% opacity.
 */
@Composable
fun SettingsRow(
  title: String,
  modifier: Modifier = Modifier,
  summary: AnnotatedString? = null,
  icon: RowIcon? = null,
  control: RowControl = RowControl.None,
  enabled: Boolean = true,
  indent: Boolean = false,
  summaryColour: Color = Color.Unspecified,
  onClick: (() -> Unit)? = null,
) {
  val interaction = when {
    onClick == null -> Modifier
    control is RowControl.Switch -> Modifier.toggleable(control.checked, enabled, Role.Switch) { onClick() }
    control is RowControl.Checkbox -> Modifier.toggleable(control.checked, enabled, Role.Checkbox) { onClick() }
    control is RowControl.Radio -> Modifier.selectable(control.selected, enabled, Role.RadioButton, onClick = onClick)
    else -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
  }
  val colourState = (control as? RowControl.Colour)?.hex
  ListItem(
    modifier = modifier
      .defaultMinSize(minHeight = 56.dp)
      .then(interaction)
      .then(if (colourState != null) Modifier.semantics { stateDescription = colourState } else Modifier)
      .alpha(if (enabled) 1f else 0.38f)
      .padding(start = if (indent) 40.dp else 0.dp),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
    supportingContent = summary?.let {
      { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (summaryColour == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else summaryColour) }
    },
    leadingContent = icon?.let { { LeadingIcon(it) } },
    trailingContent = trailing(control, enabled),
  )
}

@Composable
private fun LeadingIcon(icon: RowIcon) {
  Box(
    modifier = Modifier
      .size(40.dp)
      .background(icon.container ?: MaterialTheme.colorScheme.primaryContainer, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon.icon, contentDescription = null, tint = icon.tint ?: MaterialTheme.colorScheme.onPrimaryContainer)
  }
}

/** The trailing control draws state only; the row owns the input, so the control has no handler. */
private fun trailing(control: RowControl, enabled: Boolean): (@Composable () -> Unit)? = when (control) {
  RowControl.None -> null
  is RowControl.Switch -> { { Switch(checked = control.checked, onCheckedChange = null, enabled = enabled) } }
  is RowControl.Checkbox -> { { Checkbox(checked = control.checked, onCheckedChange = null, enabled = enabled) } }
  is RowControl.Radio -> { { RadioButton(selected = control.selected, onClick = null, enabled = enabled) } }
  is RowControl.Colour -> { {
    Box(
      Modifier
        .size(32.dp)
        .background(control.colour, CircleShape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        .testTag("colour-swatch"),
    )
  } }
  RowControl.Chevron -> { { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) } }
}

@Preview
@Composable
private fun SettingsRowPreview() {
  ChromerTheme {
    Row {
      SettingsRow(
        title = "Default toolbar colour",
        summary = AnnotatedString("#3F51B5"),
        icon = RowIcon(Icons.Outlined.Palette),
        control = RowControl.Colour(Color(0xFF3F51B5), "#3F51B5"),
        onClick = {},
      )
    }
  }
}
