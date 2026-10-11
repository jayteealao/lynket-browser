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

package arun.com.chromer.ui.permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import arun.com.chromer.R

/**
 * Says why a permission is needed before Android asks for it. "Grant" moves on to the system
 * screen or prompt; "Not now" changes nothing.
 */
@Composable
fun PermissionExplainerDialog(
  icon: ImageVector,
  title: String,
  body: String,
  onGrant: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
    title = { Text(title) },
    text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = { TextButton(onClick = onGrant) { Text(stringResource(R.string.grant)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
  )
}
