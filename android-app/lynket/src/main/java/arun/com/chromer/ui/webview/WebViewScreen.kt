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

package arun.com.chromer.ui.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import arun.com.chromer.R
import arun.com.chromer.browsing.menu.BrowsingMenuItem
import arun.com.chromer.ui.browsing.BrowsingBottomBar
import arun.com.chromer.ui.browsing.BrowsingTopBarActions
import arun.com.chromer.ui.browsing.HideOnScrollState

/** What the web view's bars show. [progress] is 0..100; at 100 the progress bar hides. */
data class WebViewPage(val title: String, val address: String, val progress: Int)

/**
 * The web view's bars: the top bar collapses while the page scrolls down and returns when it
 * scrolls up (enter-always), and the bottom bar slides away with it, as the legacy toolbar and
 * bottom bar did. A WebView sends Compose no nested scroll, so the host feeds [onPageScroll] from
 * the WebView's scroll changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class WebViewBarsState {
  val top = TopAppBarState(initialHeightOffsetLimit = -Float.MAX_VALUE, initialHeightOffset = 0f, initialContentOffset = 0f)
  val bottom = HideOnScrollState()

  /** The page scrolled from [oldY] to [y] px. */
  fun onPageScroll(y: Int, oldY: Int) {
    if (y <= 0) {
      top.heightOffset = 0f
      bottom.show()
      return
    }
    val dy = (y - oldY).toFloat()
    top.heightOffset -= dy
    bottom.onScroll(dy)
  }
}

/**
 * The web view: a top bar in the website colour with Close, the page title and address and the
 * browsing actions; a progress bar while the page loads; the page; and the bottom bar over the
 * page's end. [page] draws the WebView (the host wraps it in an AndroidView).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
  page: WebViewPage,
  toolbarColour: Color,
  menuItems: List<BrowsingMenuItem>,
  bottomBarItems: List<BrowsingMenuItem>,
  bars: WebViewBarsState,
  onClose: () -> Unit,
  onMenuItem: (BrowsingMenuItem) -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable (Modifier) -> Unit,
) {
  val foreground = if (toolbarColour.luminance() > 0.5f) Color.Black else Color.White
  val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior(state = bars.top, canScroll = { true })
  Scaffold(
    modifier = modifier.fillMaxSize().testTag("webview-screen"),
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top).union(WindowInsets.ime),
    topBar = {
      Column {
        TopAppBar(
          title = {
            Column {
              Text(page.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("webview-title"))
              if (page.address.isNotEmpty() && page.address != page.title) {
                Text(page.address, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("webview-address"))
              }
            }
          },
          navigationIcon = {
            IconButton(onClick = onClose, modifier = Modifier.testTag("webview-close")) {
              Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
            }
          },
          actions = { BrowsingTopBarActions(menuItems, foreground, onMenuItem) },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = toolbarColour,
            scrolledContainerColor = toolbarColour,
            titleContentColor = foreground,
            navigationIconContentColor = foreground,
            actionIconContentColor = foreground,
          ),
          scrollBehavior = behavior,
          modifier = Modifier.testTag("webview-top-bar"),
        )
        if (page.progress in 0 until 100) {
          LinearProgressIndicator(
            progress = { page.progress / 100f },
            color = foreground,
            trackColor = toolbarColour,
            modifier = Modifier.fillMaxWidth().testTag("webview-progress"),
          )
        }
      }
    },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      content(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
      BrowsingBottomBar(
        bottomBarItems,
        containerColour = toolbarColour,
        contentColour = foreground,
        onItem = onMenuItem,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .onSizeChanged { bars.bottom.limit = it.height.toFloat() }
          .graphicsLayer { translationY = -bars.bottom.offset },
      )
    }
  }
}

@Composable
fun rememberWebViewBarsState(): WebViewBarsState = remember { WebViewBarsState() }
