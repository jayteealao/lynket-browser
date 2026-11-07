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

package arun.com.chromer.ui.webview

import arun.com.chromer.bubbles.BubblePageCount

/** The address of the pages that did not load, as on the approved screen. */
const val FAILED_ADDRESS = "https://lemire.me/blog/beating-the-compiler"

/** A page that did not load for [cause], in a bubble that is third of seven by default. */
fun failedPage(cause: LoadFailure, count: BubblePageCount? = BubblePageCount(3, 7), progress: Int = 100): WebViewPage =
  WebViewPage("lemire.me", FAILED_ADDRESS, progress, count, PageLoad.Failed(cause, FAILED_ADDRESS))

/** A page that did not load for [cause] and is being retried, at [progress], in a bubble third of seven. */
fun retryingPage(cause: LoadFailure = LoadFailure.Offline, count: BubblePageCount? = BubblePageCount(3, 7), progress: Int = 40): WebViewPage =
  failedPage(cause, count, progress).copy(load = PageLoad.Failed(cause, FAILED_ADDRESS, retrying = true))

/** The host of the worst page: many subdomains and a two-part ending, with nowhere to wrap. */
const val WORST_HOST = "a-very-long-subdomain-name.of-a-site-with-many-parts.example.co.uk"

/** The worst page: a long host as title and address, the last of twelve bubbles, a site not reached. */
fun worstFailedPage(): WebViewPage {
  val address = "https://$WORST_HOST/some/deep/path"
  return WebViewPage(WORST_HOST, address, 100, BubblePageCount(12, 12), PageLoad.Failed(LoadFailure.CannotReach, address))
}
