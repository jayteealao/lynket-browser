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

package arun.com.chromer.screenshot

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.browsing.menu.ActionButton
import arun.com.chromer.browsing.menu.BrowsingScreen
import arun.com.chromer.browsing.menu.browsingBottomBarItems
import arun.com.chromer.browsing.menu.browsingMenuItems
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.ui.webview.LoadFailure
import arun.com.chromer.ui.webview.WebViewBarsState
import arun.com.chromer.ui.webview.WebViewPage
import arun.com.chromer.ui.webview.WebViewScreen
import arun.com.chromer.ui.webview.failedPage
import arun.com.chromer.ui.webview.worstFailedPage
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of a page that did not load: each cause in a bubble, offline in Web View mode, the worst
 * content at the largest font, and a split-screen height, in the light and the dark theme. Record
 * with `./gradlew :lynket:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WebPageErrorGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }

  private fun capture(theme: ThemeMode, page: WebViewPage, fontScale: Float = 1f) {
    composeRule.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
        ChromerTheme(themeMode = theme) { Screen(page) }
      }
    }
    composeRule.waitForIdle()
    composeRule.onRoot().captureRoboImage()
  }

  @Composable
  private fun Screen(page: WebViewPage) = WebViewScreen(
    page = page,
    toolbarColour = Color(0xFF3F51B5),
    menuItems = browsingMenuItems(BrowsingScreen.WebView, true, ActionButton(text(R.string.share), iconRes = R.drawable.ic_share_24dp), null, text),
    bottomBarItems = browsingBottomBarItems(BrowsingScreen.WebView, true, text),
    bars = WebViewBarsState(),
    onClose = {},
    onMenuItem = {},
    onRetry = {},
  ) { modifier -> Box(modifier.background(Color.White)) }

  @Test
  fun offline_bubble_light() = capture(ThemeMode.Light, failedPage(LoadFailure.Offline))

  @Test
  fun cannotReach_bubble_light() = capture(ThemeMode.Light, failedPage(LoadFailure.CannotReach))

  @Test
  fun certificate_bubble_light() = capture(ThemeMode.Light, failedPage(LoadFailure.Certificate))

  @Test
  fun general_bubble_light() = capture(ThemeMode.Light, failedPage(LoadFailure.General))

  @Test
  fun offline_bubble_dark() = capture(ThemeMode.Dark, failedPage(LoadFailure.Offline))

  @Test
  fun cannotReach_bubble_dark() = capture(ThemeMode.Dark, failedPage(LoadFailure.CannotReach))

  @Test
  fun certificate_bubble_dark() = capture(ThemeMode.Dark, failedPage(LoadFailure.Certificate))

  @Test
  fun general_bubble_dark() = capture(ThemeMode.Dark, failedPage(LoadFailure.General))

  @Test
  fun offline_webView_light() = capture(ThemeMode.Light, failedPage(LoadFailure.Offline, count = null))

  @Test
  fun offline_webView_dark() = capture(ThemeMode.Dark, failedPage(LoadFailure.Offline, count = null))

  /** A long host as title and address, the last of twelve bubbles, at the largest font. */
  @Test
  fun worst_light() = capture(ThemeMode.Light, worstFailedPage(), fontScale = 2f)

  @Test
  fun worst_dark() = capture(ThemeMode.Dark, worstFailedPage(), fontScale = 2f)

  /** A split-screen window: the picture leaves first, so the title and Retry stay. */
  @Test
  @Config(qualifiers = "w411dp-h360dp-xxhdpi")
  fun splitScreen_light() = capture(ThemeMode.Light, failedPage(LoadFailure.Offline, count = null))

  @Test
  @Config(qualifiers = "w411dp-h360dp-xxhdpi")
  fun splitScreen_dark() = capture(ThemeMode.Dark, failedPage(LoadFailure.Offline, count = null))
}
