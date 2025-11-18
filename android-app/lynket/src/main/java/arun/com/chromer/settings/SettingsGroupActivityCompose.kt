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

// Phase 7.1: SettingsGroupActivity migrated to Jetpack Compose

package arun.com.chromer.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import arun.com.chromer.settings.browsingmode.BrowsingModeActivity
import arun.com.chromer.settings.browsingoptions.BrowsingOptionsActivity
import arun.com.chromer.settings.lookandfeel.LookAndFeelActivity
import arun.com.chromer.ui.screens.SettingsScreen
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.Utils
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase 7.1: Settings Group Activity migrated to Jetpack Compose
 *
 * Main settings screen showing categories:
 * - Browsing Mode
 * - Look & Feel
 * - Browsing Options
 * - Default browser card (if not default)
 */
@AndroidEntryPoint
class SettingsGroupActivityCompose : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                SettingsScreen(
                    onNavigateBack = { finish() },
                    onBrowsingModeClick = {
                        startActivity(Intent(this, BrowsingModeActivity::class.java))
                    },
                    onLookAndFeelClick = {
                        startActivity(Intent(this, LookAndFeelActivity::class.java))
                    },
                    onBrowsingOptionsClick = {
                        startActivity(Intent(this, BrowsingOptionsActivity::class.java))
                    },
                    isDefaultBrowser = Utils.isDefaultBrowser(this)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recreate to update default browser card visibility
        recreate()
    }
}
