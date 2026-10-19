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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.browsing.amp.AmpState

/**
 * The AMP lookup (boards amp-resolver--loading and amp-resolver--error). While it runs: the old
 * "Grabbing AMP link" dialog with the address, a progress bar, the old status line and Skip. When
 * the lookup cannot run because the device is offline: the error pattern with Try again and
 * Open normal page.
 */
@Composable
fun AmpResolverDialog(
  state: AmpState,
  onSkip: () -> Unit,
  onRetry: () -> Unit,
  onOpenNormal: () -> Unit,
  onDismiss: () -> Unit,
) {
  if (state is AmpState.Error) {
    AmpErrorDialog(state.url, onRetry, onOpenNormal, onDismiss)
  } else {
    AmpLoadingDialog(state, onSkip, onDismiss)
  }
}

@Composable
private fun AmpLoadingDialog(state: AmpState, onSkip: () -> Unit, onDismiss: () -> Unit) {
  val status = when (state) {
    is AmpState.Found -> R.string.link_found
    is AmpState.NotFound -> R.string.link_not_found
    else -> R.string.loading
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.grabbing_amp_link)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          displayAddress(state.url),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (state is AmpState.Loading) {
          LinearProgressIndicator(Modifier.fillMaxWidth().testTag("amp-progress"))
        }
        Text(
          stringResource(status),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("amp-status"),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onSkip, enabled = state is AmpState.Loading, modifier = Modifier.testTag("amp-skip")) {
        Text(stringResource(R.string.skip))
      }
    },
    modifier = Modifier.testTag("amp-loading"),
  )
}

@Composable
private fun AmpErrorDialog(url: String, onRetry: () -> Unit, onOpenNormal: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Box(
        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
      }
    },
    title = { Text(stringResource(R.string.amp_error_title), textAlign = TextAlign.Center) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.amp_error_body), style = MaterialTheme.typography.bodyMedium)
        Text(
          displayAddress(url),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("amp-try-again")) {
          Text(stringResource(R.string.retry))
        }
        Button(onClick = onOpenNormal, modifier = Modifier.testTag("amp-open-normal")) {
          Text(stringResource(R.string.amp_open_normal))
        }
      }
    },
    modifier = Modifier.padding(horizontal = 8.dp).testTag("amp-error"),
  )
}

/** The address without its scheme, as the boards show it. */
internal fun displayAddress(url: String): String = url.substringAfter("://")
