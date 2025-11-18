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

// Phase 4.4: AmpResolverActivity migrated to Jetpack Compose

package arun.com.chromer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.ui.theme.ChromerTheme
import kotlinx.coroutines.delay

/**
 * UI state for AMP Resolver screen
 */
sealed class AmpResolverState {
    data object Loading : AmpResolverState()
    data object Found : AmpResolverState()
    data object NotFound : AmpResolverState()
}

/**
 * Screen that shows AMP link resolution progress
 */
@Composable
fun AmpResolverDialog(
    website: Website?,
    onSkip: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var state by remember { mutableStateOf<AmpResolverState>(AmpResolverState.Loading) }

    // Update state when website loads
    LaunchedEffect(website) {
        if (website != null) {
            state = if (website.hasAmp()) {
                AmpResolverState.Found
            } else {
                AmpResolverState.NotFound
            }
            // Auto-dismiss after showing result
            delay(200)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.grabbing_amp_link),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state) {
                    AmpResolverState.Loading -> {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    AmpResolverState.Found -> {
                        Text(
                            text = stringResource(R.string.link_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                    AmpResolverState.NotFound -> {
                        Text(
                            text = stringResource(R.string.link_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.skip))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AmpResolverDialogPreview() {
    ChromerTheme {
        AmpResolverDialog(
            website = null
        )
    }
}
