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

// Phase 5.4: HomeActivity migrated to Jetpack Compose

package arun.com.chromer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.HomeActivityViewModel
import arun.com.chromer.home.epoxycontroller.model.CustomTabProviderInfo
import coil.compose.AsyncImage
import dev.arunkumar.android.common.Resource

/**
 * Phase 5.4: Home screen migrated to Jetpack Compose
 *
 * Main launcher screen showing:
 * - Search bar for entering URLs
 * - Provider info (which browser is being used)
 * - Recent browsing history
 * - Settings and tips buttons
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeActivityViewModel,
    onSearchPerformed: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onTipsClick: () -> Unit = {},
    onWebsiteClick: (Website) -> Unit = {},
    onProviderClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val providerInfo by viewModel.providerInfoLiveData.observeAsState()
    val recents by viewModel.recentsLiveData.observeAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onTipsClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lightbulb_outline),
                            contentDescription = stringResource(R.string.tips),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_or_url)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search)
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (searchQuery.isNotBlank()) {
                                keyboardController?.hide()
                                onSearchPerformed(searchQuery)
                                searchQuery = ""
                            }
                        }
                    ),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
            }

            // Provider Info Section
            providerInfo?.let { provider ->
                item {
                    ProviderInfoCard(
                        providerInfo = provider,
                        onClick = onProviderClick
                    )
                }
            }

            // Recents Section
            item {
                Text(
                    text = stringResource(R.string.recently_opened),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            when (val recentsResource = recents) {
                is Resource.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is Resource.Success -> {
                    val websites = recentsResource.data
                    if (websites.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.no_recent_history),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(websites) { website ->
                            WebsiteListItem(
                                website = website,
                                onClick = { onWebsiteClick(website) }
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    item {
                        Text(
                            text = stringResource(R.string.error_loading_recents),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                null -> {
                    // Loading state - show placeholder
                    item {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderInfoCard(
    providerInfo: CustomTabProviderInfo,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = providerInfo.allowChange, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider Icon
            AsyncImage(
                model = providerInfo.iconUri,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                // Provider Description
                Text(
                    text = providerInfo.providerDescription.resolve(context),
                    style = MaterialTheme.typography.bodyLarge
                )

                // Provider Reason (if any)
                if (providerInfo.providerReason.stringRes != 0) {
                    Text(
                        text = providerInfo.providerReason.resolve(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WebsiteListItem(
    website: Website,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = website.safeLabel(),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = website.url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            AsyncImage(
                model = website.faviconUrl ?: R.drawable.ic_launcher,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
