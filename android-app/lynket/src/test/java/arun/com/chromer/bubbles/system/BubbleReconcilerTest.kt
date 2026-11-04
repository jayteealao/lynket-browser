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
import android.service.notification.StatusBarNotification
import arun.com.chromer.bubbles.BubblePlace
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

/**
 * The open list against what Android holds: a bubble in the stack is active with the bubble flag,
 * one in the overflow is active without it, and one the overflow dropped is not active.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [FailingReadNotificationManagerShadow::class])
class BubbleReconcilerTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val manager by lazy { BubbleNotificationManager(application) }

  private val first = "https://example.com/one"
  private val second = "https://example.com/two"

  @Before
  fun readsWork() {
    FailingReadNotificationManagerShadow.failing = false
    FailingReadNotificationManagerShadow.reads = 0
  }

  private fun website(url: String) = Website("Title", url, null, null, null, null, false, 0, 0)

  private fun post(url: String, incognito: Boolean = false) {
    runBlocking { manager.showBubbles(BubbleLoadData(website(url), false, false, incognito)) }
  }

  private fun notificationOf(url: String, incognito: Boolean = false): Notification =
    shadowOf(notificationManager).allNotifications.single { it.shortcutId == bubbleConversationId(url, incognito) }

  /** What Android does to a notification it shows as a bubble. */
  private fun showInStack(url: String, incognito: Boolean = false) {
    val notification = notificationOf(url, incognito)
    notification.flags = notification.flags or Notification.FLAG_BUBBLE
  }

  @Test
  fun a_bubble_with_the_flag_reads_the_stack() {
    post(first)
    showInStack(first)

    assertThat(manager.bubblePlaces()?.get(bubbleNotificationId(first))).isEqualTo(BubblePlace.STACK)
  }

  @Test
  fun a_bubble_without_the_flag_reads_the_overflow() {
    post(first)

    assertThat(manager.bubblePlaces()?.get(bubbleNotificationId(first))).isEqualTo(BubblePlace.OVERFLOW)
  }

  @Test
  fun a_cancelled_bubble_is_missing() {
    post(first)
    post(second)

    notificationManager.cancel(bubbleNotificationId(first))

    val places = manager.bubblePlaces()
    assertThat(places).doesNotContainKey(bubbleNotificationId(first))
    assertThat(places).containsKey(bubbleNotificationId(second))
  }

  @Test
  fun a_read_that_throws_reads_as_null_and_not_as_empty() {
    post(first)
    FailingReadNotificationManagerShadow.failing = true

    assertThat(manager.bubblePlaces()).isNull()
  }
}

/** A read of the active notifications that can throw, and that counts its calls. */
@Implements(NotificationManager::class)
class FailingReadNotificationManagerShadow : ShadowNotificationManager() {

  @Implementation
  public override fun getActiveNotifications(): Array<StatusBarNotification> {
    reads++
    if (failing) throw RuntimeException("The notification service is gone")
    return super.getActiveNotifications()
  }

  companion object {
    var failing = false
    var reads = 0
  }
}
