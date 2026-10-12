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

import android.app.Application
import android.content.Intent
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The back stack survives process death with every key equal, including URLs with `?`, `#`, `%`
 * and non-ASCII characters (the same serializer `rememberNavBackStack` uses on Android).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class NavKeyRoundTripTest {

  private val urls = listOf(
    "https://example.com/a%20b?q=ü&x=1#frag",
    "https://bücher.example/straße?ä=ö",
    "https://example.com/100%25?a=b&c=d#e",
  )

  private val allKeys: List<NavKey> = listOf(
    LynketKey.Home, LynketKey.History, LynketKey.Tabs, LynketKey.Settings, LynketKey.ProviderSelection,
    LynketKey.About, LynketKey.Tips, LynketKey.Intro, LynketKey.Changelog, LynketKey.WebHeads,
    LynketKey.BrowsingMode, LynketKey.LookAndFeel, LynketKey.BrowsingOptions, LynketKey.PerApp,
  ) + urls.map { LynketKey.Browser(it) } + urls.map { LynketKey.Article(it) }

  @Test
  fun everyKeyRoundTripsThroughSavedState() {
    val serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer<NavKey>())
    val stack = NavBackStack(*allKeys.toTypedArray())

    val saved = encodeToSavedState(serializer, stack)
    val restored = decodeFromSavedState(serializer, saved)

    assertThat(restored.toList()).containsExactlyElementsIn(allKeys).inOrder()
    restored.filterIsInstance<LynketKey.Browser>().forEachIndexed { index, key ->
      assertThat(key.url).isEqualTo(urls[index])
    }
  }

  @Test
  fun destinationExtraOpensOnlyAllowedScreens() {
    val allowed = listOf(
      LynketKey.Settings, LynketKey.BrowsingMode, LynketKey.LookAndFeel,
      LynketKey.BrowsingOptions, LynketKey.PerApp, LynketKey.ProviderSelection,
    )
    allowed.forEach { key ->
      val name = Destination.nameOf(key)
      assertThat(name).isNotNull()
      assertThat(Destination.keyFor(Intent().putExtra(Destination.EXTRA, name))).isEqualTo(key)
    }
    listOf("Home", "Browser", "Changelog", "WebHeads", "settings", "", "../Settings").forEach { value ->
      assertThat(Destination.keyFor(Intent().putExtra(Destination.EXTRA, value))).isNull()
    }
    assertThat(Destination.keyFor(Intent())).isNull()
    assertThat(Destination.nameOf(LynketKey.Home)).isNull()
  }
}
