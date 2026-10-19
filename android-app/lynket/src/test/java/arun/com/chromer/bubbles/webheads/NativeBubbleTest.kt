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

package arun.com.chromer.bubbles.webheads

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Looper
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.bubbles.system.BubbleGate
import arun.com.chromer.bubbles.system.BubbleLoadData
import arun.com.chromer.bubbles.system.BubbleNotificationManager
import arun.com.chromer.bubbles.system.BubbleRefusal
import arun.com.chromer.bubbles.system.BubbleRefusedActivity
import arun.com.chromer.bubbles.system.NativeFloatingBubble
import arun.com.chromer.bubbles.system.bubbleConversationId
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import javax.inject.Inject

/**
 * Native bubbles need a conversation shortcut; a refused bubble opens no notification, and a
 * bubble Android shows as a plain notification is removed and opens in a tab.
 */
@HiltAndroidTest
class NativeBubbleTest : LynketRobolectricSuite() {
  @BindValue
  @JvmField
  val bubbleGate: BubbleGate = mockk(relaxed = true)

  @Inject
  lateinit var nativeFloatingBubble: NativeFloatingBubble

  @Inject
  lateinit var bubbleNotificationManager: BubbleNotificationManager

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val url = "https://a.example"

  private fun bubbleNotification(): Notification? =
    shadowOf(notificationManager).allNotifications.singleOrNull { it.shortcutId == bubbleConversationId(url) }

  @Test
  fun a_refused_bubble_posts_nothing_and_shows_no_toast() {
    every { bubbleGate.refusal(bubbleConversationId(url)) } returns BubbleRefusal.NOTIFICATIONS_OFF

    val opened = nativeFloatingBubble.openBubble(Website(url), false, false, false, application)
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(opened).isFalse()
    assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
  }

  @Test
  fun a_bubble_notification_carries_a_conversation_shortcut() {
    runBlocking { bubbleNotificationManager.showBubbles(BubbleLoadData(Website(url), false, false, false)) }

    val id = bubbleConversationId(url)
    val shortcut = application.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single { it.id == id }
    assertThat(shortcut.shortLabel.toString()).isNotEmpty()
    assertThat(shortcut.intent!!.dataString).isEqualTo(url)
    val notification = shadowOf(notificationManager).allNotifications.single { it.shortcutId == id }
    assertThat(notification.bubbleMetadata).isNotNull()
  }

  @Test
  fun a_plain_notification_is_removed_and_the_page_opens_in_a_tab() {
    every { bubbleGate.refusal(any()) } returns null

    // Robolectric never sets the bubble flag, as Android does when it does not show the bubble.
    assertThat(nativeFloatingBubble.openBubble(Website(url), false, false, false, application)).isTrue()

    val tab = awaitStartedActivity()
    assertThat(tab.dataString).isEqualTo(url)
    assertThat(BubbleRefusedActivity.refusalOf(tab)).isEqualTo(BubbleRefusal.NOT_SHOWN)
    assertThat(bubbleNotification()).isNull()
  }

  @Test
  fun a_bubble_android_shows_stays() {
    every { bubbleGate.refusal(any()) } returns null

    nativeFloatingBubble.openBubble(Website(url), false, false, false, application)
    awaitCondition("the bubble notification") { bubbleNotification() != null }
    bubbleNotification()!!.flags = bubbleNotification()!!.flags or Notification.FLAG_BUBBLE

    // Past the 1 s pause and the three flag reads, and the second post with the icon.
    Thread.sleep(2_500)
    shadowOf(Looper.getMainLooper()).idle()
    assertThat(bubbleNotification()).isNotNull()
    assertThat(shadowOf(application).nextStartedActivity).isNull()
  }

  private fun awaitStartedActivity(): Intent {
    var started: Intent? = null
    awaitCondition("a browsing tab") {
      started = shadowOf(application).nextStartedActivity
      started != null
    }
    return started!!
  }

  /** The bubble posts and is checked on background threads, with a 1 s pause between. */
  private fun awaitCondition(what: String, condition: () -> Boolean) {
    repeat(500) {
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      Thread.sleep(10)
    }
    error("Timed out waiting for $what")
  }
}
