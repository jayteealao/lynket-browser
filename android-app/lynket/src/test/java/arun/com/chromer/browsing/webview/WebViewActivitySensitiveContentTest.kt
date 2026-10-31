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
import android.webkit.WebView
import arun.com.chromer.shared.Constants
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * On Android 15 and later an incognito page, in a bubble or a tab, is hidden from a screen share;
 * a normal page is not. Below Android 15 the page opens as before.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
// The API 35 and 36 cases run before the API 34 case. Started right after an API 34 page, the first
// API 35 setup sometimes failed inside Robolectric (FileSystemAlreadyExistsException while copying
// its fonts), before any test code ran.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WebViewActivitySensitiveContentTest {
  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private val application: Application get() = RuntimeEnvironment.getApplication()

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  private fun <T : WebViewActivity> open(cls: Class<T>, link: String, incognito: Boolean?): T {
    val intent = Intent(application, cls).setData(Uri.parse(link))
    if (incognito != null) intent.putExtra(Constants.EXTRA_KEY_INCOGNITO, incognito)
    return Robolectric.buildActivity(cls, intent).setup().get()
  }

  /** The activity's own WebView, null when it never made one. */
  private fun WebViewActivity.ownWebView(): WebView? =
    WebViewActivity::class.java.getDeclaredField("webView").apply { isAccessible = true }.get(this) as WebView?

  private fun assertSensitive(activity: WebViewActivity) {
    assertThat(activity.isFinishing).isFalse()
    val webView = activity.ownWebView()!!
    assertThat(webView.contentSensitivity).isEqualTo(View.CONTENT_SENSITIVITY_SENSITIVE)
    assertThat(webView.isContentSensitive).isTrue()
  }

  @Test
  @Config(sdk = [35])
  fun anIncognitoBubbleIsSensitiveContentOnApi35() {
    assertSensitive(open(EmbeddableWebViewActivity::class.java, "https://example.com/private-35", incognito = true))
  }

  @Test
  @Config(sdk = [36])
  fun anIncognitoBubbleIsSensitiveContentOnApi36() {
    assertSensitive(open(EmbeddableWebViewActivity::class.java, "https://example.com/private-36", incognito = true))
  }

  /** The API 34 framework has no content sensitivity method, so any call would throw during setup. */
  @Test
  @Config(sdk = [34])
  fun onApi34AnIncognitoBubbleMakesNoCallAndStillLoads() {
    val link = "https://example.com/private-34"
    val activity = open(EmbeddableWebViewActivity::class.java, link, incognito = true)

    assertThat(activity.isFinishing).isFalse()
    val webView = activity.ownWebView()
    assertThat(webView).isNotNull()
    assertThat(shadowOf(webView!!).lastLoadedUrl).isEqualTo(link)
  }

  @Test
  @Config(sdk = [35])
  fun aNormalBubbleIsNotSensitiveContent() {
    val activity = open(EmbeddableWebViewActivity::class.java, "https://example.com/normal-bubble", incognito = false)

    assertThat(activity.isFinishing).isFalse()
    assertThat(activity.ownWebView()!!.contentSensitivity).isEqualTo(View.CONTENT_SENSITIVITY_AUTO)
  }

  @Test
  @Config(sdk = [35])
  fun anIncognitoTabPageIsSensitiveContent() {
    assertSensitive(open(WebViewActivity::class.java, "https://example.com/private-tab", incognito = true))
  }

  @Test
  @Config(sdk = [35])
  fun aPageWithNoIncognitoExtraIsNotSensitiveContent() {
    val activity = open(WebViewActivity::class.java, "https://example.com/no-extra", incognito = null)

    assertThat(activity.isFinishing).isFalse()
    assertThat(activity.ownWebView()!!.contentSensitivity).isEqualTo(View.CONTENT_SENSITIVITY_AUTO)
  }
}
