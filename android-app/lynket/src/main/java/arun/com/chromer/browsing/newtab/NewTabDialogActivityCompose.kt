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

// Phase 4.6: NewTabDialogActivity migrated to Jetpack Compose

package arun.com.chromer.browsing.newtab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.screens.NewTabScreen
import arun.com.chromer.ui.theme.ChromerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 4.6: New Tab Dialog Activity migrated to Jetpack Compose
 *
 * Shows a bottom sheet dialog for entering a URL to open in a new tab.
 * Replaces MaterialSearchView RxJava implementation with Compose.
 */
@AndroidEntryPoint
class NewTabDialogActivityCompose : ComponentActivity() {

    @Inject
    lateinit var tabsManager: TabsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                NewTabScreen(
                    onUrlSubmit = { url ->
                        launchUrl(url)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun launchUrl(url: String) {
        tabsManager.openUrl(
            this,
            website = Website(url),
            fromApp = true,
            fromWebHeads = false,
            fromNewTab = true
        )
    }
}
