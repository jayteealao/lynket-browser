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

// Phase 5.2: CustomTabActivity migrated to Jetpack Compose

package arun.com.chromer.browsing.customtabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import arun.com.chromer.shared.Constants.EXTRA_KEY_TOOLBAR_COLOR
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 5.2: Custom Tab Activity migrated to Jetpack Compose
 *
 * Launches Chrome Custom Tabs for URLs. This activity has minimal UI -
 * it just configures and launches the custom tab, then manages lifecycle.
 * As soon as user presses back, this activity gets focus and immediately finishes
 * to avoid showing a "ghost tab".
 */
@AndroidEntryPoint
class CustomTabActivityCompose : ComponentActivity() {

    @Inject
    lateinit var customTabs: CustomTabs

    /**
     * As soon as user presses back, this activity will get focus. We need to kill this activity else
     * user will see a ghost tab.
     */
    private var isLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbarColor = intent.getIntExtra(EXTRA_KEY_TOOLBAR_COLOR, 0)
        val url = intent.dataString

        if (savedInstanceState == null && url != null) {
            customTabs
                .forUrl(url)
                .toolbarColor(toolbarColor)
                .launch()
        } else {
            finish()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isLoaded = true
    }

    override fun onResume() {
        super.onResume()
        if (isLoaded) {
            finish()
        }
    }
}
