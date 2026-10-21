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

package arun.com.chromer.util

import android.net.Uri
import androidx.core.net.toUri

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
      !raw.isNullOrEmpty() && isWebScheme(runCatching { raw.toUri().scheme }.getOrNull())

    /** The host of [raw] for a log line, never the whole address; null when there is none. */
    fun hostForLog(raw: String?): String? =
      if (raw.isNullOrEmpty()) null else runCatching { raw.toUri().host }.getOrNull()
  }
}
