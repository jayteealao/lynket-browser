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

// Phase 7.5: BrowsingOptionsActivity migrated to Jetpack Compose

package arun.com.chromer.settings.browsingoptions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import arun.com.chromer.R
import arun.com.chromer.browsing.providerselection.ProviderSelectionActivity
import arun.com.chromer.settings.Preferences
import arun.com.chromer.ui.theme.ChromerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 7.5: Browsing Options Activity migrated to Jetpack Compose
 *
 * Browsing behavior settings:
 * - Custom tabs provider selection
 * - Secondary browser selection
 * - Favorite share app selection
 * - Bottom bar behavior
 * - Opening behavior
 */
@AndroidEntryPoint
class BrowsingOptionsActivityCompose : ComponentActivity() {

    @Inject
    lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                BrowsingOptionsScreen(
                    preferences = preferences,
                    onNavigateBack = { finish() },
                    onCustomTabProviderClick = {
                        startActivity(Intent(this, ProviderSelectionActivity::class.java))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowsingOptionsScreen(
    preferences: Preferences,
    onNavigateBack: () -> Unit = {},
    onCustomTabProviderClick: () -> Unit = {}
) {
    val context = LocalContext.current

    var showNotice by remember { mutableStateOf(!preferences.webHeads()) }
    var prefetch by remember { mutableStateOf(preferences.prefetchEnabled()) }
    var aggressiveLoading by remember { mutableStateOf(preferences.aggressiveLoading()) }
    var ampMode by remember { mutableStateOf(preferences.ampMode()) }
    var bottomBar by remember { mutableStateOf(preferences.bottomBar()) }
    var openInBackground by remember { mutableStateOf(preferences.openInBackground()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browsing_options)) },
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
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Notice about Web Heads
            if (showNotice) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.web_heads_disabled_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Providers Section
            item {
                SectionHeader(title = stringResource(R.string.providers))
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.custom_tab_provider),
                    subtitle = stringResource(R.string.custom_tab_provider_description),
                    onClick = onCustomTabProviderClick
                )
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.secondary_browser),
                    subtitle = stringResource(R.string.secondary_browser_description),
                    onClick = { /* TODO: Show browser picker */ }
                )
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.fav_share_app),
                    subtitle = stringResource(R.string.fav_share_app_description),
                    onClick = { /* TODO: Show share app picker */ }
                )
            }

            // Opening Behavior Section
            item {
                SectionHeader(title = stringResource(R.string.opening_behavior))
            }

            item {
                SwitchPreferenceItem(
                    title = stringResource(R.string.prefetch),
                    subtitle = stringResource(R.string.prefetch_description),
                    checked = prefetch,
                    onCheckedChange = { checked ->
                        prefetch = checked
                        preferences.prefetchEnabled(checked)
                    }
                )
            }

            item {
                SwitchPreferenceItem(
                    title = stringResource(R.string.aggressive_loading),
                    subtitle = stringResource(R.string.aggressive_loading_description),
                    checked = aggressiveLoading,
                    onCheckedChange = { checked ->
                        aggressiveLoading = checked
                        preferences.aggressiveLoading(checked)
                    }
                )
            }

            item {
                SwitchPreferenceItem(
                    title = stringResource(R.string.amp_mode),
                    subtitle = stringResource(R.string.amp_mode_description),
                    checked = ampMode,
                    onCheckedChange = { checked ->
                        ampMode = checked
                        preferences.ampMode(checked)
                    }
                )
            }

            item {
                SwitchPreferenceItem(
                    title = stringResource(R.string.open_in_background),
                    subtitle = stringResource(R.string.open_in_background_description),
                    checked = openInBackground,
                    onCheckedChange = { checked ->
                        openInBackground = checked
                        preferences.openInBackground(checked)
                    }
                )
            }

            // Bottom Bar Section
            item {
                SectionHeader(title = stringResource(R.string.bottom_bar))
            }

            item {
                SwitchPreferenceItem(
                    title = stringResource(R.string.bottom_bar),
                    subtitle = stringResource(R.string.bottom_bar_description),
                    checked = bottomBar,
                    onCheckedChange = { checked ->
                        bottomBar = checked
                        preferences.bottomBar(checked)
                    }
                )
            }

            // Bottom Bar Actions Explanation
            if (bottomBar) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.bottom_bar_actions),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            BottomActionItem(
                                icon = "➕",
                                description = stringResource(R.string.new_tab_action_explanation)
                            )
                            BottomActionItem(
                                icon = "↗",
                                description = stringResource(R.string.share_action_explanation)
                            )
                            BottomActionItem(
                                icon = "📄",
                                description = stringResource(R.string.bottom_bar_article_mode_explanation)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun PreferenceItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
    HorizontalDivider()
}

@Composable
private fun SwitchPreferenceItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
    HorizontalDivider()
}

@Composable
private fun BottomActionItem(
    icon: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
