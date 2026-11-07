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
import android.webkit.WebViewClient.ERROR_CONNECT
import android.webkit.WebViewClient.ERROR_HOST_LOOKUP
import androidx.compose.runtime.MutableState
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
import org.robolectric.annotation.Config

/**
 * The WebView client shared by Web View mode and the native bubble turns what WebView reports
 * into the page's load state: only the page itself can fail it, a site's own HTTP error page is a
 * loaded page, a certificate error cancels the load, and the finish after a failure keeps it.
 */
@HiltAndroidTest
@UninstallModules(NetworkModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WebViewPageLoadTest {
  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private class FakeNetwork(var online: Boolean = true) : NetworkMonitor {
    override val isOnline: Flow<Boolean> get() = flowOf(online)
    override fun isOnlineNow(): Boolean = online
  }

  @BindValue
  @JvmField
  val network: NetworkMonitor = FakeNetwork()

  private val fakeNetwork get() = network as FakeNetwork
  private val application: Application get() = RuntimeEnvironment.getApplication()
  private val link = "https://example.com/page"

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  private fun <T : WebViewActivity> open(cls: Class<T>): T =
    Robolectric.buildActivity(cls, Intent(application, cls).setData(Uri.parse(link))).setup().get()

  private fun open(): WebViewActivity = open(WebViewActivity::class.java)

  private fun WebViewActivity.webView(): WebView =
    WebViewActivity::class.java.getDeclaredField("webView").apply { isAccessible = true }.get(this) as WebView

  private fun WebViewActivity.client(): WebViewClient = webView().webViewClient

  private fun WebViewActivity.load(): PageLoad {
    val state = WebViewActivity::class.java.getDeclaredField("page\$delegate").apply { isAccessible = true }.get(this)
    return ((state as MutableState<*>).value as WebViewPage).load
  }

  private fun request(mainFrame: Boolean, url: String = link): WebResourceRequest = mockk {
    every { isForMainFrame } returns mainFrame
    every { this@mockk.url } returns Uri.parse(url)
  }

  private fun error(code: Int): WebResourceError = mockk(relaxed = true) { every { errorCode } returns code }

  private fun WebViewActivity.start() = client().onPageStarted(webView(), link, null)

  private fun WebViewActivity.finishLoad(url: String = link) = client().onPageFinished(webView(), url)

  private fun WebViewActivity.fail(code: Int, mainFrame: Boolean = true, url: String = link) =
    client().onReceivedError(webView(), request(mainFrame, url), error(code))

  @Test
  fun anOfflinePageThatCannotLookUpItsHostFailsAsOfflineWithItsAddress() {
    fakeNetwork.online = false
    val activity = open()
    activity.start()

    activity.fail(ERROR_HOST_LOOKUP)

    assertThat(activity.load()).isEqualTo(PageLoad.Failed(LoadFailure.Offline, link))
  }

  @Test
  fun theBubblePageSharesTheClientAndFailsAsOffline() {
    fakeNetwork.online = false
    val activity = open(EmbeddableWebViewActivity::class.java)
    activity.start()

    activity.fail(ERROR_HOST_LOOKUP)

    assertThat(activity.load()).isEqualTo(PageLoad.Failed(LoadFailure.Offline, link))
  }

  @Test
  fun anOnlinePageThatCannotConnectFailsAsCannotReach() {
    val activity = open()
    activity.start()

    activity.fail(ERROR_CONNECT)

    assertThat(activity.load()).isEqualTo(PageLoad.Failed(LoadFailure.CannotReach, link))
  }

  @Test
  fun aFrameThatFailsLeavesThePageLoadingAndThenLoaded() {
    fakeNetwork.online = false
    val activity = open()
    activity.start()

    activity.fail(ERROR_HOST_LOOKUP, mainFrame = false, url = "https://ads.example.net/frame")

    assertThat(activity.load()).isEqualTo(PageLoad.Loading)

    activity.finishLoad()

    assertThat(activity.load()).isEqualTo(PageLoad.Loaded)
  }

  @Test
  fun aSitesOwnNotFoundPageIsLoaded() {
    val activity = open()
    activity.start()
    val notFound: WebResourceResponse = mockk(relaxed = true) { every { statusCode } returns 404 }

    activity.client().onReceivedHttpError(activity.webView(), request(mainFrame = true), notFound)
    activity.finishLoad()

    assertThat(activity.load()).isEqualTo(PageLoad.Loaded)
  }

  @Test
  fun aCertificateErrorCancelsTheLoadAndFailsThePage() {
    val activity = open()
    activity.start()
    val handler: SslErrorHandler = mockk(relaxed = true)
    val sslError: SslError = mockk { every { url } returns link }

    activity.client().onReceivedSslError(activity.webView(), handler, sslError)

    verify { handler.cancel() }
    verify(exactly = 0) { handler.proceed() }
    assertThat(activity.load()).isEqualTo(PageLoad.Failed(LoadFailure.Certificate, link))
  }

  @Test
  fun theFinishAfterAFailedLoadKeepsThePageFailed() {
    val activity = open()
    activity.start()
    activity.fail(ERROR_CONNECT)

    activity.finishLoad()

    assertThat(activity.load()).isEqualTo(PageLoad.Failed(LoadFailure.CannotReach, link))
  }
}
