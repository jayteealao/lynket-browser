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

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.OpenBubble
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.di.hilt.IoDispatcher
import arun.com.chromer.util.ActivityLifeCycleCallbackAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the list of open native bubbles in line with what Android holds. Android sends nothing when
 * a bubble ages into the overflow, or when a full overflow drops one, so the list is read again when
 * a bubble opens, when one is dismissed and when a Lynket screen starts.
 */
@Singleton
@RequiresApi(Build.VERSION_CODES.Q)
class BubbleReconciler
@Inject
constructor(
  private val bubbleNotificationManager: BubbleNotificationManager,
  private val bubbleRegistry: BubbleRegistry,
  @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {

  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

  fun reconcile() {
    val mode = BrowsingMode.NativeBubbles
    // A person who never uses native bubbles pays for no read.
    if (bubbleRegistry.list(mode).isEmpty()) return
    val dropped = bubbleRegistry.reconcile(mode, bubbleNotificationManager::bubblePlaces) ?: return
    Timber.d(
      "Reconciled: total=%d overflow=%s returnable=%s dropped=%s",
      bubbleRegistry.list(mode).size,
      bubbleRegistry.overflow(mode).ids(),
      bubbleRegistry.returnCandidates(mode).ids(),
      dropped.joinToString(",") { it.notificationId.toString() },
    )
  }

  /** The same, off the calling thread: the read asks Android, so the main thread must not wait for it. */
  fun reconcileLater() {
    scope.launch { reconcile() }
  }

  /** Reconciles at every activity start: a bubble page is an activity, so it reads a fresh list. */
  fun attach(application: Application) {
    application.registerActivityLifecycleCallbacks(object : ActivityLifeCycleCallbackAdapter() {
      override fun onActivityStarted(activity: Activity) = reconcileLater()
    })
  }

  private fun List<OpenBubble>.ids() = joinToString(",") { it.key.notificationId.toString() }
}
