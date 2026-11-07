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

package arun.com.chromer.bubbles.webheads.motion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.IdentityHashMap

/** Whether the page of a web head is ready. */
enum class PageState { Unknown, Loading, Loaded, Failed }

/** What told that the page of a head finished. */
enum class PageSignal {
  /** The Custom Tab of the head reported that its navigation finished or failed. */
  Navigation,

  /** The prefetch of the head's address ended. */
  Prefetch,

  /** The fetch of the site details (title, icon, colour) ended. */
  SiteDetails,
}

/** How long a head stays loading with no signal before its state turns unknown, so it never loads forever. */
const val PAGE_STATE_LIMIT_MS = 30_000L

/**
 * Sets [WebHeadState.pageState] of each head: loading from [start], then loaded or failed by the
 * first [finish], or unknown when [limitMs] passes with no signal. A head that left loading keeps
 * its state; a [drop]ped head takes no more changes. Call it on the thread that reads the heads.
 */
class HeadPageTracker(private val scope: CoroutineScope, private val limitMs: Long = PAGE_STATE_LIMIT_MS) {

  // Keyed by the head itself, so a closed head and a new head of the same address never share a limit.
  private val limits = IdentityHashMap<WebHeadState, Job>()

  fun start(head: WebHeadState) {
    limits.remove(head)?.cancel()
    head.pageState = PageState.Loading
    limits[head] = scope.launch {
      delay(limitMs)
      if (limits.remove(head) != null && head.pageState == PageState.Loading) {
        head.pageState = PageState.Unknown
      }
    }
  }

  /** The page of [head] finished by [signal]; only a loading head that is still tracked changes. */
  fun finish(head: WebHeadState, signal: PageSignal, failed: Boolean) {
    val limit = limits.remove(head) ?: return
    limit.cancel()
    if (head.pageState == PageState.Loading) {
      head.pageState = if (failed) PageState.Failed else PageState.Loaded
      Timber.d("Head page %s: %s by %s", head.url, head.pageState, signal)
    }
  }

  /** [head] closed: forget it, so a late signal or the limit changes nothing. */
  fun drop(head: WebHeadState) {
    limits.remove(head)?.cancel()
  }
}
