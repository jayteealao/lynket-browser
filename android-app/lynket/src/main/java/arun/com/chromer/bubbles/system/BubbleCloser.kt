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
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubblePlace
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.OpenBubble
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.di.hilt.IoDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
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
 * comes back at once. With the stack open it waits for the fold, see [BubbleStackWatcher]. The X of
 * the last bubble in an open stack is the exception, see [closePage].
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
  @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {

  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
  private val main = Handler(Looper.getMainLooper())

  /** The bubbles between their X and the end of [closePage]: a second tap of the X closes nothing. */
  private val pagesClosing = ConcurrentHashMap.newKeySet<Int>()

  fun close(notificationId: Int) {
    val closed = takeOffTheList(notificationId)
    val held = closed?.place == BubblePlace.STACK && stackWatcher.holdForTheFold()
    bubbleNotificationManager.dismissed(notificationId)
    fillTheFreePlace(closed, held)
  }

  /**
   * [close] for a page that finishes on the main thread. The list and the hold of the return are in
   * memory and change at once, so the count goes from the other pages and a fold that comes next
   * still finds the hold. The notification, the shortcut and the return need Android, and wait on
   * the lock of a bubble post, so they run off the main thread and the page does not wait for them.
   */
  private fun closeOffTheMainThread(notificationId: Int) {
    val closed = takeOffTheList(notificationId)
    val held = closed?.place == BubblePlace.STACK && stackWatcher.holdForTheFold()
    scope.launch {
      try {
        bubbleNotificationManager.dismissed(notificationId)
        fillTheFreePlace(closed, held)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }

  /** After a close: [held] says the stack was open and [BubbleStackWatcher] holds the return. */
  private fun fillTheFreePlace(closed: OpenBubble?, held: Boolean) {
    // A close in the overflow leaves the stack full, and a second signal finds no entry. A close in
    // an open stack waits for the fold, and only a folded stack can be dragged away. A close in a
    // folded stack leaves its read to the return, which can tell a drag: during a drag a read here
    // would file the closing bubbles under the overflow, and their closes would ask for no return.
    // The return runs off this thread, so a delete intent does not hold back the others of a drag.
    when {
      closed?.place != BubblePlace.STACK -> bubbleReconciler.reconcileLater()
      held -> bubbleReconciler.reconcileLater()
      else -> nativeFloatingBubble.returnNewestOverflowBubble()
    }
  }

  /**
   * What a page in a bubble does when it finishes: [close], then [finishPage], on the main thread.
   * The page finishes at once; the Android part of the close runs after it, off the main thread.
   *
   * The X of the last bubble in an open stack needs more, or the stack empties and folds, and the
   * bubbles come back to a folded stack. So when the overflow holds a bubble, the closed bubble stays
   * until the newest overflow bubble is in the stack, and the stack stays open on that bubble's page.
   * A phone that selects a returned bubble starts its page, and that is the signal. The emulators
   * leave the selection alone, so their closed bubble goes at the end of [SHELL_ADD_MS], and the shell
   * selects the bubble that is left. Other held closes still wait for the fold.
   *
   * The closed bubble leaves the list at once, so its count goes from the top bar and it can never be
   * returned. A return that fails ends as any close in an open stack does.
   * sdlc-debt: a fixed wait; a shell slower than it to add the bubble empties the stack and folds it,
   * and a signal that a bubble entered the stack would replace it.
   *
   * A page that leaves the bubbles for a full window ([leavingTheStack]) closes as an ordinary close,
   * so no bubble is brought into an open stack in front of the new window, and its place fills at the
   * fold.
   */
  fun closePage(notificationId: Int, leavingTheStack: Boolean = false, finishPage: () -> Unit) {
    if (notificationId in pagesClosing) return
    if (leavingTheStack || !isLastInOpenStack(notificationId)) {
      closeOffTheMainThread(notificationId)
      finishPage()
      return
    }
    pagesClosing += notificationId
    scope.launch {
      try {
        bubbleReconciler.reconcile()
        if (isLastInOpenStack(notificationId)) takeLastPlace(notificationId) else close(notificationId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e)
      } finally {
        main.post {
          pagesClosing -= notificationId
          finishPage()
        }
      }
    }
  }

  private suspend fun takeLastPlace(notificationId: Int) {
    takeOffTheList(notificationId)
    val returned = nativeFloatingBubble.returnIntoOpenStack().await()
    val nextPage = stackWatcher.startedPage
    if (returned) {
      withTimeoutOrNull(SHELL_ADD_MS) { nextPage.first { it != null && it != notificationId } }
    } else {
      stackWatcher.holdForTheFold()
    }
    bubbleNotificationManager.dismissed(notificationId)
    bubbleReconciler.reconcile()
    // A page that finishes before the next one starts can fold the stack on Android 14.
    if (returned) withTimeoutOrNull(NEXT_PAGE_MS) { nextPage.first { it != null && it != notificationId } }
  }

  /** From the list alone, so an ordinary close reads nothing from Android on the main thread. */
  private fun isLastInOpenStack(notificationId: Int): Boolean {
    val mode = BrowsingMode.NativeBubbles
    val stack = bubbleRegistry.list(mode).filter { it.place == BubblePlace.STACK }
    return stackWatcher.stackOpen &&
      stack.singleOrNull()?.key?.notificationId == notificationId &&
      bubbleRegistry.returnCandidates(mode).isNotEmpty()
  }

  /** Waits for the closes that run off the main thread, for a test that reads Android after one. */
  @VisibleForTesting
  internal suspend fun awaitCloses() {
    scope.coroutineContext[Job]?.children?.toList()?.joinAll()
  }

  private fun takeOffTheList(notificationId: Int): OpenBubble? {
    Timber.d("Bubble dismissed: %d", notificationId)
    val key = BubbleKey(BrowsingMode.NativeBubbles, notificationId)
    bubbleRegistry.markClosedOnPurpose(key)
    return bubbleRegistry.remove(key)
  }

  private companion object {
    /** How long after its flag a returned bubble can take to enter the stack. See the note above. */
    const val SHELL_ADD_MS = 700L

    /** How long the page of the bubble that is left can take to start. */
    const val NEXT_PAGE_MS = 1_000L
  }
}
