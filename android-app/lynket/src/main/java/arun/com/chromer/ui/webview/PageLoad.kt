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

/** Where the page of the web view is in its load: still loading, loaded, or failed to load. */
sealed interface PageLoad {
  /** The page is loading; it is also the state before the first load starts. */
  data object Loading : PageLoad

  /** The page finished loading. A site's own error page, such as its 404 page, is loaded too. */
  data object Loaded : PageLoad

  /** The page itself did not load, for [cause]; [address] is the address that failed. */
  data class Failed(val cause: LoadFailure, val address: String) : PageLoad
}

/** Why a page did not load. The cause is a hint for the words a person reads, not a diagnosis. */
enum class LoadFailure {
  /** The device has no network connection. */
  Offline,

  /** The device is online, but the site's name or server did not answer. */
  CannotReach,

  /** The site's certificate is not trusted, so nothing of the page loads. */
  Certificate,

  /** Any other reason the page did not load. */
  General,
}
