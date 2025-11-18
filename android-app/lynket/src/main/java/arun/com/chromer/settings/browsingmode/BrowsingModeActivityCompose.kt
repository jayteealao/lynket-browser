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

// Phase 7.3: BrowsingModeActivity migrated to Jetpack Compose

package arun.com.chromer.settings.browsingmode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.ServiceManager
import arun.com.chromer.util.Utils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 7.3: Browsing Mode Activity migrated to Jetpack Compose
 *
 * Select browsing mode:
 * - Custom Tabs (default)
 * - Web Heads (requires overlay permission)
 * - Native Bubbles (Android 10+)
 */
@AndroidEntryPoint
class BrowsingModeActivityCompose : ComponentActivity() {

    @Inject
    lateinit var preferences: Preferences

    @Inject
    lateinit var tabsManager: TabsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                BrowsingModeScreen(
                    preferences = preferences,
                    tabsManager = tabsManager,
                    onNavigateBack = { finish() },
                    onRequestOverlayPermission = {
                        Utils.openDrawOverlaySettings(this)
                    }
                )
            }
        }
    }
}

enum class BrowsingMode {
    CUSTOM_TABS,
    WEB_HEADS,
    NATIVE_BUBBLES
}

data class BrowsingModeOption(
    val mode: BrowsingMode,
    val title: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowsingModeScreen(
    preferences: Preferences,
    tabsManager: TabsManager,
    onNavigateBack: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit = {}
) {
    val context = LocalContext.current

    var currentMode by remember {
        mutableStateOf(
            when {
                preferences.webHeads() -> BrowsingMode.WEB_HEADS
                preferences.nativeBubbles() -> BrowsingMode.NATIVE_BUBBLES
                else -> BrowsingMode.CUSTOM_TABS
            }
        )
    }

    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showNativeBubblesDialog by remember { mutableStateOf(false) }

    val browsingModes = listOf(
        BrowsingModeOption(
            mode = BrowsingMode.CUSTOM_TABS,
            title = stringResource(R.string.custom_tabs),
            description = stringResource(R.string.custom_tabs_description)
        ),
        BrowsingModeOption(
            mode = BrowsingMode.WEB_HEADS,
            title = stringResource(R.string.web_heads),
            description = stringResource(R.string.web_heads_description)
        ),
        BrowsingModeOption(
            mode = BrowsingMode.NATIVE_BUBBLES,
            title = stringResource(R.string.browsing_mode_native_bubbles),
            description = stringResource(R.string.browsing_mode_native_bubbles_description)
        )
    )

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = { Text(stringResource(R.string.permission_required)) },
            text = { Text(stringResource(R.string.overlay_permission_content)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverlayPermissionDialog = false
                        onRequestOverlayPermission()
                    }
                ) {
                    Text(stringResource(R.string.grant))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showNativeBubblesDialog) {
        AlertDialog(
            onDismissRequest = { showNativeBubblesDialog = false },
            title = { Text(stringResource(R.string.browsing_mode_native_bubbles)) },
            text = { Text(stringResource(R.string.browsing_mode_native_bubbles_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNativeBubblesDialog = false
                        tabsManager.openUrl(
                            context,
                            Website("https://github.com/arunkumar9t2/lynket-browser/wiki/Android-10-Bubbles-Guide")
                        )
                    }
                ) {
                    Text(stringResource(R.string.browsing_mode_native_bubbles_guide))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNativeBubblesDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browsing_mode)) },
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
            itemsIndexed(browsingModes) { index, option ->
                BrowsingModeItem(
                    option = option,
                    isSelected = currentMode == option.mode,
                    onSelect = {
                        when (option.mode) {
                            BrowsingMode.CUSTOM_TABS -> {
                                currentMode = BrowsingMode.CUSTOM_TABS
                                preferences.webHeads(false)
                                preferences.nativeBubbles(false)
                                ServiceManager.takeCareOfServices(context)
                            }
                            BrowsingMode.WEB_HEADS -> {
                                if (Utils.isOverlayGranted(context)) {
                                    currentMode = BrowsingMode.WEB_HEADS
                                    preferences.webHeads(true)
                                    preferences.nativeBubbles(false)
                                    ServiceManager.takeCareOfServices(context)
                                } else {
                                    showOverlayPermissionDialog = true
                                }
                            }
                            BrowsingMode.NATIVE_BUBBLES -> {
                                currentMode = BrowsingMode.NATIVE_BUBBLES
                                preferences.webHeads(false)
                                preferences.nativeBubbles(true)
                                ServiceManager.takeCareOfServices(context)
                                showNativeBubblesDialog = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BrowsingModeItem(
    option: BrowsingModeOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        },
        modifier = Modifier.clickable(onClick = onSelect)
    )
    HorizontalDivider()
}
