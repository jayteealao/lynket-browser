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
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.bubbles.BubbleLoadData
import arun.com.chromer.bubbles.bubbleConversationId
import arun.com.chromer.bubbles.system.BubbleNotificationManager
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.readableContentColor
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject
import kotlin.math.abs

/**
 * The page a bubble opens draws its top bar and bottom bar in the site's colour from the first
 * frame, keeps the app colour for a site with no colour, takes a colour the page finds later, and
 * draws its title in the readable colour of the bar.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BubbleSiteColourPageTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createEmptyComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  @Inject
  lateinit var notifications: BubbleNotificationManager

  private val application: Application get() = RuntimeEnvironment.getApplication()

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  /** Posts a real bubble for [URL] and returns the intent its page opens with. */
  private fun postedIntent(siteColor: Int): Intent {
    runBlocking {
      notifications.showBubbles(
        BubbleLoadData(Website("Colour", URL, null, null, null, null, false, 0, 0), false, false, false, siteColor = siteColor)
      )
    }
    val manager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return shadowOf(manager).allNotifications.single { it.shortcutId == bubbleConversationId(URL) }
      .let { shadowOf(it.bubbleMetadata!!.intent).savedIntent }
  }

  private fun openPage(siteColor: Int): EmbeddableWebViewActivity =
    Robolectric.buildActivity(EmbeddableWebViewActivity::class.java, postedIntent(siteColor)).setup().get()

  private fun EmbeddableWebViewActivity.toolbarColour(): Int =
    WebViewActivity::class.java.getDeclaredMethod("getToolbarColour").apply { isAccessible = true }.invoke(this) as Int

  private fun image(tag: String): Bitmap =
    composeRule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()

  /** A point in the top bar's padding: 2 px above its bottom edge, at its centre. */
  private fun topBarColour(): Int = image("webview-top-bar").let { it.getPixel(it.width / 2, it.height - 3) }

  /** A point in the bottom bar's padding: 2 px below its top edge, at its centre. */
  private fun bottomBarColour(): Int = image("browsing-bottom-bar").let { it.getPixel(it.width / 2, 2) }

  private fun near(a: Int, b: Int): Boolean =
    listOf(16, 8, 0).all { shift -> abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) <= 2 }

  private fun assertNear(actual: Int, expected: Int) =
    assertWithMessage("#%06X near #%06X".format(actual and 0xFFFFFF, expected and 0xFFFFFF)).that(near(actual, expected)).isTrue()

  @Test
  fun the_bars_open_in_the_site_colour() {
    val page = openPage(SITE_COLOUR)

    assertThat(page.toolbarColour()).isEqualTo(SITE_COLOUR)
    assertNear(topBarColour(), SITE_COLOUR)
    assertNear(bottomBarColour(), SITE_COLOUR)

    composeRule.waitForIdle()
    assertThat(page.toolbarColour()).isEqualTo(SITE_COLOUR)
  }

  @Test
  fun a_site_with_no_colour_keeps_the_app_colour() {
    val primary = ContextCompat.getColor(application, R.color.colorPrimary)
    val page = openPage(Constants.NO_COLOR)

    assertThat(page.toolbarColour()).isEqualTo(primary)
    assertNear(topBarColour(), primary)
    assertNear(bottomBarColour(), primary)
  }

  @Test
  fun a_colour_the_page_finds_later_replaces_it() {
    val page = openPage(SITE_COLOUR)
    // The page has settled on the colour it opened with before it finds another.
    composeRule.waitForIdle()

    WebViewActivity::class.java.getDeclaredMethod("onToolbarColorSet", Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(page, LATER_COLOUR)
    composeRule.waitForIdle()

    assertThat(page.toolbarColour()).isEqualTo(LATER_COLOUR)
    assertNear(topBarColour(), LATER_COLOUR)
  }

  @Test
  fun the_title_is_readable_on_a_very_dark_site_colour() = assertTitleIn(DARK_SITE, android.graphics.Color.WHITE)

  @Test
  fun the_title_is_readable_on_a_very_light_site_colour() = assertTitleIn(LIGHT_SITE, android.graphics.Color.BLACK)

  private fun assertTitleIn(siteColour: Int, expected: Int) {
    assertThat(Color(siteColour).readableContentColor().toArgb()).isEqualTo(expected)
    openPage(siteColour)

    val title = image("webview-title")
    val pixels = IntArray(title.width * title.height).also { title.getPixels(it, 0, title.width, 0, 0, title.width, title.height) }
    assertThat(pixels.any { near(it, expected) }).isTrue()
  }

  private companion object {
    const val URL = "https://colour.example/page"
    val SITE_COLOUR = 0xFF0B6E4F.toInt()
    val LATER_COLOUR = 0xFF2E7D32.toInt()
    val DARK_SITE = 0xFF0B1F33.toInt()
    val LIGHT_SITE = 0xFFFFF8E1.toInt()
  }
}
