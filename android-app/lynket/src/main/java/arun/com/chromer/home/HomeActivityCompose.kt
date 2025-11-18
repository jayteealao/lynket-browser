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

package arun.com.chromer.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import arun.com.chromer.about.changelog.Changelog
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.intro.ChromerIntroActivity
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.SettingsGroupActivity
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tips.TipsActivity
import arun.com.chromer.ui.screens.HomeScreen
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.events.Event
import arun.com.chromer.util.events.EventBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 5.4: Home Activity migrated to Jetpack Compose
 *
 * Main launcher activity showing search bar, provider info, and recent history.
 * Replaces Epoxy RecyclerView and MaterialSearchView with Compose UI.
 */
@AndroidEntryPoint
class HomeActivityCompose : ComponentActivity() {

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var tabsManager: TabsManager

    private val viewModel: HomeActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if first run
        if (Preferences.get(this).isFirstRun) {
            startActivity(Intent(this, ChromerIntroActivity::class.java))
        }

        // Show changelog if needed
        Changelog.conditionalShow(this)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Setup event listeners
        setupEventListeners()

        setContent {
            ChromerTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onSearchPerformed = { url ->
                        tabsManager.openUrl(this, Website(url))
                    },
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsGroupActivity::class.java))
                    },
                    onTipsClick = {
                        startActivity(Intent(this, TipsActivity::class.java))
                    },
                    onWebsiteClick = { website ->
                        tabsManager.openUrl(this, website)
                    },
                    onProviderClick = {
                        // Open provider selection
                        startActivity(Intent(this, SettingsGroupActivity::class.java))
                    }
                )
            }
        }
    }

    private fun setupEventListeners() {
        lifecycleScope.launch {
            eventBus.observe<Event.TabEvent.FinishNonBrowsingActivities>()
                .collect {
                    finish()
                }
        }
    }
}
