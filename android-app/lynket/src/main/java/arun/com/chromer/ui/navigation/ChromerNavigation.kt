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

package arun.com.chromer.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import arun.com.chromer.R
import arun.com.chromer.ui.about.AboutScreen
import arun.com.chromer.ui.changelog.ChangelogDialog
import arun.com.chromer.ui.components.ErrorState
import arun.com.chromer.ui.intro.IntroScreen
import arun.com.chromer.ui.tips.TipsScreen
import arun.com.chromer.util.Utils
import arun.com.chromer.util.WebUrl
import arun.com.chromer.ui.article.ArticleScreen
import arun.com.chromer.ui.browser.BrowserScreen
import arun.com.chromer.ui.history.HistoryScreen
import arun.com.chromer.ui.home.HomeScreen
import arun.com.chromer.ui.providerselection.ProviderSelectionScreen
import arun.com.chromer.ui.perapp.PerAppScreen
import arun.com.chromer.ui.settings.BrowsingModeScreen
import arun.com.chromer.ui.settings.BrowsingOptionsScreen
import arun.com.chromer.ui.settings.LookAndFeelScreen
import arun.com.chromer.ui.settings.SettingsScreen
import arun.com.chromer.ui.tabs.TabsScreen

/**
 * The single navigation display of [arun.com.chromer.MainActivity]: one back stack of [LynketKey]s,
 * each shown with its own saveable state and view model store. No other activity hosts a
 * [NavDisplay]; entry activities show one Compose destination or hand a key to MainActivity.
 *
 * At the root the display does not handle back, so the system closes the activity as before.
 */
@Composable
fun LynketNavDisplay(
  navigator: LynketNavigator,
  modifier: Modifier = Modifier,
  entries: LynketEntries = LynketEntries.Default,
) {
  NavDisplay(
    backStack = navigator.backStack,
    modifier = modifier,
    onBack = { navigator.back() },
    entryDecorators = listOf(
      rememberSaveableStateHolderNavEntryDecorator(),
      rememberViewModelStoreNavEntryDecorator(),
    ),
    sceneStrategies = listOf(DialogSceneStrategy()),
    // A short fade-through between screens, forward and back, in place of the library's 700 ms
    // cross-fade (navigation3-ui 1.2.0 defaultTransitionSpec). Predictive back keeps the library's
    // gesture-driven spec, and the changelog dialog scene is not animated by these specs.
    transitionSpec = { screenFadeThrough() },
    popTransitionSpec = { screenFadeThrough() },
    entryProvider = entryProvider<NavKey> {
      entry<LynketKey.Home> { entries.home(navigator) }
      entry<LynketKey.History> { entries.history(navigator) }
      entry<LynketKey.Tabs> { entries.tabs(navigator) }
      entry<LynketKey.Settings> { SettingsScreen(navigator = navigator) }
      entry<LynketKey.ProviderSelection> { ProviderSelectionScreen(navigator = navigator) }
      entry<LynketKey.About> { AboutScreen(navigator = navigator) }
      entry<LynketKey.Tips> { TipsScreen(navigator = navigator) }
      entry<LynketKey.Intro> { entries.intro(navigator) }
      entry<LynketKey.Changelog>(metadata = DialogSceneStrategy.dialog()) { entries.changelog(navigator) }
      entry<LynketKey.Browser> { key ->
        if (WebUrl.parseOrNull(key.url) == null) {
          LinkErrorScreen(url = key.url, onBack = { navigator.back() })
        } else {
          BrowserScreen(url = key.url, navigator = navigator)
        }
      }
      entry<LynketKey.Article> { key -> ArticleScreen(url = key.url, navigator = navigator) }
      entry<LynketKey.BrowsingMode> { BrowsingModeScreen(navigator = navigator) }
      entry<LynketKey.LookAndFeel> { LookAndFeelScreen(navigator = navigator) }
      entry<LynketKey.BrowsingOptions> { BrowsingOptionsScreen(navigator = navigator) }
      entry<LynketKey.PerApp> { PerAppScreen(navigator = navigator) }
    },
  )
}

/** Duration of the outgoing screen's fade; the incoming screen fades in after it. */
private const val SCREEN_FADE_OUT_MS = 70

/** Duration of the incoming screen's fade; with [SCREEN_FADE_OUT_MS] a change takes 200 ms. */
private const val SCREEN_FADE_IN_MS = 130

/** Material fade-through: the old screen fades out quickly, then the new one fades in, eased out. */
private fun screenFadeThrough(): ContentTransform =
  fadeIn(tween(SCREEN_FADE_IN_MS, delayMillis = SCREEN_FADE_OUT_MS, easing = LinearOutSlowInEasing)) togetherWith
    fadeOut(tween(SCREEN_FADE_OUT_MS, easing = FastOutLinearInEasing))

/** Screens that tests replace with fakes; the app uses [Default]. */
class LynketEntries(
  val home: @Composable (LynketNavigator) -> Unit,
  val history: @Composable (LynketNavigator) -> Unit,
  val tabs: @Composable (LynketNavigator) -> Unit,
  val intro: @Composable (LynketNavigator) -> Unit,
  val changelog: @Composable (LynketNavigator) -> Unit = { navigator -> Changelog(navigator) },
) {
  companion object {
    val Default = LynketEntries(
      home = { navigator -> HomeScreen(navigator = navigator) },
      history = { navigator -> HistoryScreen(onBack = { navigator.back() }) },
      tabs = { navigator -> TabsScreen(onBack = { navigator.back() }, closeIcon = false) },
      intro = { navigator -> IntroScreen(navigator = navigator) },
    )
  }
}

@Composable
private fun Changelog(navigator: LynketNavigator) {
  val context = LocalContext.current
  ChangelogDialog(
    onOk = { navigator.remove(LynketKey.Changelog) },
    onRate = { Utils.openPlayStore(context, context.packageName) },
  )
}

/** A link that is not a web address: the shared error state with the text as it arrived. */
@Composable
internal fun LinkErrorScreen(url: String, onBack: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center,
  ) {
    ErrorState(
      title = stringResource(R.string.link_error_title),
      body = url,
      actionLabel = stringResource(R.string.go_back),
      onAction = onBack,
      modifier = Modifier.testTag("link-error"),
    )
  }
}
