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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.browsing.article.ReaderState
import arun.com.chromer.browsing.menu.ActionButton
import arun.com.chromer.browsing.menu.BrowsingScreen
import arun.com.chromer.browsing.menu.browsingBottomBarItems
import arun.com.chromer.browsing.menu.browsingMenuItems
import arun.com.chromer.ui.reader.ImageViewerScreen
import arun.com.chromer.ui.reader.ReaderScreen
import arun.com.chromer.ui.reader.readerMenu
import arun.com.chromer.ui.reader.sampleReaderArticle
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
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
 * Goldens of the reader (light, dark, black, fold, loading, error), the image viewer and the web
 * view (with and without the bottom bar). Record with `./gradlew :lynket:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = RW_PHONE)
class ReaderWebViewGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }

  private fun capture(theme: ThemeMode = ThemeMode.Light, animated: Boolean = false, content: @Composable () -> Unit) {
    // The loading skeleton pulses forever, so its frames are advanced by hand.
    if (animated) composeRule.mainClock.autoAdvance = false
    composeRule.setContent { ChromerTheme(themeMode = theme) { content() } }
    if (animated) composeRule.mainClock.advanceTimeBy(1_000) else composeRule.waitForIdle()
    composeRule.onRoot().captureRoboImage()
  }

  @Composable
  private fun Reader(state: ReaderState) = ReaderScreen(
    state = state,
    menuItems = readerMenu(text),
    bottomBarItems = browsingBottomBarItems(BrowsingScreen.Article, true, text),
    textSizeIncrement = 0,
    savedTextSizeIncrement = 0,
    accent = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    onClose = {},
    onMenuItem = {},
    onRetry = {},
    onOpenInBrowser = {},
    onImage = {},
    onKeyword = {},
    onTextSizeChange = {},
    onSaveTextSize = {},
  )

  @Test
  fun reader_light() = capture { Reader(ReaderState.Content(sampleReaderArticle())) }

  @Test
  fun reader_dark() = capture(ThemeMode.Dark) { Reader(ReaderState.Content(sampleReaderArticle())) }

  @Test
  fun reader_black() = capture(ThemeMode.Black) { Reader(ReaderState.Content(sampleReaderArticle())) }

  @Test
  @Config(qualifiers = RW_FOLD)
  fun reader_fold() = capture { Reader(ReaderState.Content(sampleReaderArticle())) }

  @Test
  fun reader_loading() = capture(animated = true) { Reader(ReaderState.Loading) }

  @Test
  fun reader_error() = capture { Reader(ReaderState.Error) }

  /** The viewer with a missing image: Coil fails asynchronously, so the capture waits for its message. */
  @Test
  fun imageViewer() {
    composeRule.setContent { ChromerTheme(themeMode = ThemeMode.Dark) { ImageViewerScreen("file:///nowhere.png", onClose = {}, onShare = {}) } }
    composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("image-error").fetchSemanticsNodes().isNotEmpty() }
    composeRule.onRoot().captureRoboImage()
  }

  @Composable
  private fun WebView(bottomBar: Boolean) = WebViewScreen(
    page = WebViewPage("Android 17 brings resizable apps everywhere", "https://www.theverge.com/2026/10/android-17", 60),
    toolbarColour = Color(0xFF3F51B5),
    menuItems = browsingMenuItems(BrowsingScreen.WebView, bottomBar, ActionButton(text(R.string.share), iconRes = R.drawable.ic_share_24dp), null, text),
    bottomBarItems = browsingBottomBarItems(BrowsingScreen.WebView, bottomBar, text),
    bars = WebViewBarsState(),
    onClose = {},
    onMenuItem = {},
  ) { modifier ->
    Box(modifier.background(Color.White)) {
      Column(Modifier.padding(16.dp)) { Text("The page draws here (a WebView on the device).", color = Color.DarkGray) }
    }
  }

  @Test
  fun webView_chrome() = capture { WebView(bottomBar = true) }

  @Test
  fun webView_bottomBarOff() = capture { WebView(bottomBar = false) }
}

private const val RW_PHONE = "w411dp-h891dp-xxhdpi"
private const val RW_FOLD = "w841dp-h701dp-xxhdpi"
