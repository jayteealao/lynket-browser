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
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewClient.ERROR_HOST_LOOKUP
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.bubbles.system.EXTRA_BUBBLE_NOTIFICATION_ID
import arun.com.chromer.data.preferences.BrowsingMode.NativeBubbles
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.util.network.NetworkModule
import arun.com.chromer.util.network.NetworkMonitor
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * A page that did not load shows its cause and Retry in both real web view pages, the native
 * bubble's and Web View mode's, through the one WebView client they share. A failed frame or a
 * site's own error page keeps the page.
 */
@HiltAndroidTest
@UninstallModules(NetworkModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WebViewErrorPageTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createEmptyComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private class FakeNetwork(var online: Boolean = true) : NetworkMonitor {
    override val isOnline: Flow<Boolean> get() = flowOf(online)
    override fun isOnlineNow(): Boolean = online
  }

  @BindValue
  @JvmField
  val network: NetworkMonitor = FakeNetwork()

  @Inject
  lateinit var registry: BubbleRegistry

  private val fakeNetwork get() = network as FakeNetwork
  private val application: Application get() = RuntimeEnvironment.getApplication()
  private val links = (1..7).map { "https://example.com/$it" }
  private val link = links[2]

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  /** Opens Web View mode's page for [link]. */
  private fun openWebView(): WebViewActivity =
    Robolectric.buildActivity(WebViewActivity::class.java, Intent(application, WebViewActivity::class.java).setData(Uri.parse(link))).setup().get()

  /** Opens the page as its bubble does, third of seven bubbles. */
  private fun openBubble(): WebViewActivity {
    links.forEach { registry.add(NativeBubbles, it, incognito = false) }
    val intent = Intent(application, EmbeddableWebViewActivity::class.java)
      .setData(Uri.parse(link))
      .putExtra(EXTRA_BUBBLE_NOTIFICATION_ID, bubbleNotificationId(link))
    return Robolectric.buildActivity(EmbeddableWebViewActivity::class.java, intent).setup().get()
  }

  private fun WebViewActivity.webView(): WebView =
    WebViewActivity::class.java.getDeclaredField("webView").apply { isAccessible = true }.get(this) as WebView

  private fun WebViewActivity.client(): WebViewClient = webView().webViewClient

  private fun request(mainFrame: Boolean, url: String = link): WebResourceRequest = mockk {
    every { isForMainFrame } returns mainFrame
    every { this@mockk.url } returns Uri.parse(url)
  }

  private fun error(code: Int): WebResourceError = mockk(relaxed = true) { every { errorCode } returns code }

  private fun WebViewActivity.start() {
    client().onPageStarted(webView(), link, null)
    composeRule.waitForIdle()
  }

  private fun WebViewActivity.finishLoad() {
    client().onPageFinished(webView(), link)
    composeRule.waitForIdle()
  }

  private fun WebViewActivity.fail(code: Int, mainFrame: Boolean = true, url: String = link) {
    client().onReceivedError(webView(), request(mainFrame, url), error(code))
    composeRule.waitForIdle()
  }

  private fun tag(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun assertNoWayPast() = listOf("Proceed", "Continue", "Open anyway").forEach {
    composeRule.onAllNodes(hasText(it, substring = true, ignoreCase = true), useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun anOfflineBubblePageShowsItsCauseAndRetryUnderCloseAndTheCount() {
    fakeNetwork.online = false
    val activity = openBubble()
    activity.start()

    activity.fail(ERROR_HOST_LOOKUP)

    composeRule.onNodeWithText("You are offline").assertExists()
    composeRule.onNodeWithText("Retry").assertExists()
    tag("webview-close").assertExists()
    tag("webview-count").assertExists()
    tag("browsing-bottom-bar").assertDoesNotExist()
  }

  @Test
  fun anOfflineWebViewModePageShowsTheSameErrorWithNoCount() {
    fakeNetwork.online = false
    val activity = openWebView()
    activity.start()

    activity.fail(ERROR_HOST_LOOKUP)

    composeRule.onNodeWithText("You are offline").assertExists()
    composeRule.onNodeWithText("Retry").assertExists()
    tag("webview-close").assertExists()
    tag("webview-count").assertDoesNotExist()
    tag("browsing-bottom-bar").assertDoesNotExist()
  }

  @Test
  fun aCertificateErrorCancelsTheLoadAndShowsNoWayPastIt() {
    val activity = openWebView()
    activity.start()
    val handler: SslErrorHandler = mockk(relaxed = true)
    val sslError: SslError = mockk { every { url } returns link }

    activity.client().onReceivedSslError(activity.webView(), handler, sslError)
    composeRule.waitForIdle()

    verify { handler.cancel() }
    verify(exactly = 0) { handler.proceed() }
    composeRule.onNodeWithText("This connection is not secure").assertExists()
    composeRule.onNodeWithText("Retry").assertExists()
    assertNoWayPast()
  }

  @Test
  fun aFrameThatFailsKeepsThePage() {
    fakeNetwork.online = false
    val activity = openWebView()
    activity.start()

    activity.fail(ERROR_HOST_LOOKUP, mainFrame = false, url = "http://frame.invalid/")
    activity.finishLoad()

    tag("webview-error").assertDoesNotExist()
    tag("browsing-bottom-bar").assertExists()
  }

  @Test
  fun aSitesOwnNotFoundPageKeepsThePage() {
    val activity = openWebView()
    activity.start()
    val notFound: WebResourceResponse = mockk(relaxed = true) { every { statusCode } returns 404 }

    activity.client().onReceivedHttpError(activity.webView(), request(mainFrame = true), notFound)
    activity.finishLoad()

    tag("webview-error").assertDoesNotExist()
    tag("browsing-bottom-bar").assertExists()
  }

  @Test
  fun theWebViewLeavesOnAFailureAndComesBackOnTheNextLoad() {
    fakeNetwork.online = false
    val activity = openWebView()
    activity.start()
    assertThat(activity.webView().parent).isInstanceOf(SwipeRefreshLayout::class.java)

    activity.fail(ERROR_HOST_LOOKUP)

    tag("webview-error").assertExists()
    assertThat(activity.webView().parent).isNull()

    fakeNetwork.online = true
    activity.start()

    tag("webview-error").assertDoesNotExist()
    assertThat(activity.webView().parent).isInstanceOf(SwipeRefreshLayout::class.java)
  }
}
