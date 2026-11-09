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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.browsing.menu.BrowsingScreen
import arun.com.chromer.browsing.menu.browsingBottomBarItems
import arun.com.chromer.browsing.menu.browsingMenuItems
import arun.com.chromer.bubbles.BubblePageCount
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.ui.webview.WebViewBarsState
import arun.com.chromer.ui.webview.WebViewPage
import arun.com.chromer.ui.webview.WebViewScreen
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the open menu of a page in a bubble: Open in full window first, as text, over the bar
 * in the site's colour with the count, in the light and the dark theme. The menu and the bottom bar
 * show what a bubble keeps: History and Minimize, which misbehave in a bubble, are left out. The
 * whole screen is drawn, so the menu's own window is in the picture. Record with `./gradlew :lynket:recordRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class BubbleMenuGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }

  private fun capture(theme: ThemeMode) {
    composeRule.setContent {
      ChromerTheme(themeMode = theme) {
        WebViewScreen(
          page = WebViewPage("Beating the compiler", "https://lemire.me/blog", 100, BubblePageCount(3, 7)),
          toolbarColour = Color(0xFF1E88E5),
          menuItems = browsingMenuItems(BrowsingScreen.WebView, bottomBar = true, actionButton = null, shareWithApp = null, text = text, inBubble = true),
          bottomBarItems = browsingBottomBarItems(BrowsingScreen.WebView, true, text, inBubble = true),
          bars = WebViewBarsState(),
          onClose = {},
          onMenuItem = {},
        ) { modifier -> Box(modifier.background(Color.White)) }
      }
    }
    composeRule.onNodeWithTag("browsing-overflow").performClick()
    composeRule.waitForIdle()
    captureScreenRoboImage()
  }

  @Test
  fun bubbleMenu_light() = capture(ThemeMode.Light)

  @Test
  fun bubbleMenu_dark() = capture(ThemeMode.Dark)
}
