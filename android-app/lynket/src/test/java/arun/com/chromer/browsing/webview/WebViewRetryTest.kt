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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewClient.ERROR_CONNECT
import android.webkit.WebViewClient.ERROR_HOST_LOOKUP
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.bubbles.system.EXTRA_BUBBLE_NOTIFICATION_ID
import arun.com.chromer.data.preferences.BrowsingMode.NativeBubbles
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.webview.LoadFailure
import arun.com.chromer.ui.webview.PageLoad
import arun.com.chromer.ui.webview.WebViewPage
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Retry on a page that did not load, in both real web view pages (the native bubble's and Web View
 * mode's): it loads the address that failed again, never a reload of the WebView's own error page;
 * while it runs Retry is disabled with the progress bar; then the page shows in the same window, or
 * the error shows again with its new cause.
 */
@HiltAndroidTest
@UninstallModules(NetworkModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WebViewRetryTest {
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

  /** The address that failed: a redirect moved the page, so it differs from the link. */
  private val failed = "https://example.com/3?moved"

  @Before
  fun setUp() {
    hiltRule.inject()
  }

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

  @Suppress("UNCHECKED_CAST") // sdlc-debt: reads the activity's private page state; a test seam would replace it.
  private fun WebViewActivity.page(): WebViewPage =
    (WebViewActivity::class.java.getDeclaredField("page\$delegate").apply { isAccessible = true }.get(this) as MutableState<WebViewPage>).value

  private fun WebViewActivity.client(): WebViewClient = webView().webViewClient

  private fun request(url: String): WebResourceRequest = mockk {
    every { isForMainFrame } returns true
    every { this@mockk.url } returns Uri.parse(url)
  }

  private fun error(code: Int): WebResourceError = mockk(relaxed = true) { every { errorCode } returns code }

  private fun WebViewActivity.start(url: String = link) {
    client().onPageStarted(webView(), url, null)
    composeRule.waitForIdle()
  }

  private fun WebViewActivity.progress(value: Int) {
    webView().webChromeClient!!.onProgressChanged(webView(), value)
    composeRule.waitForIdle()
  }

  private fun WebViewActivity.finishLoad(url: String = failed) {
    client().onPageFinished(webView(), url)
    composeRule.waitForIdle()
  }

  private fun WebViewActivity.fail(code: Int, url: String = failed) {
    client().onReceivedError(webView(), request(url), error(code))
    composeRule.waitForIdle()
  }

  private fun WebViewActivity.title(title: String) {
    webView().webChromeClient!!.onReceivedTitle(webView(), title)
    composeRule.waitForIdle()
  }

  private fun tapRetry() {
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.waitForIdle()
  }

  private fun tag(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

  /** Fails [activity] offline at [failed], then taps Retry. */
  private fun failOfflineAndRetry(activity: WebViewActivity) {
    fakeNetwork.online = false
    activity.start()
    activity.fail(ERROR_HOST_LOOKUP)
    tapRetry()
  }

  private fun assertRetrying(activity: WebViewActivity) {
    assertThat(shadowOf(activity.webView()).lastLoadedUrl).isEqualTo(failed)
    assertThat(shadowOf(activity.webView()).reloadInvocations).isEqualTo(0)
    composeRule.onNodeWithText("Retry").assertIsNotEnabled()
    tag("webview-progress").assertExists()
    composeRule.onNodeWithText("You are offline").assertExists()
    tag("browsing-bottom-bar").assertDoesNotExist()
  }

  /** The network is back: the retry starts, reports progress and finishes, and the page shows. */
  private fun assertRetryLoadsThePage(activity: WebViewActivity) {
    fakeNetwork.online = true
    activity.start(failed)
    activity.progress(60)
    tag("webview-error").assertExists()
    tag("webview-progress").assertExists()

    activity.finishLoad()

    tag("webview-error").assertDoesNotExist()
    assertThat(activity.webView().parent).isInstanceOf(SwipeRefreshLayout::class.java)
    tag("browsing-bottom-bar").assertExists()
  }

  @Test
  fun retryInABubbleLoadsTheFailedAddressAndThePageShowsInTheSameBubble() {
    val activity = openBubble()
    failOfflineAndRetry(activity)
    assertRetrying(activity)

    assertRetryLoadsThePage(activity)

    composeRule.onAllNodes(hasText("3 of 7", substring = true), useUnmergedTree = true).assertCountEquals(1)
  }

  @Test
  fun retryInWebViewModeLoadsTheFailedAddressAndThePageShows() {
    val activity = openWebView()
    failOfflineAndRetry(activity)
    assertRetrying(activity)

    assertRetryLoadsThePage(activity)

    tag("webview-count").assertDoesNotExist()
  }

  @Test
  fun aSecondTapDuringARetryDoesNothingMore() {
    val activity = openWebView()
    failOfflineAndRetry(activity)
    activity.progress(40)

    tapRetry()

    assertThat(activity.page().progress).isEqualTo(40)
    assertThat((activity.page().load as PageLoad.Failed).retrying).isTrue()
    assertThat(shadowOf(activity.webView()).lastLoadedUrl).isEqualTo(failed)
    assertThat(shadowOf(activity.webView()).reloadInvocations).isEqualTo(0)
  }

  @Test
  fun aRetryStillOfflineShowsTheErrorAgainAndOnlineTheNewCause() {
    val activity = openWebView()
    failOfflineAndRetry(activity)

    activity.start(failed)
    activity.fail(ERROR_HOST_LOOKUP)
    activity.finishLoad()

    composeRule.onNodeWithText("You are offline").assertExists()
    composeRule.onNodeWithText("Retry").assertIsEnabled()
    tag("webview-progress").assertDoesNotExist()

    fakeNetwork.online = true
    tapRetry()
    activity.fail(ERROR_CONNECT)

    composeRule.onNodeWithText("This site cannot be reached").assertExists()
    composeRule.onNodeWithText("Retry").assertIsEnabled()
    assertThat(activity.page().load).isEqualTo(PageLoad.Failed(LoadFailure.CannotReach, failed))
  }

  @Test
  fun aRetryOfACertificateErrorIsCancelledAgainWithNoWayPastIt() {
    val activity = openWebView()
    activity.start()
    // The redirect that moved the page reaches the client before the certificate error.
    activity.client().shouldOverrideUrlLoading(activity.webView(), request(failed))
    val handler: SslErrorHandler = mockk(relaxed = true)
    val sslError: SslError = mockk { every { url } returns failed }
    activity.client().onReceivedSslError(activity.webView(), handler, sslError)
    composeRule.waitForIdle()

    tapRetry()
    assertThat(shadowOf(activity.webView()).lastLoadedUrl).isEqualTo(failed)
    activity.client().onReceivedSslError(activity.webView(), handler, sslError)
    composeRule.waitForIdle()

    verify(exactly = 2) { handler.cancel() }
    verify(exactly = 0) { handler.proceed() }
    composeRule.onNodeWithText("This connection is not secure").assertExists()
    composeRule.onNodeWithText("Retry").assertIsEnabled()
    listOf("Proceed", "Continue", "Open anyway").forEach {
      composeRule.onAllNodes(hasText(it, substring = true, ignoreCase = true), useUnmergedTree = true).assertCountEquals(0)
    }
  }

  @Test
  fun aFailedPageIsTitledWithItsSiteAndALoadedRetryWithThePageTitle() {
    val activity = openWebView()
    fakeNetwork.online = false
    activity.start()
    activity.fail(ERROR_HOST_LOOKUP)

    activity.title("Webpage not available")

    tag("webview-title").assertTextEquals("example.com")
    composeRule.onAllNodes(hasText("Webpage not available"), useUnmergedTree = true).assertCountEquals(0)

    fakeNetwork.online = true
    tapRetry()
    activity.start(failed)
    activity.finishLoad()
    activity.title("Example Domain")

    tag("webview-title").assertTextEquals("Example Domain")
  }

  /** The site details lookup ends with no title for a page that failed, or that a retry loaded. */
  @Test
  fun siteDetailsWithNoTitleKeepTheSiteOfAFailedPageAndThePageTitleAfterARetry() {
    val activity = openBubble()
    fakeNetwork.online = false
    activity.start()
    activity.fail(ERROR_HOST_LOOKUP)

    activity.onWebsiteLoaded(Website(failed))
    composeRule.waitForIdle()

    tag("webview-title").assertTextEquals("example.com")

    fakeNetwork.online = true
    tapRetry()
    activity.start(failed)
    activity.title("Example Domain")
    activity.finishLoad()
    activity.onWebsiteLoaded(Website(failed))
    composeRule.waitForIdle()

    tag("webview-title").assertTextEquals("Example Domain")
  }
}
