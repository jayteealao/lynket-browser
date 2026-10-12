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

package arun.com.chromer.browsing.newtab

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.shared.base.activity.BaseActivity
import arun.com.chromer.ui.browsing.NewTabDialog
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint

/**
 * New tab: the static shortcut, the bottom bar and the Lynket options open it. One Compose dialog
 * with a search field; the typed address or search opens as a new tab.
 */
@AndroidEntryPoint
class NewTabDialogActivity : BaseActivity() {

  private val viewModel: NewTabViewModel by viewModels()

  override val layoutRes: Int
    get() = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      ChromerThemeFromSettings {
        val query by viewModel.query.collectAsStateWithLifecycle()
        val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
        NewTabDialog(
          query = query,
          suggestions = suggestions,
          onQueryChange = viewModel::onQueryChange,
          onSubmit = { text -> viewModel.submit(this, text, onOpened = ::finish) },
          onVoiceUnavailable = {
            Toast.makeText(this, R.string.no_voice_rec_apps, Toast.LENGTH_SHORT).show()
          },
          onDismiss = ::finish,
        )
      }
    }
  }
}
