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

package arun.com.chromer.tabs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.PrerenderOperationCallback
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.browsing.customtabs.CustomTabs
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.browsing.webview.WebViewStartModule
import arun.com.chromer.browsing.webview.WebViewStarter
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.ui.browsing.isShowingSecondaryBrowserError
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A link sent to the app starts the page engine early when the page will show in a web view or a
 * bubble, once per app run. Nothing is fetched ahead, an incognito link included, and a page
 * engine that fails to start leaves the page to open as before.
 */
@HiltAndroidTest
@UninstallModules(WebViewStartModule::class)
class LinkWarmUpTest : LynketRobolectricSuite() {
  enum class Behaviour { Record, Throw, Error, Silent }

  class FakeStarter : WebViewStarter {
    @Volatile var behaviour = Behaviour.Record
    val configs = CopyOnWriteArrayList<WebViewStartUpConfig>()

    override fun startUp(
      context: Context,
      config: WebViewStartUpConfig,
      receiver: WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException>,
    ) {
      configs += config
      when (behaviour) {
        Behaviour.Record, Behaviour.Silent -> Unit
        Behaviour.Throw -> throw IllegalStateException("no WebView provider")
        Behaviour.Error -> receiver.onError(WebViewStartupException("start-up failed"))
      }
    }
  }

  @BindValue
  @JvmField
  val starter: WebViewStarter = FakeStarter()

  private val fake get() = starter as FakeStarter
  private val shadowApp get() = shadowOf(application)
  private val link = "https://www.example.com/page"

  @After
  fun unmock() {
    unmockkStatic(WebViewCompat::class)
    unmockkObject(CustomTabs.Companion)
  }

  /** Slide over in full incognito mode, with a browser that opens ephemeral tabs or not. */
  private fun fullIncognitoModeWithAProvider(ephemeral: Boolean) {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.SlideOver)
    preferences.fullIncognitoMode(true)
    mockkObject(CustomTabs.Companion)
    every { CustomTabs.getCustomTabSupportingPackages(any()) } returns listOf("com.android.chrome")
    every { CustomTabs.customTabPackage(any()) } returns "com.android.chrome"
    every { CustomTabs.isEphemeralSupported(any(), any()) } returns ephemeral
  }

  @Test
  fun anIncognitoLinkForAnEphemeralTabStartsNoPageEngine() {
    fullIncognitoModeWithAProvider(ephemeral = true)

    intercept(link)
    val opened = awaitNextActivity()

    assertThat(opened?.component?.className).isEqualTo(CustomTabActivity::class.java.name)
    assertThat(opened?.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
    awaitStarts(1, timeoutMs = 500)
    assertThat(fake.configs).isEmpty()
  }

  @Test
  fun anIncognitoLinkForTheWebViewStartsThePageEngineOnce() {
    fullIncognitoModeWithAProvider(ephemeral = false)

    intercept(link)
    val opened = awaitNextActivity()

    assertThat(opened?.component?.className).isEqualTo(WebViewActivity::class.java.name)
    assertThat(opened?.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
    awaitStarts(1)
    assertThat(fake.configs).hasSize(1)
  }

  private fun viewIntent(link: String) =
    Intent(application, BrowserInterceptActivity::class.java)
      .setAction(Intent.ACTION_VIEW)
      .setData(Uri.parse(link))

  /** Starts the intercept activity and waits (bounded) until its link is processed. */
  private fun intercept(link: String, timeoutMs: Long = 5_000): BrowserInterceptActivity {
    val activity = Robolectric.buildActivity(BrowserInterceptActivity::class.java, viewIntent(link)).setup().get()
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      if (activity.isFinishing || activity.isShowingSecondaryBrowserError) return activity
      Thread.sleep(10)
    }
    throw AssertionError("The intercept activity did not process $link within $timeoutMs ms")
  }

  /** The next activity the app started, waited for (bounded); null when none starts. */
  private fun awaitNextActivity(timeoutMs: Long = 3_000): Intent? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      shadowApp.nextStartedActivity?.let { return it }
      Thread.sleep(10)
    }
    return null
  }

  /** Waits (bounded) until the page engine start was asked for at least [count] times. */
  private fun awaitStarts(count: Int, timeoutMs: Long = 3_000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline && fake.configs.size < count) {
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(10)
    }
  }

  @Test
  fun aLinkInNativeBubblesModeStartsTheEngineOnceWithoutMainThreadTasks() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.NativeBubbles)

    Robolectric.buildActivity(BrowserInterceptActivity::class.java, viewIntent(link)).setup()
    awaitStarts(1)

    assertThat(fake.configs).hasSize(1)
    assertThat(fake.configs.single().shouldRunUiThreadStartUpTasks()).isFalse()
  }

  @Test
  fun anIncognitoLinkOpensItsPageAndNothingIsFetchedAhead() {
    clearPreferences()
    mockkStatic(WebViewCompat::class)
    preferences.useWebView(true)
    preferences.fullIncognitoMode(true)

    val activity = intercept(link)
    val opened = awaitNextActivity()

    assertThat(activity.isFinishing).isTrue()
    assertThat(opened?.component?.className).isEqualTo(WebViewActivity::class.java.name)
    assertThat(opened?.dataString).isEqualTo(link)
    assertThat(opened?.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
    verify(exactly = 0) {
      WebViewCompat.prerenderUrlAsync(any<WebView>(), any<String>(), any(), any(), any<PrerenderOperationCallback>())
    }
  }

  private fun assertThePageOpensAsBefore(behaviour: Behaviour) {
    clearPreferences()
    preferences.useWebView(true)
    fake.behaviour = behaviour

    val activity = intercept(link)
    val opened = awaitNextActivity()

    assertThat(fake.configs).hasSize(1)
    assertThat(activity.isFinishing).isTrue()
    assertThat(opened?.component?.className).isEqualTo(WebViewActivity::class.java.name)
    assertThat(opened?.dataString).isEqualTo(link)
  }

  @Test
  fun aStartThatThrowsLeavesThePageToOpenAsBefore() = assertThePageOpensAsBefore(Behaviour.Throw)

  @Test
  fun aStartThatReportsAnErrorLeavesThePageToOpenAsBefore() = assertThePageOpensAsBefore(Behaviour.Error)

  @Test
  fun aStartThatNeverAnswersLeavesThePageToOpenAsBefore() = assertThePageOpensAsBefore(Behaviour.Silent)

  @Test
  fun aSecondLinkStartsNothingMore() {
    clearPreferences()
    preferences.useWebView(true)

    intercept(link)
    awaitNextActivity()
    intercept("https://www.example.com/second")
    awaitNextActivity()

    assertThat(fake.configs).hasSize(1)
  }

  @Test
  fun aRefusedLinkStartsNothing() {
    clearPreferences()
    preferences.useWebView(true)

    val activity = intercept("javascript:alert(1)")

    assertThat(activity.isFinishing).isTrue()
    awaitStarts(1, timeoutMs = 500)
    assertThat(fake.configs).isEmpty()
  }
}
