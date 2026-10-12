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

package arun.com.chromer.browsing.openwith

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import arun.com.chromer.R
import arun.com.chromer.browsing.customtabs.browsingUrl
import arun.com.chromer.shared.base.activity.BaseActivity
import arun.com.chromer.ui.settings.components.AppPickerSheet
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint

/** "Open with…": a sheet of the apps that open the page (Lynket excluded); a pick opens it there. */
@AndroidEntryPoint
class OpenIntentWithActivity : BaseActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val url = intent.browsingUrl()
    if (url == null) {
      Toast.makeText(this, getString(R.string.invalid_link), Toast.LENGTH_SHORT).show()
      finish()
      return
    }
    val webSiteIntent = Intent(ACTION_VIEW, Uri.parse(url))
    setContent {
      ChromerThemeFromSettings {
        AppPickerSheet(
          title = getString(R.string.open_with),
          subtitle = url,
          intent = webSiteIntent,
          onPick = { app ->
            startActivity(
              Intent(webSiteIntent).apply {
                component = app.component
                flags = FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK
              },
            )
            finish()
          },
          onDismiss = ::finish,
        )
      }
    }
  }
}
