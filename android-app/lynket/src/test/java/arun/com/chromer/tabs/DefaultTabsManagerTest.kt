/*
 * Lynket
 *
 * Copyright (C) 2019 Arunkumar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.tabs

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.appdetect.LinkSource
import arun.com.chromer.browsing.amp.AmpResolverActivity
import arun.com.chromer.browsing.backgroundloading.BackgroundLoadingStrategy
import arun.com.chromer.browsing.backgroundloading.BackgroundLoadingStrategyFactory
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.browsing.customtabs.CustomTabs
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.bubbles.system.BubbleGate
import arun.com.chromer.bubbles.system.bubbleConversationId
import arun.com.chromer.bubbles.webheads.WebHeadService
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.MainActivity
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.settings.BrowsingMode
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowSettings
import javax.inject.Inject


/**
 * Created by Arunkumar on 15-12-2017.
 */
@HiltAndroidTest
class DefaultTabsManagerTest : LynketRobolectricSuite() {
  @Inject
  lateinit var tabs: TabsManager

  @BindValue
  @JvmField
  val bubbleGate: BubbleGate = mockk(relaxed = true)

  @BindValue
  @JvmField
  val backgroundLoading: BackgroundLoadingStrategyFactory = mockk(relaxed = true)

  private val webViewLoading: BackgroundLoadingStrategy = mockk(relaxed = true)
  private val customTabLoading: BackgroundLoadingStrategy = mockk(relaxed = true)
  private val articleLoading: BackgroundLoadingStrategy = mockk(relaxed = true)

  private val url = "https://www.example.com"

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  @Before
  fun allowBubbles() {
    ShadowSettings.setCanDrawOverlays(true)
    every { bubbleGate.refusal(any()) } returns null
    every { backgroundLoading[WEB_VIEW] } returns webViewLoading
    every { backgroundLoading[CUSTOM_TAB] } returns customTabLoading
    every { backgroundLoading[ARTICLE] } returns articleLoading
  }

  @Test
  fun testInject() {
    assert(::tabs.isInitialized)
  }

  @Test
  @Ignore("Has issues causing timeout on CI")
  fun testHomeActivityClearedOnExternalIntent() {
    clearPreferences()
    preferences.mergeTabs(false)
    preferences.browsingMode(BrowsingMode.SlideOver)

    val homeActivity = Robolectric.buildActivity(MainActivity::class.java).create().get()

    tabs.openUrl(application, Website(url), fromApp = false)
    assert(homeActivity.isFinishing)
  }

  @Ignore("Needs to be adapted to support Android 10 bubbles")
  @Test
  fun testFromWebheadsDoesNotLaunchNewWebHead() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.WebHeads)
    val shadowApp = shadowOf(application)

    assertWebHeadServiceLaunched(shadowApp)

    tabs.openUrl(application, Website(url), fromApp = false, fromWebHeads = true)
    assert(shadowApp.nextStartedService == null)
  }

  @Test
  fun testAmpResolverOpened() {
    clearPreferences()
    preferences.ampMode(true)

    val shadowApp = shadowOf(application)
    tabs.openUrl(application, Website(url), fromApp = false, fromWebHeads = false)
    assert(
      awaitNextStartedActivity(shadowApp).component == Intent(
        application,
        AmpResolverActivity::class.java
      ).component
    )
  }

  @Test
  fun theAmpLookupCarriesTheAppTheLinkCameFrom() {
    clearPreferences()
    preferences.ampMode(true)

    val shadowApp = shadowOf(application)
    tabs.openUrl(
      application,
      Website(url),
      fromApp = false,
      linkSource = LinkSource(nonFiltered = "com.example.chat", filtered = "com.example.chat"),
    )
    val started = awaitNextStartedActivity(shadowApp)
    assert(started.component == Intent(application, AmpResolverActivity::class.java).component)
    assert(started.getStringExtra(Constants.EXTRA_KEY_LINK_SOURCE_APP) == "com.example.chat")
  }

  /** Web heads mode with aggressive loading: the link opens in a head and its page loads behind it. */
  private fun webHeadsWithAggressiveLoading(webView: Boolean) {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.WebHeads)
    preferences.aggressiveLoading(true)
    preferences.mergeTabs(true)
    preferences.useWebView(webView)
  }

  @Test
  fun webHeadsModeWithAggressiveLoadingOpensAHeadAndLoadsThePageInAWebViewTab() {
    webHeadsWithAggressiveLoading(webView = true)
    val shadowApp = shadowOf(application)

    tabs.openUrl(application, Website(url), fromApp = false)

    val service = awaitNextStartedService(shadowApp)
    assertThat(service.component?.className).isEqualTo(WebHeadService::class.java.name)
    assertThat(service.dataString).isEqualTo(url)
    val tab = awaitNextStartedActivity(shadowApp)
    assertThat(tab.component?.className).isEqualTo(WebViewActivity::class.java.name)
    assertThat(tab.dataString).isEqualTo(url)
    assertThat(shadowApp.nextStartedActivity).isNull()
    verify(exactly = 1) { webViewLoading.prepare(url) }
    verify(exactly = 0) { customTabLoading.prepare(any()) }
  }

  @Test
  fun webHeadsModeWithAggressiveLoadingLoadsThePageInACustomTab() {
    webHeadsWithAggressiveLoading(webView = false)
    mockkObject(CustomTabs.Companion)
    every { CustomTabs.getCustomTabSupportingPackages(any()) } returns listOf("com.android.chrome")
    try {
      val shadowApp = shadowOf(application)

      tabs.openUrl(application, Website(url), fromApp = false)

      assertThat(awaitNextStartedService(shadowApp).component?.className).isEqualTo(WebHeadService::class.java.name)
      val tab = awaitNextStartedActivity(shadowApp)
      assertThat(tab.component?.className).isEqualTo(CustomTabActivity::class.java.name)
      assertThat(tab.dataString).isEqualTo(url)
      verify(exactly = 1) { customTabLoading.prepare(url) }
      verify(exactly = 0) { webViewLoading.prepare(any()) }
    } finally {
      unmockkObject(CustomTabs.Companion)
    }
  }

  @Test
  fun minimizingInWebHeadsModeOpensAHeadAndLoadsNothingInTheBackground() {
    webHeadsWithAggressiveLoading(webView = true)
    val shadowApp = shadowOf(application)

    tabs.minimizeTabByUrl(url, WebViewActivity::class.java.name, incognito = false)

    val service = shadowApp.nextStartedService
    assertThat(service?.component?.className).isEqualTo(WebHeadService::class.java.name)
    assertThat(service?.dataString).isEqualTo(url)
    assertThat(shadowApp.nextStartedActivity).isNull()
    verify(exactly = 0) { backgroundLoading[any()] }
  }

  @Test
  fun nativeBubbleModeOpensALinkInABubbleAndStartsNoWebHead() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.NativeBubbles)
    preferences.aggressiveLoading(true)
    val shadowApp = shadowOf(application)

    tabs.openUrl(application, Website(url), fromApp = false)

    awaitBubbleNotification()
    assertThat(shadowApp.nextStartedService).isNull()
    assertThat(shadowApp.nextStartedActivity).isNull()
    verify(exactly = 0) { backgroundLoading[any()] }
  }

  @Test
  fun nativeBubbleModeMinimizesATabToABubbleAndStartsNoWebHead() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.NativeBubbles)
    preferences.aggressiveLoading(true)
    val shadowApp = shadowOf(application)

    tabs.minimizeTabByUrl(url, CustomTabActivity::class.java.name, incognito = false)

    awaitBubbleNotification()
    assertThat(shadowApp.nextStartedService).isNull()
    assertThat(shadowApp.nextStartedActivity).isNull()
    verify(exactly = 0) { backgroundLoading[any()] }
  }

  @Test
  fun slideOverModeMinimizesToAWebHeadWhenThatIsTheChosenMinimize() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.SlideOver)
    runBlocking {
      userPreferencesRepository.setValue(
        Preferences.MINIMIZE_BEHAVIOR_PREFERENCE,
        Preferences.MINIMIZE_BEHAVIOR_PREFERENCE_KEY,
      )
    }
    val shadowApp = shadowOf(application)

    tabs.minimizeTabByUrl(url, CustomTabActivity::class.java.name, incognito = false)

    val service = shadowApp.nextStartedService
    assertThat(service?.component?.className).isEqualTo(WebHeadService::class.java.name)
    assertThat(service?.dataString).isEqualTo(url)
    verify(exactly = 0) { bubbleGate.refusal(any()) }
  }

  @Test
  fun slideOverModeMinimizesToNothingWithoutThatChoice() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.SlideOver)
    val shadowApp = shadowOf(application)

    tabs.minimizeTabByUrl(url, CustomTabActivity::class.java.name, incognito = false)

    shadowOf(Looper.getMainLooper()).idle()
    assertThat(shadowApp.nextStartedService).isNull()
    assertThat(shadowApp.nextStartedActivity).isNull()
  }

  @Test
  fun slideOverModeOpensALinkInATabAndNoBubble() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.SlideOver)
    preferences.useWebView(true)
    val shadowApp = shadowOf(application)

    tabs.openUrl(application, Website(url), fromApp = false)

    val tab = awaitNextStartedActivity(shadowApp)
    assertThat(tab.component?.className).isEqualTo(WebViewActivity::class.java.name)
    assertThat(shadowApp.nextStartedService).isNull()
    assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    verify(exactly = 0) { bubbleGate.refusal(any()) }
  }

  @Test
  fun webHeadsModeMinimizesToAWebHead() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.WebHeads)
    val shadowApp = shadowOf(application)

    tabs.minimizeTabByUrl(url, CustomTabActivity::class.java.name, incognito = false)

    val service = shadowApp.nextStartedService
    assertThat(service?.component?.className).isEqualTo(WebHeadService::class.java.name)
    assertThat(service?.dataString).isEqualTo(url)
  }

  /** openUrl() starts the service from a background coroutine, so wait for it (bounded). */
  private fun awaitNextStartedService(shadowApp: ShadowApplication, timeoutMs: Long = 5_000): Intent {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      shadowApp.nextStartedService?.let { return it }
      Thread.sleep(10)
    }
    throw AssertionError("No service started within $timeoutMs ms")
  }

  /** The bubble posts on a background thread after the link opens. */
  private fun awaitBubbleNotification() {
    repeat(300) {
      shadowOf(Looper.getMainLooper()).idle()
      if (shadowOf(notificationManager).allNotifications.any { it.shortcutId == bubbleConversationId(url) }) return
      Thread.sleep(10)
    }
    error("No bubble notification was posted")
  }

  /** openUrl() starts the activity from a background coroutine, so wait for it (bounded). */
  private fun awaitNextStartedActivity(shadowApp: ShadowApplication, timeoutMs: Long = 5_000): Intent {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      shadowApp.nextStartedActivity?.let { return it }
      Thread.sleep(10)
    }
    throw AssertionError("No activity started within $timeoutMs ms")
  }

  private fun assertWebHeadServiceLaunched(shadowApp: ShadowApplication) {
    tabs.openUrl(application, Website(url), fromApp = false, fromWebHeads = false)
    assert(
      shadowApp.peekNextStartedService().component == Intent(
        application,
        WebHeadService::class.java
      ).component
    )
    assert(shadowApp.nextStartedService.dataString == url)
  }
}
