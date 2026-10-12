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

package arun.com.chromer.ui.intro

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import timber.log.Timber

/**
 * Asks Android to make Lynket the default browser: the browser role dialog on Android 10 and
 * later, the default apps settings screen before that. [onDone] runs after the person answers.
 */
@Composable
fun rememberDefaultBrowserRequest(onDone: () -> Unit): () -> Unit {
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    onDone()
  }
  return {
    val intent = defaultBrowserIntent(context)
    if (intent == null) {
      onDone()
    } else {
      try {
        launcher.launch(intent)
      } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No screen to choose the default browser")
        onDone()
      }
    }
  }
}

/** The intent that asks for the browser role, or null when Lynket already holds it. */
internal fun defaultBrowserIntent(context: Context): Intent? {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val roles = context.getSystemService<RoleManager>()
    if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
      if (roles.isRoleHeld(RoleManager.ROLE_BROWSER)) return null
      return roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
    }
  }
  return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}
