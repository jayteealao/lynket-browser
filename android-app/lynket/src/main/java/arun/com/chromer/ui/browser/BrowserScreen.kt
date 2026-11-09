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

package arun.com.chromer.ui.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.ui.navigation.LynketNavigator
import timber.log.Timber

/**
 * The Browser key hands the address to [CustomTabActivity], which opens it in a Custom Tab or the
 * web view by the person's settings, and leaves the back stack. It draws nothing of its own.
 */
@Composable
fun BrowserScreen(
  url: String,
  navigator: LynketNavigator
) {
  val context = LocalContext.current
  LaunchedEffect(url) {
    try {
      context.startActivity(browserIntent(context, url))
    } catch (e: Exception) {
      Timber.e(e, "Could not open %s", url)
    }
    navigator.back()
  }
  Box(Modifier.fillMaxSize())
}

/**
 * The intent that opens [url] from the Browser key. In full incognito mode it carries the
 * incognito flag, so the page opens in an ephemeral tab or the incognito web view, never in the
 * browser's normal profile.
 */
internal fun browserIntent(context: Context, url: String): Intent =
  Intent(context, CustomTabActivity::class.java).apply {
    data = Uri.parse(url)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
    if (Preferences.get(context).fullIncognitoMode()) putExtra(EXTRA_KEY_INCOGNITO, true)
  }
