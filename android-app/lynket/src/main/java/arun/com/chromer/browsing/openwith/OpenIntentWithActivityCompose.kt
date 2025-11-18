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

package arun.com.chromer.browsing.openwith

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import arun.com.chromer.ui.screens.OpenIntentWithScreen
import arun.com.chromer.ui.theme.ChromerTheme

/**
 * Phase 4.3: Open Intent With screen migrated to Jetpack Compose
 *
 * Shows a bottom sheet with apps that can handle the URL.
 * Fully declarative UI using Material3 bottom sheet.
 */
class OpenIntentWithActivityCompose : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val intentData = intent?.data

        setContent {
            ChromerTheme {
                OpenIntentWithScreen(
                    url = intentData,
                    onAppSelected = { activityInfo ->
                        val webSiteIntent = Intent(Intent.ACTION_VIEW, intentData).apply {
                            component = activityInfo.componentName
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(webSiteIntent)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
