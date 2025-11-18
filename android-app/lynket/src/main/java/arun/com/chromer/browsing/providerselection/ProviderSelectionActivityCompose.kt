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

// Phase 8.1: ProviderSelectionActivity migrated to Jetpack Compose

package arun.com.chromer.browsing.providerselection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.browsing.providerselection.ModernProviderSelectionViewModel.ProviderSelectionUiState
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.Utils
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase 8.1: Provider Selection Activity migrated to Jetpack Compose
 *
 * Select Custom Tab provider or WebView fallback.
 * Uses existing ModernProviderSelectionViewModel with Flow-based state.
 */
@AndroidEntryPoint
class ProviderSelectionActivityCompose : ComponentActivity() {

    private val viewModel: ModernProviderSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                ProviderSelectionScreenStandalone(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }

        if (savedInstanceState == null) {
            viewModel.refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSelectionScreenStandalone(
    viewModel: ModernProviderSelectionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showWebViewDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_secondary_browser)) },
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
        when (val state = uiState) {
            is ProviderSelectionUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ProviderSelectionUiState.Success -> {
                ProviderSelectionContent(
                    providers = state.providers,
                    selectedPackage = state.selectedPackage,
                    usingWebView = state.usingWebView,
                    onProviderClick = { provider ->
                        if (provider.installed) {
                            viewModel.selectProvider(provider)
                            onNavigateBack()
                        } else {
                            Utils.openPlayStore(context, provider.packageName)
                        }
                    },
                    onWebViewClick = {
                        showWebViewDialog = true
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is ProviderSelectionUiState.Error -> {
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
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.error),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }

        if (showWebViewDialog) {
            AlertDialog(
                onDismissRequest = { showWebViewDialog = false },
                title = { Text(stringResource(R.string.are_you_sure)) },
                text = { Text(stringResource(R.string.webview_disadvantages)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.selectWebView()
                            showWebViewDialog = false
                            onNavigateBack()
                        }
                    ) {
                        Text(stringResource(android.R.string.yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWebViewDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun ProviderSelectionContent(
    providers: List<Provider>,
    selectedPackage: String?,
    usingWebView: Boolean,
    onProviderClick: (Provider) -> Unit,
    onWebViewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // WebView option card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onWebViewClick),
            colors = CardDefaults.cardColors(
                containerColor = if (usingWebView)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (usingWebView) 4.dp else 2.dp
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
                    imageVector = Icons.Default.Web,
                    contentDescription = stringResource(R.string.webview),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.webview),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.webview_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (usingWebView) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.selected),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        HorizontalDivider()

        // Custom Tab providers section
        Text(
            text = stringResource(R.string.custom_tab_providers),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        if (providers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.no_providers_found),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(providers, key = { it.packageName }) { provider ->
                    ProviderGridItem(
                        provider = provider,
                        isSelected = !usingWebView && provider.packageName == selectedPackage,
                        onClick = { onProviderClick(provider) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderGridItem(
    provider: Provider,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                !provider.installed -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = provider.iconUri,
                contentDescription = provider.appName,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = provider.appName,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                color = if (provider.installed)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSelected) {
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.selected),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (!provider.installed) {
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.install),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
