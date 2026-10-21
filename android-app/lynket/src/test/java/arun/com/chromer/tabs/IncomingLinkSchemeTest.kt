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

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.Process
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity
import arun.com.chromer.data.apps.AppRepository
import arun.com.chromer.data.history.HistoryRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.ui.browsing.isShowingSecondaryBrowserError
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import javax.inject.Inject

/**
 * Another app can send BrowserInterceptActivity an explicit intent with any link. Only http and
 * https links open; every other scheme closes the activity and opens nothing. Web links open
 * exactly as the unchanged open path opens them.
 */
@HiltAndroidTest
class IncomingLinkSchemeTest : LynketRobolectricSuite() {
  @Inject
  lateinit var tabs: TabsManager

  @Inject
  lateinit var history: HistoryRepository

  @Inject
  lateinit var appRepository: AppRepository

  private val shadowApp get() = shadowOf(application)

  private val refusedLinks = listOf(
    "javascript:alert(1)",
    "file:///sdcard/x.html",
    "content://x/y",
    "intent://x#Intent;end",
  )

  private val webLinks = listOf(
    "http://www.example.com",
    "https://www.example.com",
    "HTTPS://www.example.com",
  )

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

  /** The next activity or service the app started, waited for (bounded); null when none starts. */
  private fun awaitNextStart(timeoutMs: Long = 3_000): Intent? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      shadowApp.nextStartedActivity?.let { return it }
      shadowApp.nextStartedService?.let { return it }
      Thread.sleep(10)
    }
    return null
  }

  private fun assertRefusedOpensNothing(mode: String) {
    refusedLinks.forEach { link ->
      val activity = intercept(link)

      assertWithMessage("$mode $link finishing").that(activity.isFinishing).isTrue()
      assertThat(activity.isShowingSecondaryBrowserError).isFalse()
      assertWithMessage("$mode $link started").that(awaitNextStart(timeoutMs = 500)).isNull()
      assertWithMessage("$mode $link history").that(runBlocking { history.get(Website(link)) }).isNull()
    }
  }

  @Test
  fun otherSchemesOpenNothingInTheDefaultMode() {
    clearPreferences()
    assertRefusedOpensNothing("default")
  }

  @Test
  fun otherSchemesOpenNothingWithWebHeadsOn() {
    clearPreferences()
    preferences.webHeads(true)
    assertRefusedOpensNothing("web heads")
  }

  @Test
  fun otherSchemesOpenNothingWithNativeBubblesOn() {
    clearPreferences()
    preferences.nativeBubbles(true)
    assertRefusedOpensNothing("native bubbles")
  }

  @Test
  fun otherSchemesOpenNothingInWebViewMode() {
    clearPreferences()
    preferences.useWebView(true)
    assertRefusedOpensNothing("web view")
  }

  @Test
  fun otherSchemesOpenNothingWithAmpAndArticleModeOn() {
    clearPreferences()
    preferences.ampMode(true)
    assertRefusedOpensNothing("amp")
    clearPreferences()
    preferences.articleMode(true)
    assertRefusedOpensNothing("article")
  }

  private fun assertSameAsTheOpenPath(mode: String) {
    webLinks.forEach { link ->
      val activity = intercept(link)
      assertWithMessage("$mode $link finishing").that(activity.isFinishing).isTrue()
      val fromIntercept = awaitNextStart()
      assertWithMessage("$mode $link intercept start").that(fromIntercept).isNotNull()

      tabs.openUrl(application, Website(link), fromApp = false)
      val fromOpenPath = awaitNextStart()
      assertWithMessage("$mode $link open-path start").that(fromOpenPath).isNotNull()

      assertWithMessage("$mode $link component").that(fromIntercept!!.component).isEqualTo(fromOpenPath!!.component)
      assertWithMessage("$mode $link data").that(fromIntercept.dataString).isEqualTo(fromOpenPath.dataString)
    }
  }

  @Test
  fun webLinksOpenAsTheOpenPathOpensThemInTheDefaultMode() {
    clearPreferences()
    assertSameAsTheOpenPath("default")
  }

  @Test
  fun webLinksOpenAsTheOpenPathOpensThemWithAmpModeOn() {
    clearPreferences()
    preferences.ampMode(true)
    assertSameAsTheOpenPath("amp")
  }

  private fun linkFrom(pkg: String) {
    shadowOf(application.getSystemService(AppOpsManager::class.java))
      .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), application.packageName, AppOpsManager.MODE_ALLOWED)
    shadowOf(application.getSystemService(UsageStatsManager::class.java))
      .addEvent(pkg, System.currentTimeMillis() - 500, UsageEvents.Event.ACTIVITY_RESUMED)
  }

  @Test
  fun aWebLinkFromABlacklistedAppStillFollowsItsRule() {
    clearPreferences()
    preferences.perAppSettings(true)
    runBlocking { appRepository.setPackageBlacklisted(SOURCE_APP) }
    linkFrom(SOURCE_APP)

    val activity = intercept("https://www.example.com")

    // No secondary browser is set, so the blacklist rule shows its error and keeps the activity open.
    assertThat(activity.isShowingSecondaryBrowserError).isTrue()
    assertThat(activity.isFinishing).isFalse()
  }

  @Test
  fun anOtherSchemeFromABlacklistedAppIsRefusedBeforeTheRule() {
    clearPreferences()
    preferences.perAppSettings(true)
    runBlocking { appRepository.setPackageBlacklisted(SOURCE_APP) }
    linkFrom(SOURCE_APP)

    val activity = intercept("javascript:alert(1)")

    assertThat(activity.isShowingSecondaryBrowserError).isFalse()
    assertThat(activity.isFinishing).isTrue()
    assertThat(awaitNextStart(timeoutMs = 500)).isNull()
  }

  private companion object {
    const val SOURCE_APP = "com.example.chat"
  }
}
