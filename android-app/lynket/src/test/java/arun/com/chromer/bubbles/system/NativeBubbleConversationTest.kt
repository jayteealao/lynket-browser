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
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.LocusId
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Point
import android.graphics.Rect
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.util.Utils
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.ref.WeakReference

/**
 * A bubble is a message conversation: its notification carries the locus id of its shortcut, it
 * may be suppressed on Android 12 and later, and its expanded height follows the window that
 * posted it, not the whole display.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NativeBubbleConversationTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val manager by lazy { BubbleNotificationManager(application) }

  private fun website(url: String) =
    Website(TITLE, url, null, null, null, null, false, 0, 0)

  private fun post(url: String, incognito: Boolean = false, context: Context? = null) {
    runBlocking {
      manager.showBubbles(BubbleLoadData(website(url), false, false, incognito, contextRef = WeakReference(context)))
    }
  }

  private fun notificationOf(url: String): Notification =
    shadowOf(notificationManager).allNotifications.single { it.shortcutId in bubbleIdsOf(url) }

  private fun shortcutOf(url: String): ShortcutInfo =
    application.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single { it.id in bubbleIdsOf(url) }

  /** A posting context whose window is [heightPx] tall and 1080 px wide, as in split screen. */
  private fun windowContext(heightPx: Int): Context {
    val windowManager = mockk<WindowManager> {
      every { currentWindowMetrics } returns WindowMetrics(Rect(0, 0, 1080, heightPx), WindowInsets.CONSUMED)
    }
    return object : ContextWrapper(application) {
      override fun getSystemService(name: String): Any? =
        if (name == Context.WINDOW_SERVICE) windowManager else super.getSystemService(name)
    }
  }

  private fun assertMessageWithLocusOfShortcut(url: String) {
    val notification = notificationOf(url)
    assertThat(notification.category).isEqualTo(Notification.CATEGORY_MESSAGE)
    assertThat(notification.locusId).isEqualTo(LocusId(notification.shortcutId))
    assertThat(shortcutOf(url).locusId).isEqualTo(notification.locusId)
  }

  private fun assertHeightFollowsTheWindow() {
    @Suppress("DEPRECATION") // sdlc-debt: the test reads the display size the old code used, to show the bubble no longer does.
    val displayPx = Point().apply(application.getSystemService(WindowManager::class.java).defaultDisplay::getSize).y
    assertThat(displayPx).isAtLeast(2_000)

    post(URL, context = windowContext(1000))

    val desiredHeight = notificationOf(URL).bubbleMetadata!!.desiredHeight
    assertThat(desiredHeight).isEqualTo(Utils.pxToDp(800))
    assertThat(desiredHeight).isNotEqualTo(Utils.pxToDp((displayPx * 0.8).toInt()))
  }

  @Test
  fun a_bubble_is_a_message_with_the_locus_id_of_its_shortcut() {
    post(URL)

    assertMessageWithLocusOfShortcut(URL)
  }

  @Test
  fun an_incognito_bubble_is_a_message_with_the_locus_id_of_its_shortcut() {
    post(INCOGNITO_URL, incognito = true)

    assertMessageWithLocusOfShortcut(INCOGNITO_URL)
    val locus = notificationOf(INCOGNITO_URL).locusId!!.id
    assertThat(locus).doesNotContain("secret.example")
    assertThat(locus).doesNotContain(TITLE)
  }

  @Test
  fun the_bubble_is_suppressable_on_android_12_and_later() {
    post(URL)

    assertThat(notificationOf(URL).bubbleMetadata!!.isBubbleSuppressable).isTrue()
  }

  @Test
  @Config(sdk = [30])
  fun the_bubble_is_not_marked_suppressable_before_android_12() {
    post(URL)

    // isBubbleSuppressable is Android 12 API, so the test reads the flag bit it would read.
    val flags: Int = ReflectionHelpers.getField(notificationOf(URL).bubbleMetadata!!, "mFlags")
    assertThat(flags and FLAG_SUPPRESSABLE_BUBBLE).isEqualTo(0)
    assertMessageWithLocusOfShortcut(URL)
  }

  @Test
  @Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
  fun the_desired_height_follows_the_window_not_the_display() = assertHeightFollowsTheWindow()

  @Test
  @Config(sdk = [30], qualifiers = "w411dp-h914dp-420dpi")
  fun the_desired_height_follows_the_window_on_android_11() = assertHeightFollowsTheWindow()

  private companion object {
    const val TITLE = "Secret Distinctive Title"
    const val URL = "https://normal.example/page"
    const val INCOGNITO_URL = "https://secret.example/private/path"

    /** Notification.BubbleMetadata.FLAG_SUPPRESSABLE_BUBBLE, added in Android 12. */
    const val FLAG_SUPPRESSABLE_BUBBLE = 0x00000004
  }
}
