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

package arun.com.chromer.browsing.webview

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The built-in web view runs JavaScript, so it loads only http and https links. Any other link
 * closes it before a WebView exists, so nothing is loaded.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HiltTestApplication::class)
class WebViewActivitySchemeTest {
  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private val application: Application get() = RuntimeEnvironment.getApplication()

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  private fun open(link: String): WebViewActivity {
    val intent = Intent(application, WebViewActivity::class.java).setData(Uri.parse(link))
    return Robolectric.buildActivity(WebViewActivity::class.java, intent).setup().get()
  }

  /** The activity's own WebView, null when it never made one. */
  private fun WebViewActivity.ownWebView(): WebView? =
    WebViewActivity::class.java.getDeclaredField("webView").apply { isAccessible = true }.get(this) as WebView?

  private fun View.webViews(): List<WebView> = when (this) {
    is WebView -> listOf(this)
    is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).webViews() }
    else -> emptyList()
  }

  @Test
  fun otherSchemesCloseBeforeAnyWebViewLoads() {
    listOf("javascript:alert(1)", "file:///sdcard/x.html", "content://x/y", "intent://x#Intent;end").forEach { link ->
      val activity = open(link)

      assertWithMessage("$link finishing").that(activity.isFinishing).isTrue()
      assertWithMessage("$link own WebView").that(activity.ownWebView()).isNull()
      assertWithMessage("$link WebViews in the window").that(activity.window.decorView.webViews()).isEmpty()
      assertWithMessage("$link started").that(shadowOf(activity).nextStartedActivity).isNull()
    }
  }

  @Test
  fun aWebLinkLoadsAsBefore() {
    listOf("https://example.com/", "HTTPS://example.com/").forEach { link ->
      val activity = open(link)

      assertWithMessage("$link finishing").that(activity.isFinishing).isFalse()
      val webView = activity.ownWebView()
      assertThat(webView).isNotNull()
      assertThat(shadowOf(webView!!).lastLoadedUrl).isEqualTo(link)
    }
  }
}
