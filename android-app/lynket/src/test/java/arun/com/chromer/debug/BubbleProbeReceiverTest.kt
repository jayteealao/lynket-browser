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

package arun.com.chromer.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.R
import arun.com.chromer.bubbles.system.bubbleNotificationId
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager
import timber.log.Timber

/** The debug receiver the overflow device check reads and posts bubble notifications through. */
@HiltAndroidTest
@Config(shadows = [RecordingNotificationManagerShadow::class])
class BubbleProbeReceiverTest : LynketRobolectricSuite() {

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val url = "https://example.com/?probe=1"
  private val id = bubbleNotificationId(url)
  private val logged = mutableListOf<String>()
  private val tree = object : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
      if (tag == "BubbleProbe") logged += message
    }
  }

  @Before
  fun prepare() {
    RecordingNotificationManagerShadow.calls.clear()
    notificationManager.createNotificationChannel(
      NotificationChannel(CHANNEL, CHANNEL, NotificationManager.IMPORTANCE_HIGH)
    )
    Timber.plant(tree)
  }

  @After
  fun removeTree() {
    Timber.uproot(tree)
  }

  private fun builder() = Notification.Builder(application, CHANNEL)
    .setSmallIcon(R.drawable.ic_chromer_notification)
    .setGroup("bubbles")

  private fun postBubble() {
    notificationManager.notify(id, builder().setContentTitle(TITLE).setContentText(TEXT).build())
    RecordingNotificationManagerShadow.calls.clear()
  }

  private fun broadcast(action: String?, variant: String? = null) {
    val intent = Intent().putExtra("url", url).putExtra("variant", variant)
    action?.let { intent.putExtra("action", it) }
    BubbleProbeReceiver().onReceive(application, intent)
  }

  private fun active(): Notification = notificationManager.activeNotifications.single { it.id == id }.notification

  @Test
  fun the_same_variant_keeps_the_id_and_the_text_and_posts_silently() {
    postBubble()

    broadcast("repost", "same")

    assertThat(RecordingNotificationManagerShadow.calls).containsExactly("notify $id")
    assertThat(active().flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
    assertThat(active().extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo(TEXT)
    assertThat(logged).containsExactly("repost id=$id variant=same found=true")
  }

  @Test
  fun the_text_variant_changes_the_text_and_leaves_the_title() {
    postBubble()

    broadcast("repost", "text")

    assertThat(RecordingNotificationManagerShadow.calls).containsExactly("notify $id")
    assertThat(active().flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
    assertThat(active().extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("$TEXT​")
    assertThat(active().extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo(TITLE)
  }

  @Test
  fun the_fresh_variant_cancels_first_and_keeps_the_id() {
    postBubble()

    broadcast("repost", "fresh")

    assertThat(RecordingNotificationManagerShadow.calls).containsExactly("cancel $id", "notify $id").inOrder()
    assertThat(active().flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
    assertThat(active().extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo(TEXT)
  }

  @Test
  fun a_link_with_no_active_notification_posts_nothing() {
    broadcast("repost", "same")

    assertThat(RecordingNotificationManagerShadow.calls).isEmpty()
    assertThat(logged).containsExactly("repost id=$id variant=same found=false")
  }

  @Test
  fun state_lists_each_bubble_and_skips_the_summary() {
    postBubble()
    notificationManager.notify(99, builder().setGroupSummary(true).build())
    val flags = active().flags

    broadcast("state")

    assertThat(logged).containsExactly(
      "state id=$id bubble=false flags=${flags.toString(16)}",
      "state done count=1",
    ).inOrder()
  }

  @Test
  fun clear_cancels_every_notification() {
    postBubble()

    broadcast("clear")

    assertThat(notificationManager.activeNotifications).isEmpty()
    assertThat(logged).containsExactly("clear done")
  }

  @Test
  fun a_broadcast_with_no_action_does_nothing() {
    postBubble()

    broadcast(null)

    assertThat(RecordingNotificationManagerShadow.calls).isEmpty()
    assertThat(logged).isEmpty()
    assertThat(shadowOf(notificationManager).allNotifications).hasSize(1)
  }

  private companion object {
    const val CHANNEL = "probe"
    const val TITLE = "Example"
    const val TEXT = "example.com"
  }
}

/** Records the order of the posts and the cancels, which the framework shadow does not keep. */
@Implements(NotificationManager::class)
class RecordingNotificationManagerShadow : ShadowNotificationManager() {

  @Implementation
  public override fun notify(id: Int, notification: Notification) {
    calls += "notify $id"
    super.notify(id, notification)
  }

  @Implementation
  public override fun cancel(id: Int) {
    calls += "cancel $id"
    super.cancel(id)
  }

  companion object {
    val calls = mutableListOf<String>()
  }
}
