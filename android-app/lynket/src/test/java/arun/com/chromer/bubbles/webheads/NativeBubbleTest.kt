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

import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import android.content.pm.ShortcutManager
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.R
import arun.com.chromer.bubbles.system.BubbleLoadData
import arun.com.chromer.bubbles.system.BubbleNotificationManager
import arun.com.chromer.bubbles.system.NativeFloatingBubble
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import javax.inject.Inject

/** Native bubbles need a conversation shortcut, and fall back when notifications are off. */
@HiltAndroidTest
class NativeBubbleTest : LynketRobolectricSuite() {
  @Inject
  lateinit var nativeFloatingBubble: NativeFloatingBubble

  @Inject
  lateinit var bubbleNotificationManager: BubbleNotificationManager

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  @Test
  fun notifications_off_refuses_the_bubble_with_a_message() {
    shadowOf(notificationManager).setNotificationsEnabled(false)

    val opened = nativeFloatingBubble.openBubble(Website("https://a.example"), false, false, false, application)
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(opened).isFalse()
    assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(application.getString(R.string.bubbles_not_allowed))
  }

  @Test
  fun a_bubble_notification_carries_a_conversation_shortcut() {
    val url = "https://a.example"
    runBlocking { bubbleNotificationManager.showBubbles(BubbleLoadData(Website(url), false, false, false)) }

    val id = "bubble_${url.hashCode()}"
    val shortcut = application.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single { it.id == id }
    assertThat(shortcut.shortLabel.toString()).isNotEmpty()
    assertThat(shortcut.intent!!.dataString).isEqualTo(url)
    val notification = shadowOf(notificationManager).allNotifications.single { it.shortcutId == id }
    assertThat(notification.bubbleMetadata).isNotNull()
  }
}
