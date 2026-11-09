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

package arun.com.chromer.bubbles.system

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import arun.com.chromer.bubbles.BubbleLoadData
import arun.com.chromer.bubbles.bubbleConversationId
import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The page a bubble opens starts in the site's colour: the bubble intent carries the toolbar colour
 * only when the site has one and the bubble is not incognito, and every later post of the same bubble
 * (a late icon, a return from the overflow) keeps it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BubbleSiteColourIntentTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val manager by lazy { BubbleNotificationManager(application) }

  private fun data(siteColor: Int, incognito: Boolean = false) =
    BubbleLoadData(
      Website("Title", URL, null, null, null, null, false, 0, 0),
      fromMinimize = false,
      fromAmp = false,
      incognito = incognito,
      color = Color.RED,
      siteColor = siteColor,
    )

  private fun bubbleIntent(incognito: Boolean = false): Intent =
    shadowOf(application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .allNotifications.single { it.shortcutId == bubbleConversationId(URL, incognito) }
      .let { shadowOf(it.bubbleMetadata!!.intent).savedIntent }

  private fun Intent.toolbarColour(): Int? =
    if (hasExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR)) getIntExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR, 0) else null

  @Test
  fun a_site_with_a_colour_opens_its_page_in_that_colour() {
    runBlocking { manager.showBubbles(data(SITE_COLOUR)) }

    val intent = bubbleIntent()
    assertThat(intent.toolbarColour()).isEqualTo(SITE_COLOUR)
    assertThat(intent.dataString).isEqualTo(URL)
    assertThat(intent.getBooleanExtra(Constants.EXTRA_KEY_INCOGNITO, true)).isFalse()
    assertThat(intent.getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0)).isEqualTo(bubbleNotificationId(URL))
  }

  @Test
  fun a_site_with_no_colour_carries_no_toolbar_colour() {
    runBlocking { manager.showBubbles(data(Constants.NO_COLOR)) }

    assertThat(bubbleIntent().hasExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR)).isFalse()
  }

  @Test
  fun an_incognito_bubble_carries_no_toolbar_colour() {
    runBlocking { manager.showBubbles(data(SITE_COLOUR, incognito = true)) }

    assertThat(bubbleIntent(incognito = true).hasExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR)).isFalse()
  }

  @Test
  fun a_late_icon_keeps_the_site_colour() {
    val posted = runBlocking { manager.showBubbles(data(SITE_COLOUR)) }
    runBlocking { manager.showBubbles(posted, withSummary = false) }
    assertThat(bubbleIntent().toolbarColour()).isEqualTo(SITE_COLOUR)

    val icon = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    runBlocking { manager.showBubbles(posted.copy(icon = icon, color = Color.BLUE), withSummary = false) }

    assertThat(bubbleIntent().toolbarColour()).isEqualTo(SITE_COLOUR)
  }

  @Test
  fun a_returned_bubble_keeps_the_site_colour() {
    val posted = runBlocking { manager.showBubbles(data(SITE_COLOUR)) }

    runBlocking { manager.returnBubble(posted) }

    assertThat(bubbleIntent().toolbarColour()).isEqualTo(SITE_COLOUR)
  }

  private companion object {
    const val URL = "https://colour.example/page"
    val SITE_COLOUR = 0xFF0B6E4F.toInt()
  }
}
