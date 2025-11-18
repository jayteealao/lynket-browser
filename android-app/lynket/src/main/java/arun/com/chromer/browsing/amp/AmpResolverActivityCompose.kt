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

// Phase 4.4: AmpResolverActivity migrated to Jetpack Compose

package arun.com.chromer.browsing.amp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import arun.com.chromer.browsing.BrowsingViewModel
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.screens.AmpResolverDialog
import arun.com.chromer.ui.theme.ChromerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 4.4: AMP Resolver migrated to Jetpack Compose
 *
 * Shows a dialog while fetching the AMP version of a URL,
 * then launches it automatically.
 */
@AndroidEntryPoint
class AmpResolverActivityCompose : ComponentActivity() {

    @Inject
    lateinit var tabsManager: TabsManager

    private val browsingViewModel: BrowsingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                val website by browsingViewModel.websiteState.collectAsState()

                AmpResolverDialog(
                    website = website,
                    onSkip = { launchUrl(website) },
                    onDismiss = {
                        launchUrl(website)
                        finish()
                    }
                )
            }
        }
    }

    private fun launchUrl(website: Website?) {
        if (website != null) {
            if (website.hasAmp()) {
                tabsManager.openBrowsingTab(this, Website.Ampify(website), fromNewTab = false)
            } else {
                tabsManager.openUrl(this, Website.Ampify(website), fromAmp = true)
            }
        }
    }
}
