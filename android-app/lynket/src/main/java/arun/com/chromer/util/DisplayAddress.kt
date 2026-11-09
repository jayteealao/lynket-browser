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

private val WEB_PREFIXES = listOf("https://", "http://")

/**
 * The address line of a History or Tabs row: the address without `https://`, `http://`, a
 * `user@` part or a leading `www.`, so the host and the end of the path are what the person reads. Every other scheme
 * (`file:`, `content:`, `about:`) is kept as given, because there the scheme is the information.
 * A host-only, long-host or IP address comes back whole; the row's text layout cuts it in the
 * middle. Plain string work: never throws, and a blank address gives `""`.
 */
fun displayAddress(url: String): String {
  val trimmed = url.trim()
  val rest = stripWebScheme(trimmed) ?: return trimmed
  val authorityEnd = authorityEnd(rest)
  // A `user[:password]@` part is not shown: the row names the host the way its title does.
  val authority = dropWww(rest.substring(0, authorityEnd).substringAfterLast('@'))
  val tail = rest.substring(authorityEnd)
  return authority + if (tail == "/") "" else tail
}

/**
 * The host of a web address as a row shows it: no `user@`, no leading `www.`, the port kept.
 * Null for any other scheme, or when the address has no host.
 */
fun displayHost(url: String): String? {
  val rest = stripWebScheme(url.trim()) ?: return null
  val authority = rest.substring(0, authorityEnd(rest)).substringAfterLast('@')
  return dropWww(authority).ifEmpty { null }
}

/**
 * The title of a History or Tabs row. A page with no title shows its host as the title, not its
 * address, so no row shows its address twice. With no host (a `file:` or `content:` address), the
 * last part of the path names it (the file name); failing that, the display address.
 */
fun rowTitle(title: String?, url: String): String {
  if (!title.isNullOrBlank()) return title
  return displayHost(url) ?: lastPathSegment(url.trim()) ?: displayAddress(url)
}

private fun stripWebScheme(address: String): String? =
  WEB_PREFIXES.firstOrNull { address.startsWith(it, ignoreCase = true) }
    ?.let { address.substring(it.length) }

/** The index where the authority (`user@host:port`) of [rest] ends: the first `/`, `?` or `#`. */
private fun authorityEnd(rest: String): Int =
  rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }

/** Drops a leading `www.` only when the rest of the host still has a dot: `www` alone is a host. */
private fun dropWww(authority: String): String {
  if (!authority.startsWith("www.", ignoreCase = true)) return authority
  val rest = authority.substring(4)
  return if (rest.substringBefore(':').contains('.')) rest else authority
}

/** The last non-empty path segment of a `scheme://authority/path` address, without query or fragment. */
private fun lastPathSegment(address: String): String? {
  val afterSlashes = address.substringAfter("://", missingDelimiterValue = "")
  if (afterSlashes.isEmpty()) return null
  val pathStart = afterSlashes.indexOf('/')
  if (pathStart < 0) return null
  return afterSlashes.substring(pathStart).substringBefore('?').substringBefore('#')
    .split('/').lastOrNull { it.isNotEmpty() }
}
