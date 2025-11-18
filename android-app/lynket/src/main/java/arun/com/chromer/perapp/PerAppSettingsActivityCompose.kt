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

// Phase 7.2: PerAppSettingsActivity migrated to Jetpack Compose

package arun.com.chromer.perapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.ServiceManager
import arun.com.chromer.util.Utils
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 7.2: Per-App Settings Activity migrated to Jetpack Compose
 *
 * Configure app-specific settings:
 * - Blacklist apps from opening in Lynket
 * - Per-app incognito mode
 * - Requires usage access permission
 */
@AndroidEntryPoint
class PerAppSettingsActivityCompose : ComponentActivity() {

    @Inject
    lateinit var preferences: Preferences

    private val viewModel: PerAppSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                PerAppSettingsScreen(
                    viewModel = viewModel,
                    preferences = preferences,
                    onNavigateBack = { finish() },
                    onRequestUsagePermission = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }
        }

        // Load apps on create
        if (savedInstanceState == null) {
            viewModel.loadApps()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppSettingsScreen(
    viewModel: PerAppSettingsViewModel,
    preferences: Preferences,
    onNavigateBack: () -> Unit = {},
    onRequestUsagePermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val isLoading by viewModel.loadingState.collectAsStateWithLifecycle(initialValue = false)
    val apps by viewModel.appsState.collectAsStateWithLifecycle(initialValue = emptyList())

    // Listen for app updates and update the list
    LaunchedEffect(Unit) {
        viewModel.appUpdateFlow.collect { (index, updatedApp) ->
            // State will be updated through appsState flow
        }
    }

    var perAppEnabled by remember {
        mutableStateOf(preferences.perAppSettings() && Utils.canReadUsageStats(context))
    }
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.permission_required)) },
            text = { Text(stringResource(R.string.usage_permission_explanation_per_apps)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        onRequestUsagePermission()
                    }
                ) {
                    Text(stringResource(R.string.grant))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.per_app_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_close)
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.enable),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = perAppEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !Utils.canReadUsageStats(context)) {
                                    showPermissionDialog = true
                                } else {
                                    perAppEnabled = checked
                                    preferences.perAppSettings(checked)
                                    ServiceManager.takeCareOfServices(context)
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.no_apps_found),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = { viewModel.loadApps() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(apps) { app ->
                    PerAppListItem(
                        app = app,
                        onBlacklistToggle = { blacklisted ->
                            viewModel.blacklist(Pair(app.packageName, blacklisted))
                        },
                        onIncognitoToggle = { incognito ->
                            viewModel.incognito(Pair(app.packageName, incognito))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PerAppListItem(
    app: arun.com.chromer.data.apps.model.App,
    onBlacklistToggle: (Boolean) -> Unit,
    onIncognitoToggle: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Show current settings
                    if (app.blackListed || app.incognito) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            if (app.blackListed) {
                                Text(
                                    text = stringResource(R.string.blacklisted),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (app.incognito) {
                                Text(
                                    text = stringResource(R.string.incognito),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            leadingContent = {
                AsyncImage(
                    model = app.packageName,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            },
            modifier = Modifier.clickable { expanded = !expanded }
        )

        // Expandable settings section
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 16.dp, bottom = 8.dp)
            ) {
                // Blacklist toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.blacklist),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.blacklist_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = app.blackListed,
                        onCheckedChange = onBlacklistToggle
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Incognito toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.incognito),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.incognito_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = app.incognito,
                        onCheckedChange = onIncognitoToggle
                    )
                }
            }
        }

        HorizontalDivider()
    }
}
