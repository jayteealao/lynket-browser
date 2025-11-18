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

// Phase 4.5: AboutAppActivity migrated to Jetpack Compose

package arun.com.chromer.about

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import arun.com.chromer.ui.screens.AboutScreen
import arun.com.chromer.ui.theme.ChromerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase 4.5: About App Activity migrated to Jetpack Compose
 *
 * Displays information about the app, author, and credits.
 */
@AndroidEntryPoint
class AboutAppActivityCompose : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                AboutScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}
