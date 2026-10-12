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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import arun.com.chromer.browsing.customtabs.CustomTabActivity
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
      context.startActivity(
        Intent(context, CustomTabActivity::class.java).apply {
          data = Uri.parse(url)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
      )
    } catch (e: Exception) {
      Timber.e(e, "Could not open %s", url)
    }
    navigator.back()
  }
  Box(Modifier.fillMaxSize())
}
