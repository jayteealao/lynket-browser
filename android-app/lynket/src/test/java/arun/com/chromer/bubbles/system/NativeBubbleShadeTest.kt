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
import android.content.Context
import android.content.pm.ShortcutManager
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.R
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubblePageCount
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.pageCount
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * What the shade can show of the bubbles, read from what the real manager posted: the count lives
 * in the page and reaches no notification, summary, shortcut or intent, the summary keeps its
 * words, and a returned bubble alerts nothing.
 */
@HiltAndroidTest
@Config(shadows = [RecordingShortcutManagerShadow::class])
class NativeBubbleShadeTest : LynketRobolectricSuite() {

  @Inject
  lateinit var manager: BubbleNotificationManager

  @Inject
  lateinit var registry: BubbleRegistry

  @Inject
  lateinit var nativeFloatingBubble: NativeFloatingBubble

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val links = (1..7).map { "https://$HOST/page-$it" }

  /**
   * A return runs on a real thread, and the notifications of Robolectric outlive a test, so a return
   * still running would post into the next test. An empty list stops it, and a last call waits for it.
   */
  @After
  fun waitForTheReturns() {
    registry.bubbles.value.forEach { registry.remove(it.key) }
    awaitReturn()
  }

  /** Returns run in the order they were asked for, so this one ends after the others. */
  private fun awaitReturn() {
    runBlocking { withTimeout(WAIT_FOR_RETURN_MS) { nativeFloatingBubble.returnNewestOverflowBubble().join() } }
  }

  private fun post(url: String, incognito: Boolean = false) {
    val data = BubbleLoadData(Website(TITLE, url, null, null, null, null, false, 0, 0), false, false, incognito)
    runBlocking { manager.showBubbles(data) }
    registry.add(BrowsingMode.NativeBubbles, url, incognito, data)
  }

  /** Six bubbles and an incognito one, the newest. */
  private fun postSeven() {
    links.dropLast(1).forEach { post(it) }
    post(links.last(), incognito = true)
  }

  private fun posted(): List<Notification> = shadowOf(notificationManager).allNotifications

  private fun summary(): Notification = posted().single { it.flags and Notification.FLAG_GROUP_SUMMARY != 0 }

  private fun bubbles(): List<Notification> = posted().filter { it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }

  /** Every text a bubble can show outside its page, from what the manager really posted. */
  private fun surfaceTexts(): List<String> {
    val posted = posted()
    val notifications = posted + posted.mapNotNull { it.publicVersion }
    val fromNotifications = notifications.flatMap { n ->
      n.extras.keySet().map { key ->
        @Suppress("DEPRECATION") // sdlc-debt: the typed getter needs the type of each key; the test reads every value as text.
        n.extras.get(key)?.toString().orEmpty()
      } + listOfNotNull(
        n.tickerText?.toString(),
        n.extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)?.name?.toString(),
      )
    }
    val fromIntents = posted.mapNotNull { it.bubbleMetadata?.intent }.flatMap { pending ->
      val saved = shadowOf(pending).savedIntent
      val extras = saved.extras
      @Suppress("DEPRECATION") // sdlc-debt: the typed getter needs the type of each key; the test reads every value as text.
      val values = extras?.keySet().orEmpty().map { extras?.get(it).toString() }
      listOf(saved.toUri(0)) + values
    }
    val fromShortcuts = application.getSystemService(ShortcutManager::class.java).dynamicShortcuts.flatMap {
      listOfNotNull(it.shortLabel, it.longLabel, it.disabledMessage).map(CharSequence::toString) +
        listOfNotNull(it.intent?.toUri(0), it.extras?.toString())
    }
    assertThat(fromShortcuts).isNotEmpty()
    return fromNotifications + fromIntents + fromShortcuts
  }

  private fun assertNoCount(texts: List<String>) {
    val written = (1..TOTAL).flatMap {
      listOf(
        application.getString(R.string.bubble_page_count, it, TOTAL),
        application.getString(R.string.bubble_page_count_spoken, it, TOTAL),
      )
    }
    texts.forEach { text ->
      assertThat(text).doesNotContainMatch(COUNT_SHAPE)
      assertThat(written).doesNotContain(text)
    }
  }

  @Test
  fun no_notification_shortcut_or_intent_of_seven_bubbles_holds_the_count() {
    postSeven()

    val third = BubbleKey.of(BrowsingMode.NativeBubbles, links[2], false)
    assertThat(registry.bubbles.value.pageCount(third)).isEqualTo(BubblePageCount(3, TOTAL))

    val texts = surfaceTexts()
    assertThat(texts).contains(TITLE)
    assertThat(texts).contains(links.first())
    assertThat(texts).contains("Active bubbles")
    assertNoCount(texts)
  }

  @Test
  fun each_bubble_hides_its_own_row() {
    postSeven()

    assertThat(bubbles()).hasSize(TOTAL)
    bubbles().forEach {
      assertThat(it.bubbleMetadata!!.isNotificationSuppressed).isTrue()
      assertThat(it.bubbleMetadata!!.autoExpandBubble).isFalse()
      assertThat(it.group).isEqualTo(summary().group)
    }
  }

  private fun assertSummaryKeepsItsWords() {
    val extras = summary().extras
    assertThat(extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Active bubbles")
    assertThat(extras.getCharSequence(Notification.EXTRA_TEXT).toString())
      .isEqualTo("Showing bubble notifications, swipe to expand")
    assertThat(extras.containsKey(Notification.EXTRA_SUB_TEXT)).isFalse()
    assertThat(extras.containsKey(Notification.EXTRA_SUMMARY_TEXT)).isFalse()
    assertThat(extras.containsKey(Notification.EXTRA_BIG_TEXT)).isFalse()
  }

  @Test
  fun the_summary_says_what_it_says_today() {
    post(links.first())
    assertSummaryKeepsItsWords()

    links.drop(1).forEach { post(it) }
    assertSummaryKeepsItsWords()
  }

  private fun bubbleOf(url: String): Notification = bubbles().single { it.shortcutId in bubbleIdsOf(url) }

  private fun Notification.alertsOnce() = flags and Notification.FLAG_ONLY_ALERT_ONCE != 0

  private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

  private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

  /** Returns the newest of the seven bubbles posted, and reads what that return posted. */
  private fun assertTheNewestReturnsSilently() {
    val newest = links.last()
    val before = bubbleOf(newest)
    val olderBefore = links.dropLast(1).map(::bubbleOf)
    val summaryBefore = summary()
    assertThat(before.alertsOnce()).isFalse()

    awaitReturn()

    // The flag also shows that the return posted: only that post sets it.
    val after = bubbleOf(newest)
    assertThat(after).isNotSameInstanceAs(before)
    assertThat(after.alertsOnce()).isTrue()
    assertThat(after.bubbleMetadata!!.autoExpandBubble).isFalse()
    assertThat(after.bubbleMetadata!!.isNotificationSuppressed).isTrue()
    assertThat(after.fullScreenIntent).isNull()
    assertThat(after.tickerText).isNull()
    assertThat(after.title()).isEqualTo(before.title())
    assertThat(after.text()).isEqualTo(before.text())
    assertThat(after.shortcutId).isEqualTo(before.shortcutId)
    assertThat(after.bubbleMetadata!!.deleteIntent).isEqualTo(before.bubbleMetadata!!.deleteIntent)

    // One bubble was posted, and no summary.
    links.dropLast(1).forEachIndexed { index, url -> assertThat(bubbleOf(url)).isSameInstanceAs(olderBefore[index]) }
    assertThat(summary()).isSameInstanceAs(summaryBefore)

    val channel = notificationManager.getNotificationChannel(BUBBLE_NOTIFICATION_CHANNEL_ID)
    assertThat(channel.sound).isNull()
    assertThat(channel.shouldVibrate()).isFalse()

    assertNoCount(surfaceTexts())
  }

  @Test
  fun a_returned_bubble_carries_no_alert() {
    links.forEach { post(it) }

    assertTheNewestReturnsSilently()
  }

  @Test
  fun a_returned_incognito_bubble_carries_no_alert() {
    postSeven()

    assertTheNewestReturnsSilently()

    assertThat(bubbleOf(links.last()).title()).isEqualTo(application.getString(R.string.bubble_incognito_label))
  }

  private companion object {
    const val HOST = "shade.example"
    const val TITLE = "Shade Distinctive Title"
    const val TOTAL = 7
    const val COUNT_SHAPE = "\\d+ of \\d+"
    const val WAIT_FOR_RETURN_MS = 10_000L
  }
}
