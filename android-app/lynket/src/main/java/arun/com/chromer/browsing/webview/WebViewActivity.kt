/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.browsing.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import arun.com.chromer.R
import arun.com.chromer.browsing.BrowsingActivity
import arun.com.chromer.browsing.menu.MenuDelegate
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import arun.com.chromer.ui.webview.WebViewBarsState
import arun.com.chromer.ui.webview.WebViewPage
import arun.com.chromer.ui.webview.WebViewScreen
import arun.com.chromer.util.ColorUtil
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * The built-in web view. It keeps its class name and manifest entry (tab detection and native
 * Bubbles, through [EmbeddableWebViewActivity], start it by name). The WebView is a View created
 * once per activity and shown inside the Compose screen through an AndroidView, wrapped by a
 * SwipeRefreshLayout for pull to refresh. The manifest handles rotation, fold and bubble resize
 * itself, so the page and its history stay; saveState covers process death.
 */
@AndroidEntryPoint
open class WebViewActivity : BrowsingActivity() {
  @Inject
  lateinit var menuDelegate: MenuDelegate

  private var webView: WebView? = null
  private var swipeRefresh: SwipeRefreshLayout? = null
  private val bars = WebViewBarsState()
  private var page by mutableStateOf(WebViewPage(title = "", address = "", progress = 0))
  private var toolbarColour by mutableIntStateOf(0)

  // Back goes through the page history first; with predictive back (targetSdk 36) the system no
  // longer calls onBackPressed, so the callback is enabled only while the page can go back.
  private val webBackCallback = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() {
      webView?.goBack()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val url = intent?.dataString ?: return // BrowsingActivity already closed with a message.
    toolbarColour = intent.getIntExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR, ContextCompat.getColor(this, R.color.colorPrimary))
    page = page.copy(title = website?.safeLabel() ?: url, address = url)

    val view = try {
      WebView(this)
    } catch (e: Exception) {
      // The WebView package is missing or disabled.
      Timber.e(e)
      Toast.makeText(this, R.string.web_view_not_found, Toast.LENGTH_LONG).show()
      finish()
      return
    }
    Timber.d("WebView created")
    webView = view
    setupWebView(view)
    val restored = savedInstanceState?.getBundle(KEY_WEB_VIEW_STATE)?.let { view.restoreState(it) } != null
    if (!restored) view.loadUrl(url)
    onBackPressedDispatcher.addCallback(this, webBackCallback)

    val menuItems = menuDelegate.menuItems()
    val bottomBarItems = menuDelegate.bottomBarItems()
    setContent {
      ChromerThemeFromSettings(applySystemBars = false) {
        WebViewScreen(
          page = page,
          toolbarColour = Color(toolbarColour),
          menuItems = menuItems,
          bottomBarItems = bottomBarItems,
          bars = bars,
          onClose = ::finish,
          onMenuItem = { menuDelegate.handleItemSelected(it.id) },
          modifier = Modifier.semantics { testTagsAsResourceId = true },
        ) { modifier ->
          AndroidView(
            factory = { context ->
              SwipeRefreshLayout(context).apply {
                (view.parent as? ViewGroup)?.removeView(view)
                addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                setOnRefreshListener { view.reload() }
                setColorSchemeColors(toolbarColour, ColorUtil.getClosestAccentColor(toolbarColour))
                swipeRefresh = this
              }
            },
            onRelease = { layout ->
              layout.removeAllViews()
              swipeRefresh = null
            },
            modifier = modifier,
          )
        }
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView(view: WebView) {
    view.webViewClient = object : WebViewClient() {
      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let {
          page = page.copy(address = it)
          loadWebsiteDetails(it)
        }
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        swipeRefresh?.isRefreshing = false
      }

      override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        webBackCallback.isEnabled = view?.canGoBack() == true
      }

      // The page's renderer runs in its own process, which the system may stop under memory
      // pressure. Unhandled, that ends the whole app; instead the dead WebView is released and
      // the screen starts again with the link.
      // Called on API 26 and later only.
      @RequiresApi(Build.VERSION_CODES.O)
      override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Timber.w("Page renderer gone (crashed: %b); reloading the page", detail?.didCrash() == true)
        if (view != null) onRendererGone(view)
        return true
      }
    }
    view.webChromeClient = object : WebChromeClient() {
      override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        if (!title.isNullOrEmpty()) page = page.copy(title = title)
      }

      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        page = page.copy(progress = newProgress)
      }
    }
    view.settings.javaScriptEnabled = true
    view.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY -> bars.onPageScroll(scrollY, oldScrollY) }
  }

  /**
   * Releases a WebView whose renderer is gone and builds the screen again on the page the person
   * was on. A page that stops the renderer again and again is not reloaded more than
   * [MAX_RENDERER_RECOVERIES] times; then the screen closes with a message.
   */
  private fun onRendererGone(view: WebView) {
    if (webView !== view) return
    webView = null
    // The back and forward list lives in the app process, so it survives the renderer.
    stateAfterRendererGone = runCatching { Bundle().also { view.saveState(it) } }.getOrNull()
    (view.parent as? ViewGroup)?.removeView(view)
    view.destroy()
    if (isFinishing || isDestroyed) return
    val recoveries = intent.getIntExtra(EXTRA_RENDERER_RECOVERIES, 0)
    if (recoveries >= MAX_RENDERER_RECOVERIES) {
      Toast.makeText(this, R.string.web_view_page_stopped, Toast.LENGTH_LONG).show()
      finish()
      return
    }
    intent.putExtra(EXTRA_RENDERER_RECOVERIES, recoveries + 1)
    recreate()
  }

  /** The page state saved when the renderer stopped, kept for the screen built again. */
  private var stateAfterRendererGone: Bundle? = null

  override fun getCurrentUrl(): String = webView?.url ?: super.getCurrentUrl()

  override fun onWebsiteLoaded(website: Website) {
    page = page.copy(title = website.safeLabel(), address = website.url)
  }

  override fun onToolbarColorSet(websiteThemeColor: Int) {
    super.onToolbarColorSet(websiteThemeColor)
    toolbarColour = websiteThemeColor
    swipeRefresh?.setColorSchemeColors(websiteThemeColor, ColorUtil.getClosestAccentColor(websiteThemeColor))
    // Status bar icons in the toolbar's foreground colour, as the legacy toolbar's content.
    val lightContent = ColorUtil.getForegroundWhiteOrBlack(websiteThemeColor) == AndroidColor.WHITE
    enableEdgeToEdge(
      statusBarStyle = if (lightContent) {
        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
      } else {
        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
      },
    )
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    val state = webView?.let { view -> Bundle().also { view.saveState(it) } } ?: stateAfterRendererGone
    state?.let { outState.putBundle(KEY_WEB_VIEW_STATE, it) }
  }

  override fun onDestroy() {
    webView?.let { view ->
      (view.parent as? ViewGroup)?.removeView(view)
      view.destroy()
    }
    webView = null
    super.onDestroy()
  }

  private companion object {
    const val KEY_WEB_VIEW_STATE = "web_view_state"
    const val EXTRA_RENDERER_RECOVERIES = "arun.com.chromer.extra.RENDERER_RECOVERIES"
    const val MAX_RENDERER_RECOVERIES = 2
  }
}
