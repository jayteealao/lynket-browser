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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.browsing.menu.BrowsingScreen
import arun.com.chromer.browsing.menu.browsingBottomBarItems
import arun.com.chromer.browsing.menu.browsingMenuItems
import arun.com.chromer.bubbles.BubblePageCount
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.webview.WebViewBarsState
import arun.com.chromer.ui.webview.WebViewPage
import arun.com.chromer.ui.webview.WebViewScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of a page in a bubble in its site's colour: a green site, a very dark and a very light
 * site, and a site with no colour on the app colour. Record with `./gradlew :lynket:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class BubbleSiteColourGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }

  private val page = WebViewPage(
    "Beating the compiler at signed 32-bit division",
    "https://lemire.me/blog/2026/beating-the-compiler",
    100,
    BubblePageCount(3, 7),
  )

  private fun capture(colour: @Composable () -> Color) {
    composeRule.setContent { ChromerTheme { BubblePage(colour()) } }
    composeRule.waitForIdle()
    composeRule.onRoot().captureRoboImage()
  }

  @Composable
  private fun BubblePage(toolbarColour: Color) = WebViewScreen(
    page = page,
    toolbarColour = toolbarColour,
    menuItems = browsingMenuItems(BrowsingScreen.WebView, bottomBar = true, actionButton = null, shareWithApp = null, text = text, inBubble = true),
    bottomBarItems = browsingBottomBarItems(BrowsingScreen.WebView, true, text, inBubble = true),
    bars = WebViewBarsState(),
    onClose = {},
    onMenuItem = {},
  ) { modifier ->
    Box(modifier.background(Color.White)) {
      Column(Modifier.padding(16.dp)) { Text("The page draws here (a WebView on the device).", color = Color.DarkGray) }
    }
  }

  @Test
  fun bubblePage_siteColour() = capture { Color(0xFF0B6E4F) }

  @Test
  fun bubblePage_darkSite() = capture { Color(0xFF0B1F33) }

  @Test
  fun bubblePage_lightSite() = capture { Color(0xFFFFF8E1) }

  @Test
  fun bubblePage_noSiteColour() = capture { colorResource(R.color.colorPrimary) }
}
