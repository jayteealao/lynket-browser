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

package arun.com.chromer.bubbles.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/** The notification id of the bubble that a dismiss, or a page in a bubble, is for. */
internal const val EXTRA_BUBBLE_NOTIFICATION_ID = "arun.com.chromer.extra.BUBBLE_NOTIFICATION_ID"

/**
 * Android sends this when the person drags a bubble away or picks Dismiss bubble.
 * [BubbleCloser] does the rest, for this signal and for the X of a page.
 */
@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.Q)
class BubbleDismissReceiver : BroadcastReceiver() {

  @Inject
  lateinit var bubbleCloser: BubbleCloser

  override fun onReceive(context: Context, intent: Intent) {
    if (!intent.hasExtra(EXTRA_BUBBLE_NOTIFICATION_ID)) {
      Timber.w("A bubble dismiss without a notification id")
      return
    }
    bubbleCloser.close(intent.getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0))
  }
}
