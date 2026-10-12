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

package arun.com.chromer.ui.browsing

import android.app.Activity
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import arun.com.chromer.R
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import java.util.WeakHashMap

/**
 * The error shown when a link from a blacklisted app cannot go to the secondary browser (none
 * chosen, not installed, or it refused the link): the app icon, the title, [message], "Launch
 * settings" and Cancel.
 */
@Composable
fun SecondaryBrowserErrorDialog(
  message: CharSequence,
  onLaunchSettings: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val appIcon = remember(context) {
    ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()?.asImageBitmap()
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = appIcon?.let { { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) } },
    title = { Text(stringResource(R.string.secondary_browser_launching_error_title)) },
    text = { Text(message.toString(), modifier = Modifier.testTag("secondary-browser-error-message")) },
    confirmButton = {
      TextButton(onClick = onLaunchSettings, modifier = Modifier.testTag("secondary-browser-launch-settings")) {
        Text(stringResource(R.string.launch_setting))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("secondary-browser-cancel")) {
        Text(stringResource(android.R.string.cancel))
      }
    },
    modifier = Modifier.testTag("secondary-browser-error"),
  )
}

private val showingError = WeakHashMap<Activity, Boolean>()

/** True while [showSecondaryBrowserError] shows its dialog over this activity. */
val Activity.isShowingSecondaryBrowserError: Boolean
  get() = showingError[this] == true

/**
 * Shows [SecondaryBrowserErrorDialog] over [activity], the translucent activity that received the
 * link. "Launch settings" opens Lynket; either button or a tap outside closes the dialog and
 * finishes [activity]. Call on the main thread.
 */
fun showSecondaryBrowserError(
  activity: Activity,
  message: CharSequence,
  // Tests pass a fixed theme; the app follows the person's theme settings.
  theme: @Composable (@Composable () -> Unit) -> Unit = { ChromerThemeFromSettings(it) },
) {
  showingError[activity] = true
  val finish = {
    showingError.remove(activity)
    activity.finish()
  }
  val content: @Composable () -> Unit = {
    theme {
      SecondaryBrowserErrorDialog(
        message = message,
        onLaunchSettings = {
          activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.let {
            it.addFlags(FLAG_ACTIVITY_CLEAR_TOP)
            activity.startActivity(it)
          }
          finish()
        },
        onDismiss = finish,
      )
    }
  }
  val root = activity.findViewById<ViewGroup>(android.R.id.content)
  if (activity is ComponentActivity && (root == null || root.childCount == 0)) {
    activity.setContent(content = content)
  } else {
    activity.addContentView(
      ComposeView(activity).apply { setContent(content) },
      ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
    )
  }
}
