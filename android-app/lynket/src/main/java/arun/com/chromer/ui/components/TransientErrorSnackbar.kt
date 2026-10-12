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

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/**
 * The one transient-error pattern: a snackbar with one action. By default it stays until the
 * action is taken or the caller cancels the coroutine (for example when the condition clears).
 */
suspend fun SnackbarHostState.showTransientError(
  message: String,
  actionLabel: String,
  onAction: () -> Unit,
  duration: SnackbarDuration = SnackbarDuration.Indefinite,
) {
  val result = showSnackbar(
    message = message,
    actionLabel = actionLabel,
    withDismissAction = false,
    duration = duration,
  )
  if (result == SnackbarResult.ActionPerformed) onAction()
}
