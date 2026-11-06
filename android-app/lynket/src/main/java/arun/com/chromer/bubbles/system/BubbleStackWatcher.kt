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
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import arun.com.chromer.browsing.webview.EmbeddableWebViewActivity
import arun.com.chromer.util.ActivityLifeCycleCallbackAdapter
import dagger.Lazy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells whether the bubble stack is open, and brings overflow bubbles back when it folds. A bubble
 * that returns while the stack is open takes the selection and replaces the page on screen, so a
 * close leaves the overflow alone until the stack folds.
 *
 * Android gives the app no signal for a fold, so this reads the pages: the stack is open while a
 * page in a bubble is started. A switch between bubbles stops a page too, and the page of the next
 * bubble starts around that stop. So a fold is a stop after which no page starts within
 * [FOLD_CHECK_MS]. The last bubble closing from its X leaves no page to start, and reads the same.
 * sdlc-debt: a fixed wait; a page slower than it to start reads as a fold, and a return can then
 * come in an open stack. The overflow panel of an open stack shows no page either, so it reads as a
 * fold. A signal from Android would replace this.
 */
@Singleton
@RequiresApi(Build.VERSION_CODES.Q)
class BubbleStackWatcher
@Inject
constructor(
  // Lazy: this watches every activity, and a person who opens no bubble builds none of it.
  private val nativeFloatingBubble: Lazy<NativeFloatingBubble>,
) {

  private val main = Handler(Looper.getMainLooper())

  private val lastStartedPage = MutableStateFlow<Int?>(null)

  /** The notification id of the bubble page that started last, so the page a person sees. */
  val startedPage: StateFlow<Int?> = lastStartedPage.asStateFlow()

  /** The bubble pages between a start and a stop, written on the main thread. */
  @Volatile
  private var startedPages = 0

  /** A page stopped and no page has started yet, so this may still be a switch. */
  @Volatile
  private var foldUnsure = false

  private val foldCheck = Runnable {
    foldUnsure = false
    Timber.d("Stack folded")
    nativeFloatingBubble.get().fillFreePlaces { !stackOpen }
  }

  /** True from a page start until a fold is sure: the delete intent of a page can follow its stop. */
  val stackOpen: Boolean get() = startedPages > 0 || foldUnsure

  fun attach(application: Application) {
    application.registerActivityLifecycleCallbacks(object : ActivityLifeCycleCallbackAdapter() {
      override fun onActivityStarted(activity: Activity) {
        if (activity.isBubblePage) pageStarted(activity.intent.getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0))
      }

      override fun onActivityStopped(activity: Activity) {
        if (activity.isBubblePage) pageStopped()
      }
    })
  }

  internal fun pageStarted(notificationId: Int? = null) {
    startedPages++
    lastStartedPage.value = notificationId
    Timber.d("Bubble page started: %d", startedPages)
    foldUnsure = false
    main.removeCallbacks(foldCheck)
  }

  internal fun pageStopped() {
    startedPages--
    Timber.d("Bubble page stopped: %d", startedPages)
    if (startedPages == 0) {
      foldUnsure = true
      main.postDelayed(foldCheck, FOLD_CHECK_MS)
    }
  }

  private val Activity.isBubblePage: Boolean
    get() = this is EmbeddableWebViewActivity && intent.hasExtra(EXTRA_BUBBLE_NOTIFICATION_ID)

  internal companion object {
    /**
     * How long after a page stops the page of the next bubble can still start. The longest measured
     * were 598 ms on the Android 14 emulator and 949 ms on the Android 11 one.
     */
    const val FOLD_CHECK_MS = 1_000L
  }
}
