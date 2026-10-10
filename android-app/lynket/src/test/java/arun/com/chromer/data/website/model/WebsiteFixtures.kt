/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.data.website.model

/**
 * Test-only helpers that let the ViewModel and repository tests build and copy [Website]
 * by the field names they were written with (`visitCount`, `isBookmarked`).
 * [Website] is a plain class whose fields are `count` and `bookmarked`.
 */

/** Builds a [Website] with named arguments; importing `Website` brings this into scope. */
internal fun Website(
  url: String,
  title: String?,
  createdAt: Long,
  visitCount: Int,
  isBookmarked: Boolean
): Website = Website(
  title,
  url,
  null,
  null,
  null,
  null,
  isBookmarked,
  createdAt,
  visitCount
)

internal val Website.visitCount: Int get() = count

internal val Website.isBookmarked: Boolean get() = bookmarked

internal fun Website.copy(
  url: String = this.url,
  title: String? = this.title,
  createdAt: Long = this.createdAt,
  visitCount: Int = this.count,
  isBookmarked: Boolean = this.bookmarked
): Website = Website(
  title,
  url,
  faviconUrl,
  canonicalUrl,
  themeColor,
  ampUrl,
  isBookmarked,
  createdAt,
  visitCount
)
