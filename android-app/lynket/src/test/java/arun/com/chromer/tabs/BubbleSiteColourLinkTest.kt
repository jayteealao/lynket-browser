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

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.bubbles.system.BubbleGate
import arun.com.chromer.bubbles.system.bubbleIdsOf
import arun.com.chromer.data.common.qualifiers.Disk
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.data.website.stores.WebsiteStore
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import javax.inject.Inject

/**
 * A link opened as a native bubble carries the site's colour to its page only when the person's
 * toolbar colour follows the site, and never for an incognito bubble; the colour of a tab is as before.
 */
@HiltAndroidTest
class BubbleSiteColourLinkTest : LynketRobolectricSuite() {

  @BindValue
  @JvmField
  val bubbleGate: BubbleGate = mockk(relaxed = true)

  @Inject
  lateinit var tabs: TabsManager

  @Inject
  lateinit var settings: Preferences

  @Inject
  @Disk
  lateinit var websiteCache: WebsiteStore

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private fun colourSettings(colouredToolbar: Boolean = true, dynamic: Boolean = true, web: Boolean = true, app: Boolean = false) {
    runBlocking {
      userPreferencesRepository.setValues(
        mapOf(
          Preferences.TOOLBAR_COLOR_PREF to colouredToolbar,
          Preferences.DYNAMIC_COLOR to dynamic,
          Preferences.DYNAMIC_COLOR_WEB to web,
          Preferences.DYNAMIC_COLOR_APP to app,
        )
      )
    }
  }

  private fun cacheColour() {
    runBlocking { websiteCache.saveWebsiteColor(HOST, SITE_COLOUR) }
  }

  /** Opens [website] as a native bubble and reads its page intent once Android has it. */
  private fun bubblePageIntent(website: Website = Website(URL), incognito: Boolean = false): Intent {
    every { bubbleGate.refusal(any()) } returns null
    settings.browsingMode(BrowsingMode.NativeBubbles)

    tabs.openWebHeads(application, website, incognito = incognito)

    var posted: Notification? = null
    repeat(500) {
      shadowOf(Looper.getMainLooper()).idle()
      posted = shadowOf(notificationManager).allNotifications.firstOrNull { it.shortcutId in bubbleIdsOf(URL) }
      if (posted != null) return@repeat
      Thread.sleep(10)
    }
    val notification = checkNotNull(posted) { "No bubble notification was posted" }
    // Robolectric never sets the bubble flag; Android does when it shows the bubble.
    notification.flags = notification.flags or Notification.FLAG_BUBBLE
    return shadowOf(notification.bubbleMetadata!!.intent).savedIntent
  }

  private fun Intent.toolbarColour(): Int? =
    if (hasExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR)) getIntExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR, 0) else null

  @Test
  fun a_cached_site_colour_reaches_the_bubble_page() {
    colourSettings()
    cacheColour()

    assertThat(bubblePageIntent().toolbarColour()).isEqualTo(SITE_COLOUR)
  }

  @Test
  fun the_theme_colour_of_the_site_reaches_the_bubble_page() {
    colourSettings()
    val website = Website(URL).apply { themeColor = "#0B6E4F" }

    assertThat(bubblePageIntent(website).toolbarColour()).isEqualTo(SITE_COLOUR)
  }

  @Test
  fun with_dynamic_colour_off_the_bubble_page_carries_no_colour() {
    colourSettings(dynamic = false)
    cacheColour()

    assertThat(bubblePageIntent().toolbarColour()).isNull()
  }

  @Test
  fun with_the_coloured_toolbar_off_the_bubble_page_carries_no_colour() {
    colourSettings(colouredToolbar = false)
    cacheColour()

    assertThat(bubblePageIntent().toolbarColour()).isNull()
  }

  @Test
  fun the_colour_of_the_source_app_stays_out_of_the_bubble_page() {
    colourSettings(web = false, app = true)
    cacheColour()

    assertThat(bubblePageIntent().toolbarColour()).isNull()
  }

  @Test
  fun an_incognito_bubble_page_carries_no_colour() {
    colourSettings()
    cacheColour()

    assertThat(bubblePageIntent(incognito = true).toolbarColour()).isNull()
  }

  @Test
  fun a_site_with_no_known_colour_carries_no_colour() {
    colourSettings()

    assertThat(bubblePageIntent().toolbarColour()).isNull()
  }

  @Test
  fun the_colour_of_a_tab_is_as_before() {
    settings.browsingMode(BrowsingMode.SlideOver)
    settings.useWebView(true)
    colourSettings()
    cacheColour()

    tabs.openBrowsingTab(application, Website(URL), fromNewTab = false)
    val coloured = shadowOf(application).nextStartedActivity
    assertThat(coloured.component?.className).isEqualTo(WebViewActivity::class.java.name)
    assertThat(coloured.toolbarColour()).isEqualTo(SITE_COLOUR)

    colourSettings(dynamic = false)
    tabs.openBrowsingTab(application, Website(URL), fromNewTab = false)
    val plain = shadowOf(application).nextStartedActivity
    assertThat(plain.toolbarColour()).isEqualTo(settings.toolbarColor())
  }

  private companion object {
    const val HOST = "colour.example"
    const val URL = "https://colour.example/page"
    val SITE_COLOUR = 0xFF0B6E4F.toInt()
  }
}
