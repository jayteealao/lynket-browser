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

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every in-app destination of [arun.com.chromer.MainActivity]'s single back stack.
 *
 * Keys are saved over process death by Navigation 3's serializer. A URL is a plain [String] field,
 * never part of a route string, so `?`, `#`, `%` and non-ASCII characters are kept exactly.
 */
@Serializable
sealed interface LynketKey : NavKey {
  @Serializable data object Home : LynketKey
  @Serializable data object History : LynketKey
  @Serializable data object Tabs : LynketKey
  @Serializable data object Settings : LynketKey
  @Serializable data object ProviderSelection : LynketKey
  @Serializable data object About : LynketKey
  @Serializable data object Tips : LynketKey
  @Serializable data object Intro : LynketKey
  @Serializable data object Changelog : LynketKey
  @Serializable data object WebHeads : LynketKey
  @Serializable data class Browser(val url: String) : LynketKey
  @Serializable data class Article(val url: String) : LynketKey
  @Serializable data class PerAppSettings(val packageName: String) : LynketKey
}
