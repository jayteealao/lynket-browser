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

package arun.com.chromer.tips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import arun.com.chromer.ui.screens.TipsScreen
import arun.com.chromer.ui.theme.ChromerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase 4.1: Tips screen migrated to Jetpack Compose
 *
 * Shows helpful tips about using Lynket Browser features.
 * Fully declarative UI using Material3 and Coil for images.
 */
@AndroidEntryPoint
class TipsActivityCompose : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                TipsScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}
