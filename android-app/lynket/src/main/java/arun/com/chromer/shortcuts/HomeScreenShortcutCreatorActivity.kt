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

package arun.com.chromer.shortcuts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.browsing.BrowsingActivity
import arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.ui.browsing.ShortcutCreatorDialog
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint

/**
 * "Add to Home Screen": a Compose dialog that pins a launcher shortcut for the page. The shortcut's
 * id is the page address and it opens the page through [BrowserInterceptActivity], as every pinned
 * shortcut made by earlier releases does.
 */
@AndroidEntryPoint
class HomeScreenShortcutCreatorActivity : BrowsingActivity() {

  private val viewModel: ShortcutCreatorViewModel by viewModels()

  override val layoutRes: Int get() = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val url = intent?.dataString ?: return // BrowsingActivity already showed "unsupported link" and finished.
    viewModel.start(url)
    setContent {
      ChromerThemeFromSettings {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val name by viewModel.name.collectAsStateWithLifecycle()
        ShortcutCreatorDialog(
          state = state,
          name = name.orEmpty(),
          canCreate = viewModel.canCreate(state, name),
          onNameChange = viewModel::onNameChange,
          onCreate = {
            (state as? ShortcutState.Ready)?.let { pin(it, name.orEmpty().trim()) }
            finish()
          },
          onDismiss = ::finish,
        )
      }
    }
  }

  private fun pin(ready: ShortcutState.Ready, label: String) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) return
    val website = ready.website
    ShortcutManagerCompat.requestPinShortcut(
      this,
      ShortcutInfoCompat.Builder(this, website.url)
        .setIcon(IconCompat.createWithBitmap(ready.icon))
        .setIntent(
          Intent(this, BrowserInterceptActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            data = Uri.parse(website.preferredUrl())
          },
        )
        .setLongLabel(label)
        .setShortLabel(label)
        .build(),
      null,
    )
  }

  override fun onWebsiteLoaded(website: Website) {
    // The site and its icon load in ShortcutCreatorViewModel.
  }
}
