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

import android.os.Build
import androidx.annotation.RequiresApi
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubblePlace
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.data.preferences.BrowsingMode
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Closes one native bubble: its notification, the summary when no bubble is left, and its shortcut
 * go, and it leaves the list of open bubbles marked as closed on purpose, so it never comes back on
 * its own. The mark lives here and not in the notification manager: a refused bubble goes through
 * that manager too, and is not closed by anyone.
 *
 * Two signals reach it. Android sends a delete intent for a drag and for Dismiss bubble. A page that
 * finishes sends nothing: Android removes the bubble with no delete intent and keeps it out of the
 * overflow, so the page calls this itself. A second signal for one bubble finds no entry, and so
 * asks for no return.
 *
 * A close of a bubble in the stack leaves room. With the stack folded the newest overflow bubble
 * comes back at once. With the stack open it waits for the fold, see [BubbleStackWatcher].
 */
@Singleton
@RequiresApi(Build.VERSION_CODES.Q)
class BubbleCloser
@Inject
constructor(
  private val bubbleNotificationManager: BubbleNotificationManager,
  private val bubbleRegistry: BubbleRegistry,
  private val bubbleReconciler: BubbleReconciler,
  private val nativeFloatingBubble: NativeFloatingBubble,
  private val stackWatcher: BubbleStackWatcher,
) {

  fun close(notificationId: Int) {
    Timber.d("Bubble dismissed: %d", notificationId)
    val key = BubbleKey(BrowsingMode.NativeBubbles, notificationId)
    bubbleRegistry.markClosedOnPurpose(key)
    val closed = bubbleRegistry.remove(key)
    bubbleNotificationManager.dismissed(notificationId)
    // A close in the overflow leaves the stack full, and a second signal finds no entry. A close in
    // an open stack waits for the fold, and only a folded stack can be dragged away. A close in a
    // folded stack leaves its read to the return, which can tell a drag: during a drag a read here
    // would file the closing bubbles under the overflow, and their closes would ask for no return.
    // The return runs off this thread, so a delete intent does not hold back the others of a drag.
    when {
      closed?.place != BubblePlace.STACK -> bubbleReconciler.reconcileLater()
      stackWatcher.stackOpen -> {
        nativeFloatingBubble.holdReturn()
        bubbleReconciler.reconcileLater()
      }
      else -> nativeFloatingBubble.returnNewestOverflowBubble()
    }
  }
}
