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
import android.graphics.Bitmap
import android.graphics.Color
import arun.com.chromer.appdetect.LinkSource
import arun.com.chromer.browsing.icons.WebsiteIconData
import arun.com.chromer.browsing.icons.WebsiteIconsProvider
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.OpenBubble
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A shared link posts its bubble with no fixed wait: with the site icon when it is ready within
 * 300 ms, else with the colour disc and then once more, in place, with the icon. Each link posts on
 * its own. Times are virtual milliseconds from the call to openBubble.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NativeBubblePostingTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private data class Post(val url: String, val at: Long, val withIcon: Boolean, val withSummary: Boolean, val color: Int)

  private lateinit var clock: TestCoroutineScheduler
  private val posts = mutableListOf<Post>()
  private var iconDelayMs = 0L
  private var shown = true
  private val notShown = mutableSetOf<String>()
  private var refusedAt = -1L
  private var listedAtRefusal: List<OpenBubble>? = null

  private val repo = mockk<WebsiteRepository> {
    every { getWebsite(any()) } answers { flowOf(Website(firstArg<String>())) }
    every { getWebsiteWithoutSaving(any()) } answers { flowOf(Website(firstArg<String>())) }
  }

  private val icons = mockk<WebsiteIconsProvider> {
    coEvery { getBubbleIconAndColor(any()) } coAnswers {
      delay(iconDelayMs)
      WebsiteIconData(firstArg(), Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), Color.BLUE)
    }
  }

  private val notifications = mockk<BubbleNotificationManager> {
    coEvery { showBubbles(any(), any()) } coAnswers {
      val data = firstArg<BubbleLoadData>()
      posts += Post(data.website.url, clock.currentTime, data.icon != null, secondArg(), data.color)
      data
    }
    every { isShownAsBubble(any(), any()) } answers { shown && firstArg<String>() !in notShown }
    every { cancel(any<String>(), any()) } just Runs
  }

  private val gate = mockk<BubbleGate> {
    every { refusal(any()) } returns null
  }

  private val tabsManager = mockk<TabsManager>(relaxed = true) {
    every { openRefusedBubble(any(), any(), any(), any(), any()) } answers {
      refusedAt = clock.currentTime
      listedAtRefusal = listed()
    }
  }

  private val registry = BubbleRegistry()

  /** Runs [block] on virtual time with a bubble on the same clock, then runs every job to the end. */
  private fun posting(block: suspend TestScope.(NativeFloatingBubble) -> Unit) = runTest {
    clock = testScheduler
    val bubble = NativeFloatingBubble(application, repo, notifications, icons, gate, registry, StandardTestDispatcher(testScheduler)) { tabsManager }
    block(bubble)
    advanceUntilIdle()
  }

  private fun NativeFloatingBubble.open(url: String, incognito: Boolean = false, color: Int = 0) =
    openBubble(Website(url), false, false, incognito, null, color)

  private fun listed() = registry.list(BrowsingMode.NativeBubbles)

  private fun keyOf(url: String, incognito: Boolean = false) =
    BubbleKey.of(BrowsingMode.NativeBubbles, url, incognito)

  /** What the dismiss receiver does when the person closes the bubble of [url]. */
  private fun personDismisses(url: String) {
    registry.markClosedOnPurpose(keyOf(url))
    registry.remove(keyOf(url))
  }

  @Test
  fun an_icon_ready_in_time_posts_once_with_it_at_the_icon_time() = posting { bubble ->
    iconDelayMs = 100
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(posts).hasSize(1)
    assertThat(posts.single().at).isEqualTo(100)
    assertThat(posts.single().withIcon).isTrue()
  }

  @Test
  fun a_slow_icon_does_not_hold_the_first_post_past_300_ms() = posting { bubble ->
    iconDelayMs = 5_000
    bubble.open(URL, color = Color.RED)
    advanceTimeBy(1_001)

    // The colour disc at 300 ms, and nothing at 1000 ms: there is no fixed one second wait.
    assertThat(posts.map { it.at }).containsExactly(300L)
    assertThat(posts.single().withIcon).isFalse()
    assertThat(posts.single().color).isEqualTo(Color.RED)
  }

  @Test
  fun a_late_icon_updates_the_same_bubble_once() = posting { bubble ->
    iconDelayMs = 500
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(posts).containsExactly(
      Post(URL, 300, withIcon = false, withSummary = true, color = 0),
      Post(URL, 500, withIcon = true, withSummary = false, color = Color.BLUE),
    ).inOrder()
  }

  @Test
  fun three_links_post_without_waiting_for_each_other() = posting { bubble ->
    iconDelayMs = 250
    val urls = listOf("https://one.example/", "https://two.example/", "https://three.example/")
    urls.forEach { bubble.open(it) }
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactlyElementsIn(urls)
    assertThat(posts.all { it.withIcon }).isTrue()
    assertThat(posts.map { it.at }).containsExactly(250L, 250L, 250L)
    assertThat(posts.maxOf { it.at } - posts.minOf { it.at }).isAtMost(300L)
  }

  @Test
  fun a_bubble_android_does_not_show_gets_no_late_update() = posting { bubble ->
    shown = false
    iconDelayMs = 500
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(posts.map { it.at }).containsExactly(300L)
    verify(exactly = 1) { notifications.cancel(URL, false) }
    verify(exactly = 1) { tabsManager.openRefusedBubble(application, any(), BubbleRefusal.NOT_SHOWN, false, LinkSource.NONE) }
    // The flag is read for up to 1.5 s after the post before the page goes to a tab.
    assertThat(refusedAt).isEqualTo(300L + 1_500L)
  }

  @Test
  fun two_sends_of_one_link_post_in_turn() = posting { bubble ->
    iconDelayMs = 500
    bubble.open(URL)
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(posts.map { it.at }).containsExactly(300L, 500L, 800L, 1_000L).inOrder()
  }

  @Test
  fun an_incognito_bubble_posts_at_once_with_no_icon_wait() = posting { bubble ->
    iconDelayMs = 500
    bubble.open(URL, incognito = true)
    advanceUntilIdle()

    assertThat(posts).hasSize(1)
    assertThat(posts.single().at).isEqualTo(0L)
    assertThat(posts.single().withIcon).isFalse()
    verify(exactly = 0) { repo.getWebsiteWithoutSaving(any()) }
    verify(exactly = 0) { repo.getWebsite(any()) }
    coVerify(exactly = 0) { icons.getBubbleIconAndColor(any()) }
  }

  @Test
  fun an_update_replaces_the_posted_bubble_and_keeps_the_summary() {
    val manager = BubbleNotificationManager(application)
    val website = Website("Title", URL, null, null, null, null, false, 0, 0)
    val first = BubbleLoadData(website, false, false, false, color = Color.RED)
    val icon = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    runBlocking {
      manager.showBubbles(first)
      manager.showBubbles(first.copy(icon = icon, color = Color.BLUE), withSummary = false)
    }

    val all = shadowOf(application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).allNotifications
    val bubble = all.single { it.shortcutId == bubbleConversationId(URL) }
    assertThat(bubble.color).isEqualTo(Color.BLUE)
    assertThat(bubble.bubbleMetadata!!.deleteIntent).isNotNull()
    assertThat(bubble.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
    val summary = all.single { it.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
    assertThat(summary.color).isEqualTo(Color.RED)
    assertThat(all).hasSize(2)
  }

  @Test
  fun no_link_lock_is_kept_once_every_bubble_has_posted() = posting { bubble ->
    iconDelayMs = 500
    bubble.open(URL)
    bubble.open(URL)
    bubble.open("https://example.com/other")
    advanceUntilIdle()

    val locks = NativeFloatingBubble::class.java.getDeclaredField("postLocks").apply { isAccessible = true }
    assertThat(locks.get(bubble) as Map<*, *>).isEmpty()
    assertThat(posts).hasSize(6)
  }

  @Test
  fun a_late_update_does_not_bring_back_a_dismissed_bubble() {
    val manager = BubbleNotificationManager(application)
    val website = Website("Title", URL, null, null, null, null, false, 0, 0)
    val first = BubbleLoadData(website, false, false, false, color = Color.RED)
    val icon = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    runBlocking {
      manager.showBubbles(first)
      manager.dismissed(bubbleNotificationId(URL))
      manager.showBubbles(first.copy(icon = icon, color = Color.BLUE), withSummary = false)
    }

    val all = shadowOf(application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).allNotifications
    assertThat(all).isEmpty()
  }

  @Test
  fun a_shown_bubble_is_listed_as_a_native_bubble_only() = posting { bubble ->
    assertThat(bubble.open(URL)).isTrue()
    advanceUntilIdle()

    assertThat(listed().map { it.url }).containsExactly(URL)
    assertThat(registry.list(BrowsingMode.WebHeads)).isEmpty()
  }

  @Test
  fun links_opened_in_turn_are_listed_in_that_order() = posting { bubble ->
    val urls = listOf("https://one.example/", "https://two.example/", "https://three.example/")
    urls.forEach {
      bubble.open(it)
      advanceUntilIdle()
    }

    assertThat(listed().map { it.url }).containsExactlyElementsIn(urls).inOrder()
  }

  @Test
  fun a_late_icon_keeps_one_entry() = posting { bubble ->
    iconDelayMs = 500
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(posts).hasSize(2)
    assertThat(listed()).hasSize(1)
  }

  @Test
  fun two_sends_of_one_link_keep_one_entry() = posting { bubble ->
    bubble.open(URL)
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(posts).hasSize(2)
    assertThat(listed()).hasSize(1)
  }

  @Test
  fun a_normal_and_an_incognito_bubble_of_one_link_are_two_entries() = posting { bubble ->
    bubble.open(URL)
    bubble.open(URL, incognito = true)
    advanceUntilIdle()

    assertThat(listed().map { it.incognito }).containsExactly(false, true).inOrder()
  }

  @Test
  fun a_link_the_gate_refuses_is_not_listed() = posting { bubble ->
    every { gate.refusal(any()) } returns BubbleRefusal.BUBBLES_OFF

    assertThat(bubble.open(URL)).isFalse()
    advanceUntilIdle()

    assertThat(listed()).isEmpty()
  }

  @Test
  fun a_bubble_dismissed_while_its_icon_loads_stays_off_the_list() = posting { bubble ->
    iconDelayMs = 500
    bubble.open(URL)
    advanceTimeBy(301)
    assertThat(listed()).hasSize(1)

    personDismisses(URL)
    advanceUntilIdle()

    assertThat(posts).hasSize(2)
    assertThat(listed()).isEmpty()
    assertThat(registry.isClosedOnPurpose(keyOf(URL))).isTrue()
  }

  @Test
  fun a_link_sent_again_and_dismissed_during_that_send_is_listed_after_it_posts() = posting { bubble ->
    bubble.open(URL)
    advanceUntilIdle()

    iconDelayMs = 200
    bubble.open(URL)
    advanceTimeBy(100)
    personDismisses(URL)
    advanceUntilIdle()

    // The send is the newer act, and it posted the bubble again.
    assertThat(listed().map { it.url }).containsExactly(URL)
    assertThat(registry.isClosedOnPurpose(keyOf(URL))).isFalse()
  }

  @Test
  fun a_link_android_refuses_is_off_the_list_before_the_tab_opens() = posting { bubble ->
    shown = false
    bubble.open(URL)
    advanceUntilIdle()

    verify(exactly = 1) { tabsManager.openRefusedBubble(application, any(), BubbleRefusal.NOT_SHOWN, false, LinkSource.NONE) }
    assertThat(listedAtRefusal).isEmpty()
    assertThat(listed()).isEmpty()
    assertThat(registry.isClosedOnPurpose(keyOf(URL))).isFalse()
  }

  @Test
  fun a_second_send_android_refuses_takes_the_entry_off_the_list() = posting { bubble ->
    bubble.open(URL)
    advanceUntilIdle()
    assertThat(listed()).hasSize(1)

    shown = false
    bubble.open(URL)
    advanceUntilIdle()

    verify(exactly = 1) { notifications.cancel(URL, false) }
    assertThat(listed()).isEmpty()
  }

  @Test
  fun a_refused_link_leaves_the_others_in_order() = posting { bubble ->
    val refused = "https://refused.example/"
    notShown += refused
    bubble.open("https://one.example/")
    bubble.open(refused)
    bubble.open("https://two.example/")
    advanceUntilIdle()

    assertThat(listed().map { it.url }).containsExactly("https://one.example/", "https://two.example/").inOrder()
  }

  @Test
  fun a_refused_incognito_link_leaves_the_list_too() = posting { bubble ->
    shown = false
    bubble.open(URL, incognito = true)
    advanceUntilIdle()

    assertThat(listed()).isEmpty()
    assertThat(registry.isClosedOnPurpose(keyOf(URL, incognito = true))).isFalse()
  }

  private companion object {
    const val URL = "https://example.com/bubble"
  }
}
