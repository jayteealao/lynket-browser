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
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubblePlace
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.data.preferences.BrowsingMode
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/** The notification id of the bubble a dismiss is for. */
internal const val EXTRA_BUBBLE_NOTIFICATION_ID = "arun.com.chromer.extra.BUBBLE_NOTIFICATION_ID"

/**
 * Android sends this when the person dismisses a bubble; it removes the bubble's notification, the
 * summary when no bubble is left, and its shortcut. The bubble leaves the list of open bubbles
 * marked as closed on purpose, so it never comes back on its own. The mark lives here and not in
 * the manager: a refused bubble goes through the manager too, and only a person dismissal arrives
 * here. A close of a bubble in the stack leaves room, so the newest overflow bubble comes back.
 */
@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.Q)
class BubbleDismissReceiver : BroadcastReceiver() {

  @Inject
  lateinit var bubbleNotificationManager: BubbleNotificationManager

  @Inject
  lateinit var bubbleRegistry: BubbleRegistry

  @Inject
  lateinit var bubbleReconciler: BubbleReconciler

  @Inject
  lateinit var nativeFloatingBubble: NativeFloatingBubble

  override fun onReceive(context: Context, intent: Intent) {
    if (!intent.hasExtra(EXTRA_BUBBLE_NOTIFICATION_ID)) {
      Timber.w("A bubble dismiss without a notification id")
      return
    }
    val id = intent.getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0)
    Timber.d("Bubble dismissed: %d", id)
    val key = BubbleKey(BrowsingMode.NativeBubbles, id)
    bubbleRegistry.markClosedOnPurpose(key)
    val closed = bubbleRegistry.remove(key)
    bubbleNotificationManager.dismissed(id)
    // A close in the overflow leaves the stack full, and a second delete intent finds no entry. A
    // close in the stack reconciles in the return, after the rest of a drag has arrived: a read now
    // would file those closing bubbles under the overflow, and their closes would ask for no return.
    // The broadcast does not stay open for the return, or Android would hold those closes back.
    if (closed?.place == BubblePlace.STACK) {
      nativeFloatingBubble.returnNewestOverflowBubble()
    } else {
      bubbleReconciler.reconcile()
    }
  }
}
