/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.browsing.amp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import arun.com.chromer.browsing.BrowsingActivity
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.browsing.AmpResolverDialog
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * With AMP mode on, a link comes here first: one Compose dialog looks up the page's AMP version and
 * opens it, or opens the normal page when there is none. When the lookup cannot run (offline), the
 * dialog offers Try again and Open normal page.
 */
@AndroidEntryPoint
class AmpResolverActivity : BrowsingActivity() {

  @Inject
  lateinit var tabsManager: TabsManager

  private val viewModel: AmpResolverViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val url = intent?.dataString ?: return // BrowsingActivity already showed "unsupported link" and finished.
    viewModel.start(url, incognito)
    setContent {
      ChromerThemeFromSettings {
        val state by viewModel.state.collectAsStateWithLifecycle()
        state?.let {
          AmpResolverDialog(
            state = it,
            onSkip = { openNormal(Website(it.url)) },
            onRetry = viewModel::retry,
            onOpenNormal = { openNormal(Website(it.url)) },
            onDismiss = ::finish,
          )
        }
      }
    }
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { state ->
          when (state) {
            is AmpState.Found -> {
              tabsManager.openBrowsingTab(this@AmpResolverActivity, Website.Ampify(state.website), fromNewTab = false, incognito = incognito)
              finish()
            }
            is AmpState.NotFound -> {
              // As before: "AMP link not found" shows briefly, then the normal page opens.
              delay(NOT_FOUND_DELAY_MS)
              openNormal(state.website)
            }
            else -> Unit
          }
        }
      }
    }
  }

  /** Opens the page itself, past the AMP step (the browsing mode still applies). */
  private fun openNormal(website: Website) {
    tabsManager.openUrl(this, Website.Ampify(website), fromAmp = true, incognito = incognito)
    finish()
  }

  override fun onWebsiteLoaded(website: Website) {
    // The lookup runs in AmpResolverViewModel.
  }

  private companion object {
    const val NOT_FOUND_DELAY_MS = 200L
  }
}
