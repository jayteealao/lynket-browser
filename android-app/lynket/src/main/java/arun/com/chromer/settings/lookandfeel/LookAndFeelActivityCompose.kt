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

// Phase 7.4: LookAndFeelActivity migrated to Jetpack Compose

package arun.com.chromer.settings.lookandfeel

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
import arun.com.chromer.settings.Preferences
import arun.com.chromer.ui.theme.ChromerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 7.4: Look and Feel Activity migrated to Jetpack Compose
 *
 * Appearance and theme settings:
 * - Theme selection (Light/Dark/Auto)
 * - Toolbar customization
 * - Web Heads appearance
 * - Article reader theme
 */
@AndroidEntryPoint
class LookAndFeelActivityCompose : ComponentActivity() {

    @Inject
    lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                LookAndFeelScreen(
                    preferences = preferences,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookAndFeelScreen(
    preferences: Preferences,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    var appTheme by remember { mutableIntStateOf(preferences.appThemeMode()) }
    var dynamicTheme by remember { mutableStateOf(preferences.dynamicTheme()) }
    var articleTheme by remember { mutableIntStateOf(preferences.articleTheme()) }
    var showNotice by remember { mutableStateOf(!preferences.webHeads()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.look_and_feel)) },
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

            // App Theme Section
            item {
                SectionHeader(title = stringResource(R.string.app_theme))
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.theme_mode),
                    subtitle = when (appTheme) {
                        Preferences.THEME_LIGHT -> stringResource(R.string.theme_light)
                        Preferences.THEME_DARK -> stringResource(R.string.theme_dark)
                        else -> stringResource(R.string.theme_auto)
                    }
                ) {
                    // Theme selection would open a dialog
                }
            }

            item {
                SwitchPreferenceItem(
                    title = stringResource(R.string.dynamic_theme),
                    subtitle = stringResource(R.string.dynamic_theme_description),
                    checked = dynamicTheme,
                    onCheckedChange = { checked ->
                        dynamicTheme = checked
                        preferences.dynamicTheme(checked)
                    }
                )
            }

            // Toolbar Customization Section
            item {
                SectionHeader(title = stringResource(R.string.toolbar_customization))
            }

            item {
                SwitchPreferenceItem(
                    title = stringResource(R.string.colored_toolbar),
                    subtitle = stringResource(R.string.colored_toolbar_description),
                    checked = preferences.coloredToolbar(),
                    onCheckedChange = { checked ->
                        preferences.coloredToolbar(checked)
                    }
                )
            }

            item {
                SwitchPreferenceItem(
                    title = stringResource(R.string.hide_toolbar_on_scroll),
                    subtitle = stringResource(R.string.hide_toolbar_on_scroll_description),
                    checked = preferences.hideToolbarOnScroll(),
                    onCheckedChange = { checked ->
                        preferences.hideToolbarOnScroll(checked)
                    }
                )
            }

            // Article Reader Section
            item {
                SectionHeader(title = stringResource(R.string.article_reader))
            }

            item {
                PreferenceItem(
                    title = stringResource(R.string.article_theme),
                    subtitle = when (articleTheme) {
                        Preferences.THEME_LIGHT -> stringResource(R.string.theme_light)
                        Preferences.THEME_DARK -> stringResource(R.string.theme_dark)
                        Preferences.THEME_BLACK -> stringResource(R.string.theme_black)
                        else -> stringResource(R.string.theme_dark)
                    }
                ) {
                    // Article theme selection would open a dialog
                }
            }

            // Web Heads Appearance (only if Web Heads enabled)
            if (preferences.webHeads()) {
                item {
                    SectionHeader(title = stringResource(R.string.web_heads))
                }

                item {
                    SwitchPreferenceItem(
                        title = stringResource(R.string.web_head_close_on_open),
                        subtitle = stringResource(R.string.web_head_close_on_open_description),
                        checked = preferences.webHeadCloseOnOpen(),
                        onCheckedChange = { checked ->
                            preferences.webHeadCloseOnOpen(checked)
                        }
                    )
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
