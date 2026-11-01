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

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowShortcutManager
import javax.inject.Inject

/**
 * Dragging a bubble to the dismiss target makes Android send the bubble's delete intent; the
 * receiver then removes its notification, its shortcut and, after the last bubble, the summary.
 */
@HiltAndroidTest
@Config(shadows = [RecordingShortcutManagerShadow::class])
class NativeBubbleDismissTest : LynketRobolectricSuite() {

  @Inject
  lateinit var manager: BubbleNotificationManager

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val first = "https://secret.example/one"
  private val second = "https://secret.example/two"

  @Before
  fun clearRecordedRemovals() {
    RecordingShortcutManagerShadow.removedLongLived.clear()
  }

  private fun website(url: String) = Website(TITLE, url, null, null, null, null, false, 0, 0)

  private fun post(url: String, incognito: Boolean = false) {
    runBlocking { manager.showBubbles(BubbleLoadData(website(url), false, false, incognito)) }
  }

  private fun notificationOf(url: String): Notification? =
    shadowOf(notificationManager).allNotifications.singleOrNull { it.shortcutId == bubbleConversationId(url) }

  private fun summary(): Notification? =
    shadowOf(notificationManager).allNotifications.singleOrNull { it.flags and Notification.FLAG_GROUP_SUMMARY != 0 }

  private fun shortcutIds(): List<String> =
    application.getSystemService(ShortcutManager::class.java).dynamicShortcuts.map { it.id }

  /** Sends the bubble's delete intent and hands the broadcast to the receiver, as Android does. */
  private fun dismiss(url: String) {
    notificationOf(url)!!.bubbleMetadata!!.deleteIntent!!.send()
    val sent = shadowOf(application).broadcastIntents.last()
    assertThat(sent.component?.className).isEqualTo(BubbleDismissReceiver::class.java.name)
    BubbleDismissReceiver().onReceive(application, sent)
  }

  @Test
  fun a_bubble_carries_an_immutable_delete_intent_to_the_receiver() {
    post(first)

    val deleteIntent = notificationOf(first)!!.bubbleMetadata!!.deleteIntent
    assertThat(deleteIntent).isNotNull()
    assertThat(shadowOf(deleteIntent).isBroadcast).isTrue()
    assertThat(deleteIntent!!.isImmutable).isTrue()
    val saved = shadowOf(deleteIntent).savedIntent
    assertThat(saved.component?.className).isEqualTo(BubbleDismissReceiver::class.java.name)
    assertThat(saved.getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0)).isEqualTo(first.hashCode())
  }

  @Test
  fun dismissing_a_bubble_cancels_its_notification_and_removes_its_shortcut() {
    post(first)
    assertThat(notificationOf(first)).isNotNull()
    assertThat(shortcutIds()).contains(bubbleConversationId(first))

    dismiss(first)

    assertThat(notificationOf(first)).isNull()
    assertThat(shortcutIds()).doesNotContain(bubbleConversationId(first))
    assertThat(RecordingShortcutManagerShadow.removedLongLived).containsExactly(bubbleConversationId(first))
    assertThat(summary()).isNull()
  }

  @Test
  fun dismissing_an_incognito_bubble_removes_its_shortcut_and_its_delete_intent_holds_no_address() {
    post(first, incognito = true)

    val saved = shadowOf(notificationOf(first)!!.bubbleMetadata!!.deleteIntent).savedIntent.toUri(0)
    assertThat(saved).doesNotContain("secret.example")
    assertThat(saved).doesNotContain(TITLE)

    dismiss(first)

    assertThat(notificationOf(first)).isNull()
    assertThat(shortcutIds()).doesNotContain(bubbleConversationId(first))
  }

  @Test
  fun the_summary_stays_until_the_last_bubble_is_dismissed() {
    post(first)
    post(second, incognito = true)

    dismiss(first)

    assertThat(notificationOf(first)).isNull()
    assertThat(shortcutIds()).doesNotContain(bubbleConversationId(first))
    assertThat(notificationOf(second)).isNotNull()
    assertThat(shortcutIds()).contains(bubbleConversationId(second))
    assertThat(summary()).isNotNull()

    dismiss(second)

    assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
  }

  @Test
  fun a_dismiss_without_an_id_changes_nothing() {
    post(first)

    BubbleDismissReceiver().onReceive(application, Intent())

    assertThat(notificationOf(first)).isNotNull()
    assertThat(summary()).isNotNull()
    assertThat(shortcutIds()).contains(bubbleConversationId(first))
  }

  @Test
  fun the_receiver_is_declared_and_not_exported() {
    val info = application.packageManager.getReceiverInfo(ComponentName(application, BubbleDismissReceiver::class.java), 0)

    assertThat(info.exported).isFalse()
  }

  private companion object {
    const val TITLE = "Secret Distinctive Title"
  }
}

/**
 * Robolectric's own long-lived removal does nothing; this one records the ids and drops the
 * dynamic shortcuts, as Android does.
 */
@Implements(ShortcutManager::class)
class RecordingShortcutManagerShadow : ShadowShortcutManager() {

  @Implementation
  public override fun removeLongLivedShortcuts(shortcutIds: List<String>) {
    removedLongLived += shortcutIds
    removeDynamicShortcuts(shortcutIds)
  }

  companion object {
    val removedLongLived = mutableListOf<String>()
  }
}
