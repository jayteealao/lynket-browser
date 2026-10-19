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
import android.view.View
import android.view.ViewTreeObserver
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.rememberNavBackStack
import arun.com.chromer.about.changelog.Changelog
import arun.com.chromer.settings.Preferences
import arun.com.chromer.ui.navigation.LynketKey
import arun.com.chromer.ui.navigation.LynketNavDisplay
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.navigation.Destination
import arun.com.chromer.ui.navigation.WebUrlDeepLinkMatcher
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

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

    if (savedInstanceState != null) {
      show(listOf(LynketKey.Home))
      return
    }
    val repository = Preferences.entryPoint(this).userPreferencesRepository()
    if (repository.snapshot.value != null) {
      show(freshStartKeys(intent))
      return
    }
    // The settings store's first read is not in yet (a cold start, or the first start after an
    // update while the migration runs). Wait for it without blocking the main thread; the first
    // frame is held back meanwhile, so the starting window stays on screen as before.
    var ready = false
    val content = findViewById<View>(android.R.id.content)
    content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
      override fun onPreDraw(): Boolean {
        if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
        return ready
      }
    })
    lifecycleScope.launch {
      repository.snapshot.filterNotNull().first()
      show(freshStartKeys(intent))
      ready = true
    }
  }

  private fun show(initialKeys: List<LynketKey>) {
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
    val key = WebUrlDeepLinkMatcher.keyFor(intent) ?: Destination.keyFor(intent) ?: return
    navigator?.navigate(key) ?: run { pendingLink = key }
  }

  /**
   * Reads the first-run and changelog triggers once. The stored values are updated afterwards off
   * the main thread, so the first frame never waits for a settings write.
   */
  private fun freshStartKeys(intent: Intent?): List<LynketKey> {
    val preferences = Preferences.get(this)
    val firstRun = preferences.firstRunPending()
    val savedVersionCode = preferences.versionCode()
    val changes = buildMap<String, Any?> {
      if (firstRun) put(Preferences.FIRST_RUN, false)
      if (savedVersionCode != BuildConfig.VERSION_CODE) put(Preferences.VERSION_CODE, BuildConfig.VERSION_CODE)
    }
    if (changes.isNotEmpty()) {
      val repository = Preferences.entryPoint(this).userPreferencesRepository()
      lifecycleScope.launch(Dispatchers.IO) {
        runCatching { repository.setValues(changes) }.onFailure { Timber.e(it, "Start values could not be saved") }
      }
    }
    return LynketNavigator.initialKeys(
      isFirstRun = firstRun,
      showChangelog = Changelog.shouldShow(savedVersionCode, BuildConfig.VERSION_CODE),
      deepLink = intent?.let { WebUrlDeepLinkMatcher.keyFor(it) ?: Destination.keyFor(it) },
    )
  }
}
