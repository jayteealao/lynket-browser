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

package arun.com.chromer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation3.runtime.rememberNavBackStack
import arun.com.chromer.about.changelog.Changelog
import arun.com.chromer.settings.Preferences
import arun.com.chromer.ui.navigation.LynketKey
import arun.com.chromer.ui.navigation.LynketNavDisplay
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.navigation.WebUrlDeepLinkMatcher
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint

/**
 * The launcher activity and the only host of in-app navigation: every in-app screen is a
 * [LynketKey] on the one back stack shown by [LynketNavDisplay].
 *
 * A fresh start builds the first stack once: home, the changelog when the version changed, the
 * intro on a first run, and a web link handed to this activity. A process restore keeps the saved
 * stack and reads none of those triggers again.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  private var navigator: LynketNavigator? = null
  private var pendingLink: LynketKey? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val initialKeys = if (savedInstanceState == null) freshStartKeys(intent) else listOf(LynketKey.Home)

    setContent {
      ChromerThemeFromSettings {
        // Test tags double as resource ids so the device checks can find the shell's controls.
        Surface(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
          val backStack = rememberNavBackStack(*initialKeys.toTypedArray())
          val stackNavigator = remember(backStack) { LynketNavigator(backStack) }
          DisposableEffect(stackNavigator) {
            navigator = stackNavigator
            pendingLink?.let { stackNavigator.navigate(it) }
            pendingLink = null
            onDispose { navigator = null }
          }
          LynketNavDisplay(navigator = stackNavigator)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val key = WebUrlDeepLinkMatcher.keyFor(intent) ?: return
    navigator?.navigate(key) ?: run { pendingLink = key }
  }

  /** Reads the first-run and changelog triggers once; both reads update the stored values. */
  private fun freshStartKeys(intent: Intent?): List<LynketKey> {
    val preferences = Preferences.get(this)
    return LynketNavigator.initialKeys(
      isFirstRun = preferences.isFirstRun,
      showChangelog = Changelog.shouldShow(preferences, BuildConfig.VERSION_CODE),
      deepLink = intent?.let(WebUrlDeepLinkMatcher::keyFor),
    )
  }
}
