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

// Phase 5.3: WebViewActivity migrated to Jetpack Compose

package arun.com.chromer.browsing.webview

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.screens.WebViewScreen
import arun.com.chromer.ui.theme.ChromerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Phase 5.3: WebView Activity migrated to Jetpack Compose
 *
 * Fallback browser using Android WebView when Custom Tabs are unavailable.
 * Wraps the WebView in Compose with Material3 UI components.
 */
@AndroidEntryPoint
class WebViewActivityCompose : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val url = intent.dataString
        if (url == null) {
            Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val toolbarColor = intent.getIntExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR, 0)

        setContent {
            ChromerTheme {
                WebViewScreen(
                    url = url,
                    toolbarColor = toolbarColor,
                    onNavigateBack = { finish() },
                    onWebsiteLoaded = { loadedUrl, title ->
                        lifecycleScope.launch {
                            // Load website details if needed
                            // This mimics the behavior of BrowsingActivity.loadWebsiteDetails()
                        }
                    }
                )
            }
        }
    }
}
