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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import arun.com.chromer.R
import arun.com.chromer.bubbles.system.BubbleRefusal
import arun.com.chromer.ui.components.showTransientError

/** The sentence that names [this] cause; the page has already opened in a tab. */
@StringRes
fun BubbleRefusal.messageRes(): Int = when (this) {
  BubbleRefusal.NOTIFICATIONS_OFF -> R.string.bubble_refused_notifications_off
  BubbleRefusal.BUBBLES_OFF -> R.string.bubble_refused_bubbles_off
  BubbleRefusal.NOT_SHOWN -> R.string.bubble_refused_not_shown
}

/**
 * Says why the link opened in a tab instead of a bubble, with one action that opens the matching
 * Android settings page. The one transient-error pattern: a snackbar at the bottom, above the
 * navigation bar. [onDone] runs once the snackbar leaves: after the action, or after its timeout,
 * which Android makes longer while an accessibility service such as TalkBack is on.
 */
@Composable
fun BubbleRefusedMessage(
  refusal: BubbleRefusal,
  onOpenSettings: () -> Unit,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  hostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val message = stringResource(refusal.messageRes())
  val action = stringResource(R.string.open_settings)
  val openSettings by rememberUpdatedState(onOpenSettings)
  val done by rememberUpdatedState(onDone)
  LaunchedEffect(refusal) {
    hostState.showTransientError(message, action, onAction = { openSettings() }, duration = SnackbarDuration.Long)
    done()
  }
  SnackbarHost(
    hostState = hostState,
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
      .testTag("bubble-refused"),
  )
}
