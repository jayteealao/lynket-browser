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

// Phase 5.1: BrowserInterceptActivity migrated to Jetpack Compose

package arun.com.chromer.browsing.browserintercept

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import arun.com.chromer.R
import arun.com.chromer.extenstions.finishAndRemoveTaskCompat
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.SafeIntent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 5.1: Browser Intercept Activity migrated to Jetpack Compose
 *
 * Transparent activity that intercepts http/https URLs from other apps
 * and routes them to the appropriate browser provider via TabsManager.
 * No UI - just processes intents and finishes immediately.
 */
@SuppressLint("GoogleAppIndexingApiWarning")
@AndroidEntryPoint
class BrowserInterceptActivityCompose : ComponentActivity() {

    @Inject
    lateinit var tabsManager: TabsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            val safeIntent = SafeIntent(intent)
            if (safeIntent.data == null) {
                invalidLink()
                return
            }
            lifecycleScope.launch {
                tabsManager.processIncomingIntent(this@BrowserInterceptActivityCompose, intent)
                finishAndRemoveTaskCompat()
            }
        } ?: run {
            finishAndRemoveTaskCompat()
        }
    }

    private fun invalidLink() {
        Toast.makeText(this, getString(R.string.unsupported_link), Toast.LENGTH_SHORT).show()
        finishAndRemoveTaskCompat()
    }
}
