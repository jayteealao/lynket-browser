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
import androidx.activity.ComponentActivity
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubblePlace
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager
import timber.log.Timber

/**
 * The open list against what Android holds: a bubble in the stack is active with the bubble flag,
 * one in the overflow is active without it, and one the overflow dropped is not active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [FailingReadNotificationManagerShadow::class])
class BubbleReconcilerTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val manager by lazy { BubbleNotificationManager(application) }

  private val first = "https://example.com/one"
  private val second = "https://example.com/two"

  private val registry = BubbleRegistry()
  private val dispatcher = StandardTestDispatcher()
  private val reconciler by lazy { BubbleReconciler(manager, registry, dispatcher) }

  private val logged = mutableListOf<String>()
  private val tree = object : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
      if (message.startsWith("Reconciled:")) logged += message
    }
  }

  @Before
  fun prepare() {
    FailingReadNotificationManagerShadow.failing = false
    FailingReadNotificationManagerShadow.reads = 0
    Timber.plant(tree)
  }

  @After
  fun removeTree() {
    Timber.uproot(tree)
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

  /** A bubble that opened, as the open path does it: the post, then the entry. */
  private fun open(url: String, incognito: Boolean = false, inStack: Boolean = true) {
    post(url, incognito)
    registry.add(BrowsingMode.NativeBubbles, url, incognito)
    if (inStack) showInStack(url, incognito)
  }

  private fun listed() = registry.list(BrowsingMode.NativeBubbles)

  private fun returnable() = registry.returnCandidates(BrowsingMode.NativeBubbles)

  @Test
  fun a_later_read_leaves_the_calling_thread_at_once() {
    open(first)

    reconciler.reconcileLater()

    assertThat(FailingReadNotificationManagerShadow.reads).isEqualTo(0)
    dispatcher.scheduler.advanceUntilIdle()
    assertThat(FailingReadNotificationManagerShadow.reads).isGreaterThan(0)
  }

  @Test
  fun eight_open_with_five_in_the_stack_read_as_eight_with_three_in_the_overflow() {
    val links = (1..8).map { "https://example.com/$it" }
    links.forEachIndexed { index, link -> open(link, inStack = index >= 3) }

    reconciler.reconcile()

    assertThat(listed().map { it.url }).containsExactlyElementsIn(links).inOrder()
    assertThat(registry.overflow(BrowsingMode.NativeBubbles).map { it.url })
      .containsExactlyElementsIn(links.take(3)).inOrder()
  }

  @Test
  fun a_bubble_android_dropped_leaves_unmarked_and_is_not_offered_for_a_return() {
    open(first, inStack = false)
    open(second, inStack = false)

    notificationManager.cancel(bubbleNotificationId(first))
    reconciler.reconcile()

    assertThat(listed().map { it.url }).containsExactly(second)
    assertThat(returnable().map { it.url }).containsExactly(second)
    assertThat(registry.isClosedOnPurpose(BubbleKey.of(BrowsingMode.NativeBubbles, first, false))).isFalse()
  }

  @Test
  fun a_read_that_throws_keeps_every_entry_with_its_place_and_its_order() {
    open(first, inStack = false)
    open(second)
    reconciler.reconcile()
    val before = registry.bubbles.value
    assertThat(before.map { it.place }).containsExactly(BubblePlace.OVERFLOW, BubblePlace.STACK).inOrder()

    FailingReadNotificationManagerShadow.failing = true
    reconciler.reconcile()

    assertThat(registry.bubbles.value).isEqualTo(before)
    assertThat(logged).hasSize(1)
  }

  @Test
  fun no_open_bubble_means_no_read() {
    reconciler.reconcile()

    assertThat(FailingReadNotificationManagerShadow.reads).isEqualTo(0)
    assertThat(logged).isEmpty()
  }

  @Test
  fun a_web_head_alone_means_no_read() {
    registry.add(BrowsingMode.WebHeads, first, false)

    reconciler.reconcile()

    assertThat(FailingReadNotificationManagerShadow.reads).isEqualTo(0)
    assertThat(registry.list(BrowsingMode.WebHeads)).hasSize(1)
  }

  @Test
  fun a_reconcile_logs_one_line_with_the_total_and_the_ids() {
    val aged = "https://example.com/aged"
    open(first, inStack = false)
    open(aged, inStack = false)
    open(second)

    notificationManager.cancel(bubbleNotificationId(first))
    reconciler.reconcile()

    val id = bubbleNotificationId(first)
    val agedId = bubbleNotificationId(aged)
    assertThat(logged).containsExactly("Reconciled: total=2 overflow=$agedId returnable=$agedId dropped=$id")
  }

  @Test
  fun the_line_holds_no_address_for_a_normal_or_an_incognito_bubble() {
    open(first, inStack = false)
    open(second, incognito = true, inStack = false)

    reconciler.reconcile()

    assertThat(logged).hasSize(1)
    assertThat(logged.single()).doesNotContain("example.com")
  }

  @Test
  fun an_activity_start_reconciles() {
    open(first)
    open(second)
    reconciler.attach(application)
    notificationManager.cancel(bubbleNotificationId(first))

    Robolectric.buildActivity(ComponentActivity::class.java).create().start()
    dispatcher.scheduler.advanceUntilIdle()

    assertThat(listed().map { it.url }).containsExactly(second)
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
