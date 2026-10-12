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

package arun.com.chromer.browsing.customtabs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import arun.com.chromer.shared.Constants.EXTRA_KEY_ORIGINAL_URL

/*
 * PendingIntents that the Custom Tab provider sends back to the app (menu items, the action button
 * and the bottom bar). The provider fills in the page's current address as the intent's data, and
 * for the bottom bar the clicked view's id (CustomTabsIntent.EXTRA_REMOTEVIEWS_CLICKED_ID). For an
 * immutable PendingIntent that fill-in is ignored (source: android-36 sources,
 * android/app/PendingIntent.java:257,915), so these PendingIntents are mutable. From targetSdk 34 a
 * mutable PendingIntent must name its component (PendingIntent.java:293-297), so each one does.
 */

/** FLAG_UPDATE_CURRENT, plus FLAG_MUTABLE on Android 12 and later so the provider can fill in. */
fun callbackFlags(): Int =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
  } else {
    PendingIntent.FLAG_UPDATE_CURRENT
  }

/** Returns [intent] after checking it names its component; a mutable implicit intent is refused. */
fun requireExplicit(intent: Intent): Intent {
  requireNotNull(intent.component) { "A Custom Tab callback intent must name its component: $intent" }
  return intent
}

/** A mutable, explicit broadcast PendingIntent for a Custom Tab callback. */
fun callbackBroadcast(context: Context, requestCode: Int, intent: Intent): PendingIntent =
  PendingIntent.getBroadcast(context, requestCode, requireExplicit(intent), callbackFlags())

/** A mutable, explicit activity PendingIntent for a Custom Tab callback. */
fun callbackActivity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
  PendingIntent.getActivity(context, requestCode, requireExplicit(intent), callbackFlags())

/**
 * The page address a Custom Tab callback carries: the data the provider filled in, else the
 * address the tab was opened with, else null.
 */
fun Intent.browsingUrl(): String? =
  dataString?.takeIf { it.isNotBlank() } ?: getStringExtra(EXTRA_KEY_ORIGINAL_URL)?.takeIf { it.isNotBlank() }
