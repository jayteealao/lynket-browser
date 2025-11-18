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

// Phase 4.3: OpenIntentWithActivity migrated to Jetpack Compose

package arun.com.chromer.ui.screens

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.ui.theme.ChromerTheme
import coil.compose.AsyncImage

/**
 * Screen that shows a bottom sheet for choosing an app to open a URL with
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenIntentWithScreen(
    url: Uri?,
    onAppSelected: (ActivityInfo) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val apps = remember(url) {
        if (url == null) emptyList()
        else {
            val intent = Intent(Intent.ACTION_VIEW, url)
            val packageManager = context.packageManager
            packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .map { it.activityInfo }
        }
    }

    if (url == null || apps.isEmpty()) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.open_with),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(apps) { activityInfo ->
                    AppListItem(
                        activityInfo = activityInfo,
                        onClick = { onAppSelected(activityInfo) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AppListItem(
    activityInfo: ActivityInfo,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = activityInfo.loadIcon(packageManager),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activityInfo.loadLabel(packageManager).toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activityInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OpenIntentWithScreenPreview() {
    ChromerTheme {
        // Preview shows empty state
        Box(modifier = Modifier.fillMaxSize())
    }
}
