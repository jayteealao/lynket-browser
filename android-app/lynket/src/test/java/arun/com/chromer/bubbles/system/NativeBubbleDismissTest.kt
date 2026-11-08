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
import android.net.Uri
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.webview.EmbeddableWebViewActivity
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubbleLoadData
import arun.com.chromer.bubbles.BubblePlace
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.bubbleConversationId
import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
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

  @Inject
  lateinit var registry: BubbleRegistry

  @Inject
  lateinit var nativeFloatingBubble: NativeFloatingBubble

  @Inject
  lateinit var closer: BubbleCloser

  /** The page finishes at once; its bubble's notification and shortcut go just after, off the main thread. */
  private fun EmbeddableWebViewActivity.finishAndWait() {
    finish()
    runBlocking { closer.awaitCloses() }
  }

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val first = "https://secret.example/one"
  private val second = "https://secret.example/two"

  @Before
  fun clearRecordedRemovals() {
    RecordingShortcutManagerShadow.removedLongLived.clear()
  }

  /**
   * A return runs on a real thread, and the notifications of Robolectric outlive a test, so a return
   * still running would post into the next test. An empty list stops it, and a last call waits for it.
   */
  @After
  fun waitForTheReturns() {
    registry.bubbles.value.forEach { registry.remove(it.key) }
    awaitReturns()
  }

  /** Returns run in the order they were asked for, so this one ends after the others. */
  private fun awaitReturns() {
    runBlocking { withTimeout(WAIT_FOR_RETURNS_MS) { nativeFloatingBubble.returnNewestOverflowBubble().join() } }
  }

  private fun website(url: String) = Website(TITLE, url, null, null, null, null, false, 0, 0)

  private fun post(url: String, incognito: Boolean = false) {
    val data = BubbleLoadData(website(url), false, false, incognito)
    runBlocking { manager.showBubbles(data) }
    registry.add(BrowsingMode.NativeBubbles, url, incognito, data)
  }

  private fun listed() = registry.list(BrowsingMode.NativeBubbles).map { it.url }

  private fun keyOf(url: String, incognito: Boolean = false) =
    BubbleKey.of(BrowsingMode.NativeBubbles, url, incognito)

  private fun notificationOf(url: String): Notification? =
    shadowOf(notificationManager).allNotifications.singleOrNull { it.shortcutId in bubbleIdsOf(url) }

  private fun notificationOfIncognito(url: String): Notification =
    shadowOf(notificationManager).allNotifications.single { it.shortcutId == bubbleConversationId(url, incognito = true) }

  private fun summary(): Notification? =
    shadowOf(notificationManager).allNotifications.singleOrNull { it.flags and Notification.FLAG_GROUP_SUMMARY != 0 }

  private fun shortcutIds(): List<String> =
    application.getSystemService(ShortcutManager::class.java).dynamicShortcuts.map { it.id }

  /** Sends the bubble's delete intent and hands the broadcast to the receiver, as Android does. */
  private fun dismiss(url: String): Intent {
    notificationOf(url)!!.bubbleMetadata!!.deleteIntent!!.send()
    val sent = shadowOf(application).broadcastIntents.last()
    assertThat(sent.component?.className).isEqualTo(BubbleDismissReceiver::class.java.name)
    BubbleDismissReceiver().onReceive(application, sent)
    return sent
  }

  /** A page of [url], opened by its bubble or from elsewhere. */
  private fun page(url: String, inBubble: Boolean = true): EmbeddableWebViewActivity {
    val intent = Intent(application, EmbeddableWebViewActivity::class.java).setData(Uri.parse(url))
    if (inBubble) intent.putExtra(EXTRA_BUBBLE_NOTIFICATION_ID, bubbleNotificationId(url))
    return Robolectric.buildActivity(EmbeddableWebViewActivity::class.java, intent).create().get()
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
    assertThat(saved.getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0)).isEqualTo(bubbleNotificationId(first))
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
  fun removing_a_bubble_android_did_not_show_also_removes_its_shortcut() {
    post(first, incognito = true)
    assertThat(shortcutIds()).contains(bubbleConversationId(first, incognito = true))

    manager.cancel(first, incognito = true)

    assertThat(notificationOf(first)).isNull()
    assertThat(shortcutIds()).doesNotContain(bubbleConversationId(first, incognito = true))
    assertThat(RecordingShortcutManagerShadow.removedLongLived).containsExactly(bubbleConversationId(first, incognito = true))
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
    assertThat(shortcutIds()).doesNotContain(bubbleConversationId(first, incognito = true))
  }

  @Test
  fun the_summary_stays_until_the_last_bubble_is_dismissed() {
    post(first)
    post(second, incognito = true)

    dismiss(first)

    assertThat(notificationOf(first)).isNull()
    assertThat(shortcutIds()).doesNotContain(bubbleConversationId(first))
    assertThat(notificationOf(second)).isNotNull()
    assertThat(shortcutIds()).contains(bubbleConversationId(second, incognito = true))
    assertThat(summary()).isNotNull()

    dismiss(second)

    assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
  }

  @Test
  fun the_x_of_a_page_in_a_bubble_closes_the_bubble_as_a_dismissal_does() {
    post(first)
    post(second)

    page(first).finishAndWait()

    assertThat(notificationOf(first)).isNull()
    assertThat(shortcutIds()).doesNotContain(bubbleConversationId(first))
    assertThat(listed()).containsExactly(second)
    assertThat(registry.isClosedOnPurpose(keyOf(first))).isTrue()
    assertThat(notificationOf(second)).isNotNull()
    assertThat(summary()).isNotNull()
  }

  @Test
  fun the_x_of_the_last_bubble_takes_the_summary_too() {
    post(first)

    page(first).finishAndWait()

    assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
  }

  @Test
  fun a_delete_intent_after_the_x_changes_nothing_more() {
    post(first)
    post(second)
    val deleteIntent = Intent().putExtra(EXTRA_BUBBLE_NOTIFICATION_ID, bubbleNotificationId(first))

    page(first).finishAndWait()
    BubbleDismissReceiver().onReceive(application, deleteIntent)

    assertThat(listed()).containsExactly(second)
    assertThat(notificationOf(second)).isNotNull()
    assertThat(RecordingShortcutManagerShadow.removedLongLived.distinct()).containsExactly(bubbleConversationId(first))
  }

  @Test
  fun a_second_finish_of_a_page_closes_nothing_more() {
    post(first)
    post(second)
    val page = page(first)

    page.finishAndWait()
    post(first)
    page.finishAndWait()

    assertThat(listed()).containsExactly(second, first).inOrder()
    assertThat(notificationOf(first)).isNotNull()
  }

  @Test
  fun a_page_that_is_not_in_a_bubble_finishes_and_closes_nothing() {
    post(first)

    page(first, inBubble = false).finish()

    assertThat(listed()).containsExactly(first)
    assertThat(notificationOf(first)).isNotNull()
    assertThat(shortcutIds()).contains(bubbleConversationId(first))
    assertThat(registry.isClosedOnPurpose(keyOf(first))).isFalse()
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
  fun a_dismissal_takes_the_bubble_off_the_list_and_marks_it_closed_on_purpose() {
    post(first)

    dismiss(first)

    assertThat(listed()).isEmpty()
    assertThat(registry.isClosedOnPurpose(keyOf(first))).isTrue()
  }

  @Test
  fun a_delete_intent_that_arrives_twice_changes_nothing_more() {
    post(first)
    post(second)

    val sent = dismiss(first)
    BubbleDismissReceiver().onReceive(application, sent)

    assertThat(listed()).containsExactly(second)
    assertThat(registry.isClosedOnPurpose(keyOf(first))).isTrue()
    assertThat(notificationOf(second)).isNotNull()
  }

  @Test
  fun a_dismissal_leaves_the_other_bubbles_in_order() {
    val third = "https://secret.example/three"
    listOf(first, second, third).forEach { post(it) }

    dismiss(second)

    assertThat(listed()).containsExactly(first, third).inOrder()
  }

  @Test
  fun an_incognito_dismissal_reaches_its_entry_from_the_id_alone() {
    post(first)
    post(first, incognito = true)

    notificationOfIncognito(first).bubbleMetadata!!.deleteIntent!!.send()
    BubbleDismissReceiver().onReceive(application, shadowOf(application).broadcastIntents.last())

    assertThat(registry.list(BrowsingMode.NativeBubbles).map { it.incognito }).containsExactly(false)
    assertThat(registry.isClosedOnPurpose(keyOf(first, incognito = true))).isTrue()
    assertThat(registry.isClosedOnPurpose(keyOf(first))).isFalse()
  }

  @Test
  fun a_dismissal_leaves_a_web_head_of_the_same_link_alone() {
    post(first)
    registry.add(BrowsingMode.WebHeads, first, false)

    dismiss(first)

    assertThat(registry.list(BrowsingMode.WebHeads).map { it.url }).containsExactly(first)
    assertThat(registry.isClosedOnPurpose(BubbleKey.of(BrowsingMode.WebHeads, first, false))).isFalse()
  }

  @Test
  fun a_dismiss_without_an_id_leaves_the_list_as_it_was() {
    post(first)

    BubbleDismissReceiver().onReceive(application, Intent())

    assertThat(listed()).containsExactly(first)
    assertThat(registry.isClosedOnPurpose(keyOf(first))).isFalse()
  }

  @Test
  fun a_bubble_android_did_not_show_is_not_marked_closed_on_purpose() {
    post(first)

    manager.cancel(first, incognito = false)

    assertThat(registry.isClosedOnPurpose(keyOf(first))).isFalse()
  }

  @Test
  fun a_dismissal_takes_off_a_bubble_that_went_behind_the_app_s_back_and_leaves_it_unmarked() {
    post(first)
    post(second)

    notificationManager.cancel(bubbleNotificationId(first))
    post("https://secret.example/three")
    dismiss(second)
    awaitReturns()

    assertThat(listed()).containsExactly("https://secret.example/three")
    assertThat(registry.isClosedOnPurpose(keyOf(first))).isFalse()
    assertThat(registry.isClosedOnPurpose(keyOf(second))).isTrue()
  }

  @Test
  fun a_dismissal_never_marks_a_bubble_that_the_reconcile_moved_to_the_overflow() {
    post(first)
    post(second)

    dismiss(first)
    awaitReturns()

    assertThat(registry.find(BrowsingMode.NativeBubbles, second, false)?.place).isEqualTo(BubblePlace.OVERFLOW)
    assertThat(registry.isClosedOnPurpose(keyOf(second))).isFalse()
  }

  @Test
  fun an_aged_bubble_still_active_with_no_flag_stays_listed_and_unmarked() {
    val aged = "https://secret.example/aged"
    post(aged)
    post(first)

    dismiss(first)
    awaitReturns()

    assertThat(listed()).containsExactly(aged)
    assertThat(registry.isClosedOnPurpose(keyOf(aged))).isFalse()
  }

  private fun alertsOnce(url: String) =
    notificationOf(url)?.let { it.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 } == true

  /** A return runs on an IO thread, so its post is polled for, for up to 5 s. */
  private fun returned(url: String): Boolean {
    repeat(RETURN_POLLS) {
      if (alertsOnce(url)) return true
      Thread.sleep(RETURN_POLL_MS)
    }
    return false
  }

  /** What Android does to a bubble in the stack. */
  private fun showInStack(url: String) {
    notificationOf(url)!!.flags = notificationOf(url)!!.flags or Notification.FLAG_BUBBLE
  }

  /** The time a return that must not happen gets to show. */
  private fun settle() = Thread.sleep(SETTLE_MS)

  @Test
  fun a_close_in_the_stack_posts_the_newest_overflow_bubble_again() {
    val third = "https://secret.example/three"
    listOf(first, second, third).forEach { post(it) }

    dismiss(first)

    assertThat(returned(third)).isTrue()
    assertThat(alertsOnce(second)).isFalse()
  }

  @Test
  fun each_close_of_a_drag_asks_for_a_return_while_the_others_are_on_the_way() {
    val third = "https://secret.example/three"
    val fourth = "https://secret.example/four"
    listOf(first, second, third, fourth).forEach { post(it) }
    val places = mapOf(
      bubbleNotificationId(first) to BubblePlace.OVERFLOW,
      bubbleNotificationId(second) to BubblePlace.OVERFLOW,
      bubbleNotificationId(third) to BubblePlace.STACK,
      bubbleNotificationId(fourth) to BubblePlace.STACK,
    )
    registry.reconcile(BrowsingMode.NativeBubbles) { places }

    // As in a drag: neither bubble has its flag any more when the delete intents arrive.
    dismiss(third)
    dismiss(fourth)

    assertThat(returned(second)).isTrue()
    showInStack(second)
    assertThat(returned(first)).isTrue()
  }

  @Test
  fun a_close_in_the_overflow_posts_nothing_and_stays_closed() {
    val third = "https://secret.example/three"
    listOf(first, second, third).forEach { post(it) }
    val places = mapOf(
      bubbleNotificationId(first) to BubblePlace.OVERFLOW,
      bubbleNotificationId(second) to BubblePlace.OVERFLOW,
      bubbleNotificationId(third) to BubblePlace.STACK,
    )
    registry.reconcile(BrowsingMode.NativeBubbles) { places }

    dismiss(first)
    settle()

    assertThat(alertsOnce(second)).isFalse()
    assertThat(alertsOnce(third)).isFalse()
    assertThat(listed()).containsExactly(second, third).inOrder()
    assertThat(registry.isClosedOnPurpose(keyOf(first))).isTrue()
  }

  @Test
  fun a_delete_intent_sent_twice_posts_one_bubble() {
    val third = "https://secret.example/three"
    listOf(first, second, third).forEach { post(it) }

    val sent = dismiss(first)
    BubbleDismissReceiver().onReceive(application, sent)

    assertThat(returned(third)).isTrue()
    showInStack(third)
    settle()
    assertThat(alertsOnce(second)).isFalse()
  }

  @Test
  fun a_close_with_an_empty_overflow_changes_nothing_more_and_the_summary_goes_with_the_last_bubble() {
    post(first)
    post(second)
    showInStack(second)

    dismiss(first)
    settle()

    assertThat(alertsOnce(second)).isFalse()
    assertThat(summary()).isNotNull()

    dismiss(second)
    settle()

    assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
  }

  @Test
  fun a_dismiss_without_an_id_posts_nothing() {
    post(first)
    post(second)

    BubbleDismissReceiver().onReceive(application, Intent())
    settle()

    assertThat(alertsOnce(first)).isFalse()
    assertThat(alertsOnce(second)).isFalse()
  }

  @Test
  fun a_bubble_android_did_not_show_brings_no_bubble_back() {
    post(first)
    post(second)

    manager.cancel(first, incognito = false)
    settle()

    assertThat(alertsOnce(second)).isFalse()
  }

  @Test
  fun the_receiver_is_declared_and_not_exported() {
    val info = application.packageManager.getReceiverInfo(ComponentName(application, BubbleDismissReceiver::class.java), 0)

    assertThat(info.exported).isFalse()
  }

  private companion object {
    const val TITLE = "Secret Distinctive Title"
    const val RETURN_POLLS = 100
    const val RETURN_POLL_MS = 50L
    const val SETTLE_MS = 1_500L
    const val WAIT_FOR_RETURNS_MS = 10_000L
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
