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

package arun.com.chromer.browsing.webview

import android.webkit.WebViewClient
import arun.com.chromer.ui.webview.LoadFailure
import arun.com.chromer.ui.webview.PageLoad

/**
 * Turns the WebView's load callbacks into one [PageLoad]. A start always means a new load, so it
 * clears a failure; the first failure of a load wins until the next start; and the finish WebView
 * sends after a failed or cancelled load leaves the page failed. A retry ([retry]) loads the failed
 * address again and keeps the cause on screen until the page loads or fails again, with the new
 * cause. [onChange] gets each new state, never a repeated one. [isOnline] is read only when an
 * error arrives.
 */
class PageLoadTracker(
  private val isOnline: () -> Boolean,
  private val onChange: (PageLoad) -> Unit,
) {
  var state: PageLoad = PageLoad.Loading
    private set

  private val retrying: Boolean
    get() = (state as? PageLoad.Failed)?.retrying == true

  private val failedNotRetrying: Boolean
    get() = (state as? PageLoad.Failed)?.retrying == false

  /**
   * Starts a retry of a failed page and returns the address to load, or `null` when the page has
   * not failed or a retry already runs, so a second tap does nothing.
   */
  fun retry(): String? {
    val failed = state as? PageLoad.Failed ?: return null
    if (failed.retrying) return null
    set(failed.copy(retrying = true))
    return failed.address
  }

  /** A load of [url] started; a retry keeps its cause on screen until it ends. */
  fun started(url: String) {
    if (retrying) return
    set(PageLoad.Loading)
  }

  /** The page itself, at [url], failed with the WebView error [code]. */
  fun mainFrameError(code: Int, url: String) {
    if (failedNotRetrying) return
    set(PageLoad.Failed(causeOf(code, isOnline()), url))
  }

  /** The certificate of [url] is not trusted; the load was cancelled. */
  fun certificateError(url: String) {
    if (failedNotRetrying) return
    set(PageLoad.Failed(LoadFailure.Certificate, url))
  }

  /** WebView finished the load of [url]; after a failure the page stays failed. */
  fun finished(url: String) {
    if (state == PageLoad.Loading || retrying) set(PageLoad.Loaded)
  }

  private fun set(next: PageLoad) {
    if (next == state) return
    state = next
    onChange(next)
  }

  companion object {
    /**
     * The cause a person reads for the WebView error [code]: with no network a name, connection or
     * time-out error means offline, and with one it means the site cannot be reached.
     */
    internal fun causeOf(code: Int, online: Boolean): LoadFailure = when (code) {
      WebViewClient.ERROR_HOST_LOOKUP,
      WebViewClient.ERROR_CONNECT,
      WebViewClient.ERROR_TIMEOUT,
      -> if (online) LoadFailure.CannotReach else LoadFailure.Offline
      else -> LoadFailure.General
    }
  }
}
