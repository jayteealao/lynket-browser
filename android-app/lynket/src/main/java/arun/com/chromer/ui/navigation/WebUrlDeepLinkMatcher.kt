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

package arun.com.chromer.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.navigation3.runtime.deeplink.DeepLinkMatcher
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.actionFilter
import androidx.navigation3.runtime.deeplink.invoke

/** A web address the app can open: `http` or `https` with a host. The text is kept as given. */
@JvmInline
value class WebUrl private constructor(val value: String) {
  companion object {
    /** Returns null for anything that is not an `http`/`https` address with a host; never throws. */
    fun parseOrNull(raw: String?): WebUrl? {
      if (raw.isNullOrBlank() || raw.any { it.isWhitespace() }) return null
      val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
      if (!isWebScheme(uri.scheme)) return null
      if (uri.host.isNullOrBlank()) return null
      return WebUrl(raw)
    }

    /** True for `http` and `https` in any case; the one scheme rule of every link entry. */
    fun isWebScheme(scheme: String?): Boolean = scheme?.lowercase().let { it == "http" || it == "https" }

    /**
     * True when [raw] parses to an `http` or `https` URI. Checks the scheme only, so every web link
     * that opens today still opens; `javascript:`, `file:`, `content:` and `intent:` links do not.
     */
    fun hasWebScheme(raw: String?): Boolean =
      !raw.isNullOrEmpty() && isWebScheme(runCatching { Uri.parse(raw).scheme }.getOrNull())
  }
}

/**
 * Matches any `http`/`https` VIEW request to a [LynketKey.Browser] key. Navigation 3's
 * `UriDeepLinkMatcher` matches fixed patterns only (source: navigation3-runtime-android 1.2.0
 * sources jar, `UriDeepLinkMatcher.kt`), and a browser takes any host and path, hence this subclass
 * of the library's extension point `DeepLinkMatcher.matchRequest`.
 *
 * A malformed web URI still matches, so the Browser destination shows the link error state instead
 * of the request being dropped. The key holds the URI text exactly as it arrived.
 */
class WebUrlDeepLinkMatcher :
  DeepLinkMatcher<LynketKey.Browser, DeepLinkMatcher.MatchResult<LynketKey.Browser>>(
    listOf(DeepLinkMatcher.actionFilter(Intent.ACTION_VIEW)),
  ) {

  override fun matchRequest(request: DeepLinkRequest): MatchResult<LynketKey.Browser>? {
    val uri = request.uri ?: return null
    if (!WebUrl.isWebScheme(uri.scheme)) return null
    return MatchResult(LynketKey.Browser(uri.toString()))
  }

  companion object {
    /** The key for a link in [intent], or null when the intent carries no web link. */
    fun keyFor(intent: Intent): LynketKey.Browser? =
      runCatching { WebUrlDeepLinkMatcher().match(DeepLinkRequest(intent))?.key }.getOrNull()
  }
}
