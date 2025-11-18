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

// Phase 4.1: TipsActivity migrated to Jetpack Compose

package arun.com.chromer.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.Utils
import coil.compose.AsyncImage
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial

/**
 * Data class representing a tip item
 */
data class Tip(
    val titleRes: Int,
    val subtitleRes: Int,
    val imageRes: Int,
    val icon: CommunityMaterial.Icon
)

/**
 * Tips screen showing helpful usage tips for Lynket Browser
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val tips = remember { getTipsList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tips)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tips) { tip ->
                TipCard(tip = tip)
            }
        }
    }
}

/**
 * Individual tip card component
 */
@Composable
fun TipCard(tip: Tip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Icon and Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Image(
                    asset = tip.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        MaterialTheme.colorScheme.primary
                    )
                )

                // Title
                Text(
                    text = stringResource(tip.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = stringResource(tip.subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Image
            AsyncImage(
                model = tip.imageRes,
                contentDescription = stringResource(tip.titleRes),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * Get the list of tips based on Android version
 */
@Composable
private fun remember(calculation: () -> List<Tip>): List<Tip> {
    return androidx.compose.runtime.remember { calculation() }
}

private fun getTipsList(): List<Tip> {
    val tips = mutableListOf<Tip>()

    // Provider tip
    tips.add(
        Tip(
            titleRes = R.string.choose_provider,
            subtitleRes = R.string.choose_provider_tip,
            imageRes = R.drawable.tips_providers,
            icon = CommunityMaterial.Icon.cmd_cards
        )
    )

    // Secondary browser tip
    tips.add(
        Tip(
            titleRes = R.string.choose_secondary_browser,
            subtitleRes = R.string.tips_secondary_browser,
            imageRes = R.drawable.tip_secondary_browser,
            icon = CommunityMaterial.Icon.cmd_earth
        )
    )

    // Per-app settings tip
    tips.add(
        Tip(
            titleRes = R.string.per_app_settings,
            subtitleRes = R.string.per_app_settings_explanation,
            imageRes = R.drawable.tips_per_app_settings,
            icon = CommunityMaterial.Icon.cmd_apps
        )
    )

    // Bottom bar tip (Lollipop+)
    if (Utils.ANDROID_LOLLIPOP) {
        tips.add(
            Tip(
                titleRes = R.string.bottom_bar,
                subtitleRes = R.string.tips_bottom_bar,
                imageRes = R.drawable.tips_bottom_bar,
                icon = CommunityMaterial.Icon.cmd_drag_horizontal
            )
        )
    }

    // Article mode tip
    tips.add(
        Tip(
            titleRes = R.string.article_mode,
            subtitleRes = R.string.tips_article_mode,
            imageRes = R.drawable.tips_article_keywords,
            icon = CommunityMaterial.Icon.cmd_file_document
        )
    )

    // Quick settings tip (Android N+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        tips.add(
            Tip(
                titleRes = R.string.quick_settings,
                subtitleRes = R.string.quick_settings_tip,
                imageRes = R.drawable.tips_quick_settings,
                icon = CommunityMaterial.Icon.cmd_settings
            )
        )
    }

    return tips
}

@Preview(showBackground = true)
@Composable
fun TipsScreenPreview() {
    ChromerTheme {
        TipsScreen()
    }
}
