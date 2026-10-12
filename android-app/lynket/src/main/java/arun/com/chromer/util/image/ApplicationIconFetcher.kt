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

package arun.com.chromer.util.image

import android.content.Context
import android.content.pm.PackageManager
import arun.com.chromer.util.appicon.ApplicationIcon
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options

/**
 * Loads the launcher icon of an installed app for an `application-icon:<package>` URI
 * ([ApplicationIcon.createUri]), so `AsyncImage(model = iconUri)` shows provider icons.
 *
 * Coil maps an `android.net.Uri` model to a `coil3.Uri` before it picks a fetcher
 * (source: coil-core-android 3.6.3 `coil3/map/AndroidUriMapper.kt`), so this factory takes
 * `coil3.Uri`. An opaque URI such as `application-icon:com.android.chrome` keeps the package
 * after the scheme separator.
 */
class ApplicationIconFetcher(
  private val context: Context,
  private val packageName: String,
) : Fetcher {

  override suspend fun fetch(): FetchResult? = try {
    ImageFetchResult(
      image = context.packageManager.getApplicationIcon(packageName).asImage(),
      isSampled = false,
      dataSource = DataSource.DISK,
    )
  } catch (e: PackageManager.NameNotFoundException) {
    null
  }

  class Factory : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
      val packageName = packageNameOf(data) ?: return null
      return ApplicationIconFetcher(options.context, packageName)
    }
  }

  companion object {
    /** The package of an application-icon URI, or null for any other URI. */
    fun packageNameOf(data: Uri): String? {
      if (data.scheme != ApplicationIcon.URI_SCHEME_APPLICATION_ICON) return null
      return data.toString()
        .substringAfter("${ApplicationIcon.URI_SCHEME_APPLICATION_ICON}:")
        .trimStart('/')
        .takeIf { it.isNotBlank() }
    }
  }
}
