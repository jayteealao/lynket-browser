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

// Phase 7.1: Settings screens migrated to Jetpack Compose

package arun.com.chromer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.Utils

/**
 * Phase 7.1: Settings screens migrated to Jetpack Compose
 *
 * Main settings screen with categories and default browser card.
 */

data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onBrowsingModeClick: () -> Unit = {},
    onLookAndFeelClick: () -> Unit = {},
    onBrowsingOptionsClick: () -> Unit = {},
    isDefaultBrowser: Boolean = false
) {
    val context = LocalContext.current

    val settingsCategories = remember {
        listOf(
            SettingsCategory(
                title = context.getString(R.string.browsing_mode),
                subtitle = context.getString(R.string.browsing_mode_subtitle),
                onClick = onBrowsingModeClick
            ),
            SettingsCategory(
                title = context.getString(R.string.look_and_feel),
                subtitle = context.getString(R.string.look_and_feel_subtitle),
                onClick = onLookAndFeelClick
            ),
            SettingsCategory(
                title = context.getString(R.string.browsing_options),
                subtitle = context.getString(R.string.browsing_options_subtitle),
                onClick = onBrowsingOptionsClick
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_close)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Default Browser Card (only show if not default)
            if (!isDefaultBrowser) {
                item {
                    DefaultBrowserCard(
                        onClick = {
                            val defaultBrowser = Utils.getDefaultBrowserPackage(context)
                            if (defaultBrowser.equals("android", ignoreCase = true) ||
                                defaultBrowser.startsWith("org.cyanogenmod") ||
                                defaultBrowser.equals("com.huawei.android.internal.app", ignoreCase = true)
                            ) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.GOOGLE_URL)))
                            } else {
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.parse("package:$defaultBrowser")
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            // Settings Categories
            items(settingsCategories) { category ->
                SettingsCategoryItem(
                    category = category,
                    onClick = category.onClick
                )
            }
        }
    }
}

@Composable
private fun DefaultBrowserCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.set_default_browser),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.set_default_browser_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
