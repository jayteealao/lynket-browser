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
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.os.Looper
import android.graphics.Color
import android.os.Build
import android.service.notification.StatusBarNotification
import arun.com.chromer.appdetect.LinkSource
import arun.com.chromer.browsing.icons.WebsiteIconData
import arun.com.chromer.browsing.icons.WebsiteIconsProvider
import arun.com.chromer.browsing.webview.EmbeddableWebViewActivity
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubbleLoadData
import arun.com.chromer.bubbles.BubblePlace
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.OpenBubble
import arun.com.chromer.bubbles.bubbleConversationId
import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import timber.log.Timber
import java.time.Duration

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
  private var failingIconLoads = 0
  private var shown = true
  private val notShown = mutableSetOf<String>()
  private var refusedAt = -1L
  private var listedAtRefusal: List<OpenBubble>? = null
  /** What Android answers to a read of the places; null is a read that failed. */
  private var places: Map<Int, BubblePlace>? = null
  private val readsAt = mutableListOf<Long>()
  /** How long Android takes to bring back an overflow bubble after its update; null is never. */
  private var returnAfterMs: Long? = 0L
  private val backAt = mutableMapOf<Int, Long>()
  /** The links posted by the path out of the overflow. */
  private val returned = mutableListOf<String>()
  private var failUpdates = false
  private val postedData = mutableListOf<BubbleLoadData>()
  private var heightOfAFirstPost: Int? = null
  private val logs = mutableListOf<String>()
  private val tree = object : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
      logs += message
    }
  }

  private fun isBack(id: Int) = backAt[id]?.let { clock.currentTime >= it } == true

  private val repo = mockk<WebsiteRepository> {
    every { getWebsite(any()) } answers { flowOf(Website(firstArg<String>())) }
    every { getWebsiteWithoutSaving(any()) } answers { flowOf(Website(firstArg<String>())) }
  }

  private val icons = mockk<WebsiteIconsProvider> {
    coEvery { getBubbleIconAndColor(any()) } coAnswers {
      delay(iconDelayMs)
      if (failingIconLoads > 0) {
        failingIconLoads--
        error("No icon")
      }
      WebsiteIconData(firstArg(), Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), Color.BLUE)
    }
  }

  private fun posted(data: BubbleLoadData, withSummary: Boolean): BubbleLoadData {
    posts += Post(data.website.url, clock.currentTime, data.icon != null, withSummary, data.color)
    postedData += data
    return heightOfAFirstPost?.let { data.copy(desiredHeightDp = data.desiredHeightDp ?: it) } ?: data
  }

  private val notifications = mockk<BubbleNotificationManager> {
    coEvery { showBubbles(any(), any()) } coAnswers {
      val data = firstArg<BubbleLoadData>()
      val withSummary = secondArg<Boolean>()
      if (!withSummary && failUpdates) error("The update failed")
      posted(data, withSummary)
    }
    coEvery { returnBubble(any()) } coAnswers {
      val data = firstArg<BubbleLoadData>()
      if (failUpdates) error("The return failed")
      returned += data.website.url
      returnAfterMs?.let { backAt[bubbleNotificationId(data.website.url, data.incognito)] = clock.currentTime + it }
      posted(data, withSummary = false)
    }
    every { isShownAsBubble(any(), any()) } answers {
      val id = bubbleNotificationId(firstArg(), secondArg())
      shown && firstArg<String>() !in notShown && (places?.get(id) != BubblePlace.OVERFLOW || isBack(id))
    }
    every { cancel(any<String>(), any()) } just Runs
    every { dismissed(any()) } just Runs
    every { bubblePlaces() } answers {
      readsAt += clock.currentTime
      places?.mapValues { (id, place) -> if (isBack(id)) BubblePlace.STACK else place }
    }
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
  private lateinit var closer: BubbleCloser
  private lateinit var watcher: BubbleStackWatcher

  @Before
  fun plant() = Timber.plant(tree)

  @After
  fun uproot() = Timber.uproot(tree)

  /** Runs [block] on virtual time with a bubble on the same clock, then runs every job to the end. */
  private fun posting(block: suspend TestScope.(NativeFloatingBubble) -> Unit) = runTest {
    clock = testScheduler
    val dispatcher = StandardTestDispatcher(testScheduler)
    val reconciler = BubbleReconciler(notifications, registry, dispatcher)
    val bubble = NativeFloatingBubble(application, repo, notifications, icons, gate, registry, reconciler, dispatcher) { tabsManager }
    watcher = BubbleStackWatcher { bubble }
    closer = BubbleCloser(notifications, registry, reconciler, bubble, watcher, dispatcher)
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

  @Test
  fun an_open_takes_off_an_older_link_that_android_no_longer_holds() = posting { bubble ->
    val older = "https://example.com/older"
    bubble.open(older)
    advanceUntilIdle()
    assertThat(listed().map { it.url }).containsExactly(older)

    places = mapOf(bubbleNotificationId(URL) to BubblePlace.STACK)
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(listed().map { it.url }).containsExactly(URL)
    assertThat(registry.isClosedOnPurpose(keyOf(older))).isFalse()
  }

  @Test
  fun an_open_reads_where_the_older_bubbles_are() = posting { bubble ->
    val older = "https://example.com/older"
    bubble.open(older)
    advanceUntilIdle()

    places = mapOf(bubbleNotificationId(older) to BubblePlace.OVERFLOW, bubbleNotificationId(URL) to BubblePlace.STACK)
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(registry.overflow(BrowsingMode.NativeBubbles).map { it.url }).containsExactly(older)
  }

  @Test
  fun a_link_android_refuses_runs_no_reconcile() = posting { bubble ->
    shown = false
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(readsAt).isEmpty()
  }

  @Test
  fun a_late_icon_does_not_delay_the_reconcile() = posting { bubble ->
    iconDelayMs = 5_000
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(readsAt).containsExactly(300L)
    assertThat(posts.map { it.at }).containsExactly(300L, 5_000L).inOrder()
  }

  @Test
  fun a_failed_read_on_open_leaves_the_list_as_it_was() = posting { bubble ->
    val older = "https://example.com/older"
    bubble.open(older)
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(readsAt).hasSize(2)
    assertThat(listed().map { it.url }).containsExactly(older, URL).inOrder()
  }

  private fun stored(url: String = URL, incognito: Boolean = false) =
    registry.find(keyOf(url, incognito))?.loadData

  private fun TestScope.opened(bubble: NativeFloatingBubble, vararg links: String) = links.forEach {
    bubble.open(it)
    advanceUntilIdle()
  }

  /** Android holds [links] in its overflow and every other open bubble in the stack. */
  private fun inOverflow(vararg links: String) {
    registry.reconcile(BrowsingMode.NativeBubbles) { listed().associate { it.key.notificationId to BubblePlace.STACK } }
    places = listed().associate {
      it.key.notificationId to if (it.url in links) BubblePlace.OVERFLOW else BubblePlace.STACK
    }
    posts.clear()
    postedData.clear()
  }

  private fun overflow() = registry.overflow(BrowsingMode.NativeBubbles).map { it.url }

  @Test
  fun the_newest_overflow_bubble_posts_its_stored_data_as_an_update() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE)
    inOverflow(ONE, TWO)
    val data = stored(TWO)

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(TWO)
    assertThat(posts.single().withSummary).isFalse()
    assertThat(postedData.single()).isSameInstanceAs(data)
    assertThat(overflow()).containsExactly(ONE)
  }

  @Test
  fun a_return_takes_the_path_out_of_the_overflow_and_its_late_icon_does_not() = posting { bubble ->
    failingIconLoads = 1
    opened(bubble, ONE, TWO)
    inOverflow(ONE)

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(returned).containsExactly(ONE)
    assertThat(posts.map { it.withIcon }).containsExactly(false, true).inOrder()
  }

  @Test
  fun a_read_while_a_return_has_taken_the_notification_down_keeps_the_bubble() = posting { bubble ->
    opened(bubble, ONE, TWO)
    inOverflow(ONE)
    val reconciler = BubbleReconciler(notifications, registry, StandardTestDispatcher(testScheduler))
    returnAfterMs = 1_000

    bubble.returnNewestOverflowBubble()
    advanceTimeBy(SETTLE_MS + 1)
    // Android has the cancel and not yet the post.
    val working = places
    places = working!!.filterKeys { it != keyOf(ONE).notificationId }
    reconciler.reconcile()
    assertThat(listed().map { it.url }).containsExactly(ONE, TWO).inOrder()

    places = working
    advanceUntilIdle()

    assertThat(listed().map { it.url }).containsExactly(ONE, TWO).inOrder()
    assertThat(overflow()).isEmpty()
  }

  @Test
  fun a_return_that_lost_the_notification_lets_the_next_read_drop_the_bubble() = posting { bubble ->
    opened(bubble, ONE, TWO)
    inOverflow(ONE)
    returnAfterMs = null

    bubble.returnNewestOverflowBubble()
    advanceTimeBy(SETTLE_MS + 1)
    places = places!!.filterKeys { it != keyOf(ONE).notificationId }
    advanceUntilIdle()

    assertThat(listed().map { it.url }).containsExactly(TWO)
  }

  @Test
  fun a_late_icon_of_a_bubble_that_aged_out_posts_nothing_and_waits_for_its_return() = posting { bubble ->
    iconDelayMs = 1_000
    bubble.open(ONE)
    advanceTimeBy(ICON_AND_POST_MS)
    posts.clear()
    places = mapOf(keyOf(ONE).notificationId to BubblePlace.OVERFLOW)
    advanceUntilIdle()

    assertThat(posts).isEmpty()
    assertThat(stored(ONE)?.icon).isNotNull()
  }

  @Test
  fun a_read_while_a_link_sent_again_posts_keeps_its_entry() = posting { bubble ->
    val reconciler = BubbleReconciler(notifications, registry, StandardTestDispatcher(testScheduler))
    opened(bubble, ONE, TWO)
    iconDelayMs = 100
    // Android has the cancel of the link sent again and not yet its post.
    places = mapOf(keyOf(TWO).notificationId to BubblePlace.STACK)
    shown = false

    bubble.open(ONE)
    advanceTimeBy(101)
    reconciler.reconcile()
    assertThat(listed().map { it.url }).containsExactly(ONE, TWO).inOrder()

    shown = true
    places = places!! + (keyOf(ONE).notificationId to BubblePlace.STACK)
    advanceUntilIdle()
    assertThat(listed().map { it.url }).containsExactly(ONE, TWO).inOrder()
  }

  /** The system a real manager called, with [activeFlags] the flags of the posted bubble, if any. */
  private fun systemCalls(
    activeFlags: Int?,
    readFails: Boolean = false,
    block: suspend (BubbleNotificationManager) -> Unit,
  ): NotificationManager {
    val active = activeFlags?.let { flags ->
      mockk<StatusBarNotification> {
        every { id } returns bubbleNotificationId(URL)
        every { notification } returns Notification().apply { this.flags = flags }
      }
    }
    val system = mockk<NotificationManager>(relaxed = true) {
      if (readFails) {
        every { activeNotifications } throws RuntimeException("No read")
      } else {
        every { activeNotifications } returns listOfNotNull(active).toTypedArray()
      }
    }
    val context = spyk(application) {
      every { getSystemService(Context.NOTIFICATION_SERVICE) } returns system
    }
    runBlocking { block(BubbleNotificationManager(context)) }
    return system
  }

  @Test
  fun a_return_cancels_only_the_bubble_and_then_posts_it() {
    val system = systemCalls(activeFlags = 0) { it.returnBubble(loadData()) }

    verifyOrder {
      system.cancel(bubbleNotificationId(URL))
      system.notify(bubbleNotificationId(URL), any())
    }
    verify(exactly = 1) { system.cancel(any()) }
    verify(exactly = 1) { system.notify(any(), any()) }
  }

  @Test
  fun a_return_cancels_a_bubble_with_the_flag_too() {
    val system = systemCalls(activeFlags = Notification.FLAG_BUBBLE) { it.returnBubble(loadData()) }

    verifyOrder {
      system.cancel(bubbleNotificationId(URL))
      system.notify(bubbleNotificationId(URL), any())
    }
  }

  @Test
  @Config(application = Application::class, sdk = [Build.VERSION_CODES.R])
  fun on_android_11_a_return_updates_in_place() {
    val system = systemCalls(activeFlags = 0) { it.returnBubble(loadData()) }

    verify(exactly = 0) { system.cancel(any()) }
    verify(exactly = 1) { system.notify(bubbleNotificationId(URL), any()) }
  }

  @Test
  @Config(application = Application::class, sdk = [Build.VERSION_CODES.R])
  fun on_android_11_a_link_sent_again_updates_in_place() {
    val system = systemCalls(activeFlags = 0) { it.showBubbles(loadData()) }

    verify(exactly = 0) { system.cancel(any()) }
  }

  @Test
  fun a_return_of_a_bubble_no_longer_posted_does_nothing() {
    val system = systemCalls(activeFlags = null) { it.returnBubble(loadData()) }

    verify(exactly = 0) { system.cancel(any()) }
    verify(exactly = 0) { system.notify(any(), any()) }
  }

  @Test
  fun a_returned_bubble_keeps_its_height_and_alerts_once() {
    val notification = slot<Notification>()
    val system = systemCalls(activeFlags = 0) { it.returnBubble(loadData().copy(desiredHeightDp = 731)) }

    verify { system.notify(any(), capture(notification)) }
    assertThat(notification.captured.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
    assertThat(notification.captured.bubbleMetadata!!.desiredHeight).isEqualTo(731)
  }

  @Test
  fun a_link_sent_again_comes_out_of_the_overflow_and_keeps_the_summary_posted() {
    val system = systemCalls(activeFlags = 0) { it.showBubbles(loadData()) }

    verifyOrder {
      system.notify(BUBBLE_NOTIFICATION_GROUP.hashCode(), any())
      system.cancel(bubbleNotificationId(URL))
      system.notify(bubbleNotificationId(URL), any())
    }
    verify(exactly = 1) { system.cancel(any()) }
  }

  @Test
  fun a_link_sent_again_in_the_stack_updates_in_place() {
    val system = systemCalls(activeFlags = Notification.FLAG_BUBBLE) { it.showBubbles(loadData()) }

    verify(exactly = 0) { system.cancel(any()) }
  }

  @Test
  fun a_first_post_cancels_nothing() {
    val system = systemCalls(activeFlags = null) { it.showBubbles(loadData()) }

    verify(exactly = 0) { system.cancel(any()) }
  }

  @Test
  fun an_update_never_cancels() {
    val system = systemCalls(activeFlags = Notification.FLAG_BUBBLE) { it.showBubbles(loadData(), withSummary = false) }

    verify(exactly = 0) { system.cancel(any()) }
    verify(exactly = 1) { system.notify(any(), any()) }
  }

  @Test
  fun a_failed_read_takes_no_link_sent_again_down() {
    val system = systemCalls(activeFlags = 0, readFails = true) { it.showBubbles(loadData()) }

    verify(exactly = 0) { system.cancel(any()) }
    verify(exactly = 1) { system.notify(bubbleNotificationId(URL), any()) }
  }

  @Test
  fun two_calls_return_two_bubbles_newest_first_and_the_second_waits_for_the_first_flag() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE)
    inOverflow(ONE, TWO)
    returnAfterMs = 1_000
    val start = currentTime

    bubble.returnNewestOverflowBubble()
    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(TWO, ONE).inOrder()
    assertThat(posts.map { it.at }).containsExactly(start + SETTLE_MS, start + SETTLE_MS + 1_000).inOrder()
    assertThat(overflow()).isEmpty()
  }

  @Test
  fun a_drag_of_the_stack_returns_the_overflow_bubble_and_not_a_bubble_android_is_closing() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE)
    // Android took the flag off the whole stack at once, and its delete intents are on the way.
    inOverflow(ONE, TWO, THREE)

    bubble.returnNewestOverflowBubble()
    advanceTimeBy(50)
    personDismisses(TWO)
    bubble.returnNewestOverflowBubble()
    advanceTimeBy(50)
    personDismisses(THREE)
    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(ONE)
  }

  @Test
  fun a_single_close_of_a_full_stack_returns_at_once() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE, FOUR)
    inOverflow(ONE, TWO)
    registry.reconcile(BrowsingMode.NativeBubbles) { places }
    val start = currentTime

    closer.close(keyOf(FOUR).notificationId)
    advanceUntilIdle()

    assertThat(posts.single().url).isEqualTo(TWO)
    assertThat(posts.single().at).isEqualTo(start)
  }

  @Test
  fun a_bubble_that_aged_out_unseen_costs_the_close_the_wait_and_is_then_filed() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE, FOUR, FIVE)
    inOverflow(ONE)
    registry.reconcile(BrowsingMode.NativeBubbles) { places }
    // The list still has FOUR in the stack, and Android has already aged it out.
    places = places!! + (keyOf(FOUR).notificationId to BubblePlace.OVERFLOW)
    val start = currentTime

    closer.close(keyOf(FIVE).notificationId)
    advanceUntilIdle()

    assertThat(posts.single().url).isEqualTo(FOUR)
    assertThat(posts.single().at).isEqualTo(start + SETTLE_MS)
  }

  @Test
  fun two_single_closes_in_turn_each_return_a_bubble_newest_first() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE, FOUR)
    inOverflow(ONE, TWO)
    registry.reconcile(BrowsingMode.NativeBubbles) { places }

    closer.close(keyOf(FOUR).notificationId)
    advanceUntilIdle()
    closer.close(keyOf(THREE).notificationId)
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(TWO, ONE).inOrder()
  }

  @Test
  fun a_drag_of_the_whole_stack_waits_and_returns_only_overflow_bubbles() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE, FOUR)
    inOverflow(ONE, TWO)
    // Every bubble of the stack lost its flag before the first delete intent.
    places = places!!.mapValues { BubblePlace.OVERFLOW }
    val start = currentTime

    closer.close(keyOf(FOUR).notificationId)
    advanceTimeBy(50)
    closer.close(keyOf(THREE).notificationId)
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(TWO, ONE).inOrder()
    assertThat(posts.first().at).isEqualTo(start + SETTLE_MS)
  }

  @Test
  fun a_drag_read_in_part_waits_and_leaves_the_closing_bubbles_in_the_stack() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE, FOUR, FIVE)
    inOverflow(ONE, TWO)
    // Android took the flag off FOUR and not yet off THREE, and FIVE's delete intent came first.
    places = places!! + (keyOf(FOUR).notificationId to BubblePlace.OVERFLOW)
    val start = currentTime

    closer.close(keyOf(FIVE).notificationId)
    advanceTimeBy(1)

    assertThat(posts).isEmpty()
    assertThat(registry.find(keyOf(FOUR))?.place).isEqualTo(BubblePlace.STACK)

    closer.close(keyOf(FOUR).notificationId)
    closer.close(keyOf(THREE).notificationId)
    places = places!!.filterKeys { it != keyOf(FOUR).notificationId && it != keyOf(THREE).notificationId }
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(TWO, ONE).inOrder()
    assertThat(posts.first().at).isEqualTo(start + SETTLE_MS)
  }

  @Test
  fun a_failed_read_after_a_close_waits_as_for_a_drag() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE, FOUR)
    inOverflow(ONE, TWO)
    val working = places
    places = null
    val start = currentTime

    closer.close(keyOf(FOUR).notificationId)
    advanceTimeBy(SETTLE_MS - 1)
    places = working
    advanceUntilIdle()

    assertThat(posts.single().at).isEqualTo(start + SETTLE_MS)
  }

  @Test
  fun a_close_in_the_stack_returns_the_newest_overflow_bubble_and_the_closed_one_stays_gone() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE, FOUR)
    inOverflow(ONE, TWO)

    closer.close(keyOf(FOUR).notificationId)
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(TWO)
    assertThat(posts.single().withSummary).isFalse()
    assertThat(listed().map { it.url }).containsExactly(ONE, TWO, THREE).inOrder()
    assertThat(registry.isClosedOnPurpose(keyOf(FOUR))).isTrue()
    verify { notifications.dismissed(keyOf(FOUR).notificationId) }
  }

  @Test
  fun a_second_close_of_one_bubble_returns_no_second_bubble() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE, FOUR)
    inOverflow(ONE, TWO)

    closer.close(keyOf(FOUR).notificationId)
    closer.close(keyOf(FOUR).notificationId)
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(TWO)
  }

  @Test
  fun a_close_in_the_overflow_returns_nothing() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE, FOUR)
    inOverflow(ONE, TWO)
    registry.reconcile(BrowsingMode.NativeBubbles) { places }

    closer.close(keyOf(TWO).notificationId)
    advanceUntilIdle()

    assertThat(posts).isEmpty()
    assertThat(listed().map { it.url }).containsExactly(ONE, THREE, FOUR).inOrder()
  }

  @Test
  fun a_call_with_an_empty_overflow_posts_nothing_and_still_finishes() = posting { bubble ->
    opened(bubble, ONE, TWO)
    inOverflow(ONE)

    val calls = List(2) { bubble.returnNewestOverflowBubble() }
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(ONE)
    assertThat(calls.all { it.isCompleted }).isTrue()
  }

  @Test
  fun a_late_icon_a_link_sent_again_and_a_return_all_keep_the_first_height() = posting { bubble ->
    heightOfAFirstPost = 731
    iconDelayMs = 500
    bubble.open(ONE)
    advanceUntilIdle()
    assertThat(postedData.map { it.desiredHeightDp }).containsExactly(null, 731).inOrder()

    heightOfAFirstPost = 566
    iconDelayMs = 0
    bubble.open(ONE)
    advanceUntilIdle()
    assertThat(postedData.last().desiredHeightDp).isEqualTo(731)

    opened(bubble, TWO)
    backAt.clear()
    inOverflow(ONE)
    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()
    assertThat(postedData.single().desiredHeightDp).isEqualTo(731)
  }

  @Test
  fun a_bubble_in_the_stack_is_never_posted() = posting { bubble ->
    opened(bubble, ONE, TWO)
    inOverflow()

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts).isEmpty()
  }

  @Test
  fun a_bubble_closed_on_purpose_is_never_posted() = posting { bubble ->
    opened(bubble, ONE, TWO)
    inOverflow(TWO)
    registry.markClosedOnPurpose(keyOf(TWO))

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts).isEmpty()
  }

  @Test
  fun an_incognito_bubble_returns_with_its_data_and_reads_nothing_about_the_site() = posting { bubble ->
    bubble.open(ONE, incognito = true)
    advanceUntilIdle()
    opened(bubble, TWO)
    inOverflow(ONE)
    clearMocks(repo, icons, answers = false)

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(ONE)
    assertThat(postedData.single().incognito).isTrue()
    verify(exactly = 0) { repo.getWebsite(any()) }
    coVerify(exactly = 0) { icons.getBubbleIconAndColor(any()) }
  }

  private fun assertTheReturnFailedQuietly() {
    assertThat(listed()).hasSize(2)
    assertThat(overflow()).containsExactly(ONE)
    assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    verify(exactly = 0) { tabsManager.openRefusedBubble(any(), any(), any(), any(), any()) }
    verify(exactly = 0) { notifications.cancel(any<String>(), any()) }
    assertThat(logs.filter { it.startsWith("Returned:") }).isEmpty()
  }

  @Test
  fun a_failed_return_shows_no_message_and_keeps_the_total() = posting { bubble ->
    opened(bubble, ONE, TWO)
    inOverflow(ONE)
    failUpdates = true

    val call = bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(call.isCompleted).isTrue()
    assertTheReturnFailedQuietly()
  }

  @Test
  fun a_bubble_that_ages_out_while_its_icon_loads_keeps_the_icon_and_posts_nothing() = posting { bubble ->
    failingIconLoads = 2
    iconDelayMs = 1_000
    opened(bubble, ONE, TWO)
    backAt.clear()
    inOverflow(ONE)
    failingIconLoads = 0

    bubble.returnNewestOverflowBubble()
    advanceTimeBy(SETTLE_MS + 100)
    assertThat(posts.map { it.url }).containsExactly(ONE)
    inOverflow(ONE)
    backAt.clear()
    advanceUntilIdle()

    assertThat(posts.map { it.url }).isEmpty()
    assertThat(stored(ONE)?.icon).isNotNull()
  }

  @Test
  fun a_read_before_android_lists_the_post_keeps_the_new_bubble() = posting { bubble ->
    val reconciler = BubbleReconciler(notifications, registry, StandardTestDispatcher(testScheduler))
    opened(bubble, ONE)
    shown = false
    places = mapOf(keyOf(ONE).notificationId to BubblePlace.STACK)

    bubble.open(TWO)
    advanceTimeBy(ICON_AND_POST_MS)
    reconciler.reconcile()
    assertThat(listed().map { it.url }).containsExactly(ONE, TWO).inOrder()

    shown = true
    places = places!! + (keyOf(TWO).notificationId to BubblePlace.STACK)
    advanceUntilIdle()
    assertThat(listed().map { it.url }).containsExactly(ONE, TWO).inOrder()
  }

  @Test
  fun a_stack_as_full_as_it_has_been_takes_no_return() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE)
    registry.reconcile(BrowsingMode.NativeBubbles) {
      mapOf(keyOf(ONE).notificationId to BubblePlace.OVERFLOW, keyOf(TWO).notificationId to BubblePlace.STACK, keyOf(THREE).notificationId to BubblePlace.STACK)
    }
    places = mapOf(keyOf(ONE).notificationId to BubblePlace.OVERFLOW, keyOf(TWO).notificationId to BubblePlace.STACK, keyOf(THREE).notificationId to BubblePlace.STACK)
    posts.clear()

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts).isEmpty()
  }

  @Test
  fun a_failed_return_ends_the_closes_that_wait_behind_it() = posting { bubble ->
    opened(bubble, ONE, TWO, THREE)
    inOverflow(ONE, TWO)
    returnAfterMs = null

    repeat(3) { bubble.returnNewestOverflowBubble() }
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(TWO)
  }

  @Test
  fun a_flag_that_never_shows_changes_nothing_and_the_next_call_tries_again() = posting { bubble ->
    opened(bubble, ONE, TWO)
    inOverflow(ONE)
    returnAfterMs = null

    val call = bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(call.isCompleted).isTrue()
    assertThat(posts.map { it.url }).containsExactly(ONE)
    assertTheReturnFailedQuietly()

    returnAfterMs = 0
    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts.map { it.url }).containsExactly(ONE, ONE)
    assertThat(overflow()).isEmpty()
  }

  @Test
  fun a_bubble_returned_during_its_first_load_posts_the_disc_at_once_then_the_icon() = posting { bubble ->
    iconDelayMs = 5_000
    bubble.open(ONE, color = Color.RED)
    advanceTimeBy(1_000)
    inOverflow(ONE)

    bubble.returnNewestOverflowBubble()
    advanceTimeBy(1_000)

    assertThat(posts.map { it.at to it.withIcon }).containsExactly(1_000L + SETTLE_MS to false)

    advanceUntilIdle()

    // The disc never comes after the icon.
    assertThat(posts.drop(1).all { it.withIcon }).isTrue()
    assertThat(posts.size).isGreaterThan(1)
    assertThat(posts.last().at).isEqualTo(5_000L)
    assertThat(stored(ONE)?.icon).isNotNull()
  }

  @Test
  fun a_bubble_whose_load_failed_returns_with_the_disc_then_gets_its_icon() = posting { bubble ->
    failingIconLoads = 1
    opened(bubble, ONE, TWO)
    inOverflow(ONE)

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts.map { it.withIcon }).containsExactly(false, true).inOrder()
    assertThat(posts.last().withSummary).isFalse()
    assertThat(stored(ONE)?.icon).isNotNull()
    assertThat(stored(ONE)?.color).isEqualTo(Color.BLUE)
  }

  @Test
  fun the_icon_update_keeps_the_posted_website() = posting { bubble ->
    failingIconLoads = 1
    opened(bubble, ONE, TWO)
    inOverflow(ONE)
    val posted = stored(ONE)!!.website
    every { repo.getWebsite(any()) } answers {
      flowOf(Website("A new title", firstArg(), null, null, null, null, false, 0, 0))
    }

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(postedData.last().website).isSameInstanceAs(posted)
    assertThat(stored(ONE)?.website).isSameInstanceAs(posted)
  }

  @Test
  fun a_load_that_fails_again_posts_nothing_more() = posting { bubble ->
    failingIconLoads = 1
    opened(bubble, ONE, TWO)
    inOverflow(ONE)
    failingIconLoads = 1

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(posts.map { it.withIcon }).containsExactly(false)
  }

  @Test
  fun a_bubble_closed_during_the_load_gets_no_icon_post() = posting { bubble ->
    failingIconLoads = 1
    opened(bubble, ONE, TWO)
    inOverflow(ONE)
    iconDelayMs = 1_000

    bubble.returnNewestOverflowBubble()
    advanceTimeBy(SETTLE_MS + 500)
    personDismisses(ONE)
    advanceUntilIdle()

    assertThat(posts.map { it.withIcon }).containsExactly(false)
    assertThat(listed().map { it.url }).containsExactly(TWO)
  }

  @Test
  fun a_slow_icon_does_not_hold_the_next_return() = posting { bubble ->
    failingIconLoads = 2
    opened(bubble, ONE, TWO, THREE)
    inOverflow(ONE, TWO)
    iconDelayMs = 5_000
    val start = currentTime

    bubble.returnNewestOverflowBubble()
    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    val returned = start + SETTLE_MS
    assertThat(posts.filterNot { it.withIcon }.map { it.url to it.at })
      .containsExactly(TWO to returned, ONE to returned).inOrder()
    assertThat(posts.filter { it.withIcon }.map { it.at }).containsExactly(returned + 5_000, returned + 5_000)
  }

  @Test
  fun the_returned_line_holds_the_id_and_no_address() = posting { bubble ->
    opened(bubble, ONE, TWO)
    inOverflow(ONE)

    bubble.returnNewestOverflowBubble()
    advanceUntilIdle()

    assertThat(logs.filter { it.startsWith("Returned:") }).containsExactly("Returned: ${keyOf(ONE).notificationId}")
    assertThat(logs.filter { "one.example" in it }).isEmpty()
  }

  private fun postedBubble(url: String = URL): Notification? =
    shadowOf(application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .allNotifications.singleOrNull { it.shortcutId == bubbleConversationId(url) }

  private fun loadData(url: String = URL) =
    BubbleLoadData(Website("Title", url, null, null, null, null, false, 0, 0), false, false, false, color = Color.RED)

  private fun bubbleIntentOf(url: String, incognito: Boolean): Intent =
    shadowOf(application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .allNotifications.single { it.shortcutId == bubbleConversationId(url, incognito) }
      .let { shadowOf(it.bubbleMetadata!!.intent).savedIntent }

  @Test
  fun a_bubble_intent_names_its_bubble_by_id() {
    runBlocking { BubbleNotificationManager(application).showBubbles(loadData()) }

    assertThat(bubbleIntentOf(URL, incognito = false).getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0))
      .isEqualTo(bubbleNotificationId(URL))
  }

  @Test
  fun an_incognito_bubble_intent_names_its_own_id() {
    val data = BubbleLoadData(Website(URL), false, false, incognito = true)
    runBlocking { BubbleNotificationManager(application).showBubbles(data) }

    assertThat(bubbleIntentOf(URL, incognito = true).getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0))
      .isEqualTo(bubbleNotificationId(URL, incognito = true))
  }

  @Test
  fun the_conversation_shortcut_does_not_name_a_bubble() {
    runBlocking { BubbleNotificationManager(application).showBubbles(loadData()) }

    val shortcut = application.getSystemService(ShortcutManager::class.java).dynamicShortcuts
      .single { it.id == bubbleConversationId(URL) }
    assertThat(shortcut.intent!!.component?.className).isEqualTo(EmbeddableWebViewActivity::class.java.name)
    assertThat(shortcut.intent!!.hasExtra(EXTRA_BUBBLE_NOTIFICATION_ID)).isFalse()
  }

  @Test
  fun a_first_post_may_alert() {
    runBlocking { BubbleNotificationManager(application).showBubbles(loadData()) }

    assertThat(postedBubble()!!.flags and Notification.FLAG_ONLY_ALERT_ONCE).isEqualTo(0)
  }

  @Test
  fun an_update_alerts_once_and_changes_nothing_a_person_reads() {
    val manager = BubbleNotificationManager(application)

    runBlocking { manager.showBubbles(loadData()) }
    val first = postedBubble()!!
    runBlocking { manager.showBubbles(loadData(), withSummary = false) }
    val update = postedBubble()!!

    assertThat(update.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
    assertThat(update.bubbleMetadata!!.autoExpandBubble).isFalse()
    assertThat(update.bubbleMetadata!!.isNotificationSuppressed).isTrue()
    assertThat(update.extras.getCharSequence(Notification.EXTRA_TITLE))
      .isEqualTo(first.extras.getCharSequence(Notification.EXTRA_TITLE))
    assertThat(update.extras.getCharSequence(Notification.EXTRA_TEXT))
      .isEqualTo(first.extras.getCharSequence(Notification.EXTRA_TEXT))
    assertThat(update.shortcutId).isEqualTo(first.shortcutId)
    assertThat(update.bubbleMetadata!!.deleteIntent).isEqualTo(first.bubbleMetadata!!.deleteIntent)
  }

  @Test
  fun an_update_of_a_bubble_that_is_not_posted_posts_nothing() {
    runBlocking { BubbleNotificationManager(application).showBubbles(loadData(), withSummary = false) }

    assertThat(postedBubble()).isNull()
  }

  @Test
  fun an_icon_ready_in_time_leaves_that_data_on_the_entry() = posting { bubble ->
    iconDelayMs = 100
    bubble.open(URL)
    advanceUntilIdle()

    assertThat(stored()?.icon).isNotNull()
    assertThat(stored()?.color).isEqualTo(Color.BLUE)
  }

  @Test
  fun a_slow_icon_leaves_the_disc_data_and_then_the_icon_data() = posting { bubble ->
    iconDelayMs = 500
    bubble.open(URL, color = Color.RED)
    advanceTimeBy(301)

    assertThat(stored()?.icon).isNull()
    assertThat(stored()?.color).isEqualTo(Color.RED)

    advanceUntilIdle()

    assertThat(stored()?.icon).isNotNull()
    assertThat(stored()?.color).isEqualTo(Color.BLUE)
  }

  @Test
  fun a_late_icon_of_a_dismissed_bubble_adds_nothing() = posting { bubble ->
    iconDelayMs = 500
    bubble.open(URL)
    advanceTimeBy(301)
    personDismisses(URL)
    advanceUntilIdle()

    assertThat(listed()).isEmpty()
    assertThat(stored()).isNull()
  }

  @Test
  fun a_second_send_replaces_the_data() = posting { bubble ->
    bubble.open(URL, color = Color.RED)
    advanceUntilIdle()
    val first = stored()

    coEvery { icons.getBubbleIconAndColor(any()) } coAnswers {
      WebsiteIconData(firstArg(), Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888), Color.GREEN)
    }
    bubble.open(URL, color = Color.RED)
    advanceUntilIdle()

    assertThat(stored()).isNotEqualTo(first)
    assertThat(stored()?.color).isEqualTo(Color.GREEN)
  }

  @Test
  fun an_incognito_entry_holds_data_with_no_icon() = posting { bubble ->
    bubble.open(URL, incognito = true)
    advanceUntilIdle()

    assertThat(stored(incognito = true)?.incognito).isTrue()
    assertThat(stored(incognito = true)?.icon).isNull()
  }

  /** Past the check of a page stop, on the main thread of the test, and the returns it starts. */
  private fun TestScope.afterTheFoldCheck() {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(BubbleStackWatcher.FOLD_CHECK_MS))
    advanceUntilIdle()
  }

  /** The stack of a full row holds the rest, with [links] read in the overflow. */
  private fun TestScope.fullStackWith(bubble: NativeFloatingBubble, open: List<String>, vararg links: String) {
    opened(bubble, *open.toTypedArray())
    places = listed().associate {
      it.key.notificationId to if (it.url in links) BubblePlace.OVERFLOW else BubblePlace.STACK
    }
    registry.reconcile(BrowsingMode.NativeBubbles) { places }
    posts.clear()
    postedData.clear()
  }

  private fun id(url: String) = keyOf(url).notificationId

  @Test
  fun a_close_in_an_open_stack_returns_nothing_until_the_stack_folds() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    watcher.pageStarted()

    closer.close(id(FOUR))
    advanceUntilIdle()
    assertThat(posts).isEmpty()

    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(TWO)
  }

  @Test
  fun a_fold_fills_every_free_place_newest_first() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    watcher.pageStarted()
    closer.close(id(THREE))
    closer.close(id(FOUR))

    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(TWO, ONE).inOrder()
    assertThat(overflow()).isEmpty()
  }

  @Test
  fun a_fold_stops_at_the_fullest_stack() = posting { bubble ->
    val eight = (1..8).map { "https://link$it.example/" }
    fullStackWith(bubble, eight, eight[0], eight[1], eight[2])
    watcher.pageStarted()
    closer.close(id(eight[7]))

    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(eight[2])
    assertThat(overflow().size).isEqualTo(2)
  }

  @Test
  fun a_fold_with_no_close_returns_nothing() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    watcher.pageStarted()

    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(posts).isEmpty()
  }

  @Test
  fun a_delete_intent_after_the_page_stopped_still_waits_for_the_fold() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    watcher.pageStarted()

    watcher.pageStopped()
    closer.close(id(FOUR))
    advanceUntilIdle()
    assertThat(posts).isEmpty()
    watcher.pageStarted()
    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(TWO)
  }

  @Test
  fun a_switch_between_bubbles_is_not_a_fold() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    watcher.pageStarted()
    closer.close(id(FOUR))

    // The next page can start on either side of the stop.
    watcher.pageStopped()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(BubbleStackWatcher.FOLD_CHECK_MS - 1))
    watcher.pageStarted()
    afterTheFoldCheck()
    watcher.pageStarted()
    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(posts).isEmpty()
    assertThat(watcher.stackOpen).isTrue()
  }

  @Test
  fun the_x_of_the_last_open_page_leaves_no_open_stack_and_the_return_runs() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE, TWO)
    watcher.pageStarted()

    closer.close(id(THREE))
    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(TWO)
  }

  @Test
  fun a_fold_returns_no_more_than_the_closes_when_a_read_found_a_fuller_stack() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR, FIVE), ONE)
    // A read just after a post: the flag of the bubble Android is ageing out has not cleared yet.
    registry.reconcile(BrowsingMode.NativeBubbles) { listed().associate { it.key.notificationId to BubblePlace.STACK } }
    registry.reconcile(BrowsingMode.NativeBubbles) { places }
    watcher.pageStarted()
    watcher.pageStopped()
    afterTheFoldCheck()
    assertThat(returned).isEmpty()

    watcher.pageStarted()
    closer.close(id(FIVE))
    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(ONE)
  }

  @Test
  fun a_close_with_an_empty_overflow_holds_nothing_for_a_later_fold() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE)
    watcher.pageStarted()
    closer.close(id(ONE))
    closer.close(id(THREE))
    watcher.pageStopped()
    afterTheFoldCheck()
    assertThat(returned).isEmpty()

    fullStackWith(bubble, listOf(FOUR, FIVE), FOUR)
    watcher.pageStarted()
    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).isEmpty()
  }

  @Test
  fun a_close_of_a_folded_stack_after_a_fold_returns_at_once() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    watcher.pageStarted()
    watcher.pageStopped()
    afterTheFoldCheck()
    val start = currentTime

    closer.close(id(FOUR))
    advanceUntilIdle()

    assertThat(posts.single().url).isEqualTo(TWO)
    assertThat(posts.single().at).isEqualTo(start)
  }

  @Test
  fun a_bubble_closed_in_the_overflow_while_the_stack_is_open_stays_closed_at_the_fold() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    watcher.pageStarted()
    closer.close(id(TWO))
    closer.close(id(FOUR))

    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(ONE)
  }

  @Test
  fun a_stack_opened_again_during_a_fold_stops_the_returns_and_the_next_fold_counts_again() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    returnAfterMs = 1_000
    watcher.pageStarted()
    closer.close(id(THREE))
    closer.close(id(FOUR))

    watcher.pageStopped()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(BubbleStackWatcher.FOLD_CHECK_MS))
    advanceTimeBy(100)
    watcher.pageStarted()
    advanceUntilIdle()
    assertThat(returned).containsExactly(TWO)

    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(TWO, ONE).inOrder()
  }

  @Test
  fun a_failed_return_at_a_fold_ends_the_fill_and_the_next_fold_tries_again() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    returnAfterMs = null
    watcher.pageStarted()
    closer.close(id(THREE))
    closer.close(id(FOUR))

    watcher.pageStopped()
    afterTheFoldCheck()
    assertThat(returned).containsExactly(TWO)

    returnAfterMs = 0
    watcher.pageStarted()
    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(TWO, TWO, ONE).inOrder()
  }

  @Test
  fun a_link_sent_again_from_the_overflow_comes_out_at_once_with_the_stack_open() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR), ONE, TWO)
    watcher.pageStarted()

    bubble.open(ONE)
    advanceUntilIdle()

    assertThat(posts.map { it.url }).contains(ONE)
  }

  /** The X of the page of [url], which finishes as [finished] records. */
  private fun x(url: String, finished: MutableList<String>) = closer.closePage(id(url)) { finished += url }

  private fun TestScope.mainRuns() {
    shadowOf(Looper.getMainLooper()).idle()
    runCurrent()
  }

  @Test
  fun the_x_of_the_last_bubble_keeps_it_until_the_next_page_starts() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE, TWO)
    watcher.pageStarted(id(THREE))
    val finished = mutableListOf<String>()

    x(THREE, finished)
    runCurrent()
    mainRuns()

    assertThat(returned).containsExactly(TWO)
    assertThat(registry.isClosedOnPurpose(keyOf(THREE))).isTrue()
    assertThat(listed().map { it.url }).containsExactly(ONE, TWO).inOrder()
    verify(exactly = 0) { notifications.dismissed(id(THREE)) }
    assertThat(finished).isEmpty()

    watcher.pageStopped()
    watcher.pageStarted(id(TWO))
    runCurrent()
    mainRuns()

    verify(exactly = 1) { notifications.dismissed(id(THREE)) }
    assertThat(finished).containsExactly(THREE)
  }

  @Test
  fun with_no_selection_of_a_returned_bubble_the_closed_one_goes_after_the_wait_for_the_shell() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE, TWO)
    watcher.pageStarted(id(THREE))
    val finished = mutableListOf<String>()

    x(THREE, finished)
    advanceTimeBy(699)
    verify(exactly = 0) { notifications.dismissed(id(THREE)) }

    advanceTimeBy(2)
    verify(exactly = 1) { notifications.dismissed(id(THREE)) }
    mainRuns()
    assertThat(finished).isEmpty()

    // The shell selects the bubble that is left, and only then does the page end.
    watcher.pageStopped()
    watcher.pageStarted(id(TWO))
    runCurrent()
    mainRuns()
    assertThat(finished).containsExactly(THREE)
  }

  @Test
  fun a_page_that_no_page_follows_still_finishes() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE, TWO)
    watcher.pageStarted(id(THREE))
    val finished = mutableListOf<String>()

    x(THREE, finished)
    advanceUntilIdle()
    mainRuns()

    assertThat(finished).containsExactly(THREE)
    verify(exactly = 1) { notifications.dismissed(id(THREE)) }
  }

  @Test
  fun a_second_tap_of_the_x_closes_nothing_more() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE, TWO)
    watcher.pageStarted(id(THREE))
    val finished = mutableListOf<String>()

    x(THREE, finished)
    runCurrent()
    x(THREE, finished)
    advanceUntilIdle()
    mainRuns()

    assertThat(returned).containsExactly(TWO)
    assertThat(finished).containsExactly(THREE)
    verify(exactly = 1) { notifications.dismissed(id(THREE)) }
  }

  @Test
  fun a_failed_return_of_a_last_close_closes_as_any_close_and_the_fold_tries_again() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE, TWO)
    returnAfterMs = null
    watcher.pageStarted(id(THREE))
    val finished = mutableListOf<String>()

    x(THREE, finished)
    advanceUntilIdle()
    mainRuns()

    assertThat(returned).containsExactly(TWO)
    verify(exactly = 1) { notifications.dismissed(id(THREE)) }
    assertThat(finished).containsExactly(THREE)
    assertThat(listed().map { it.url }).containsExactly(ONE, TWO).inOrder()

    returnAfterMs = 0
    watcher.pageStopped()
    afterTheFoldCheck()

    assertThat(returned).containsExactly(TWO, TWO).inOrder()
  }

  @Test
  fun held_closes_wait_for_the_fold_and_a_last_close_adds_none() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE, FOUR, FIVE), ONE, TWO, THREE)
    watcher.pageStarted(id(FIVE))
    val finished = mutableListOf<String>()

    closer.closePage(id(FIVE)) { finished += FIVE }
    watcher.pageStopped()
    watcher.pageStarted(id(FOUR))
    x(FOUR, finished)
    runCurrent()
    mainRuns()
    assertThat(returned).containsExactly(THREE)

    watcher.pageStopped()
    watcher.pageStarted(id(THREE))
    runCurrent()
    mainRuns()
    assertThat(finished).containsExactly(FIVE, FOUR).inOrder()
    assertThat(returned).containsExactly(THREE)

    watcher.pageStopped()
    afterTheFoldCheck()

    // One held close and one bubble back at the fold, though ONE is still in the overflow.
    assertThat(returned).containsExactly(THREE, TWO).inOrder()
  }

  @Test
  fun a_delete_intent_during_a_last_close_returns_no_second_bubble() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE, TWO)
    watcher.pageStarted(id(THREE))
    val finished = mutableListOf<String>()

    x(THREE, finished)
    runCurrent()
    closer.close(id(THREE))
    watcher.pageStopped()
    afterTheFoldCheck()
    mainRuns()

    assertThat(returned).containsExactly(TWO)
    assertThat(finished).containsExactly(THREE)
  }

  @Test
  fun the_x_of_the_last_bubble_with_an_empty_overflow_closes_at_once() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE), *emptyArray())
    watcher.pageStarted(id(ONE))
    val finished = mutableListOf<String>()

    x(ONE, finished)

    verify(exactly = 1) { notifications.dismissed(id(ONE)) }
    assertThat(finished).containsExactly(ONE)
  }

  @Test
  fun the_x_with_another_bubble_in_the_stack_closes_at_once() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE)
    watcher.pageStarted(id(THREE))
    val finished = mutableListOf<String>()

    x(THREE, finished)
    runCurrent()

    verify(exactly = 1) { notifications.dismissed(id(THREE)) }
    assertThat(finished).containsExactly(THREE)
    assertThat(returned).isEmpty()
  }

  @Test
  fun a_list_that_read_one_bubble_too_few_closes_as_any_close() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO, THREE), ONE, TWO)
    watcher.pageStarted(id(THREE))
    // Android holds a bubble in the stack that the list has not read yet.
    places = places!! + (id(TWO) to BubblePlace.STACK)
    val finished = mutableListOf<String>()

    x(THREE, finished)
    runCurrent()
    mainRuns()

    assertThat(returned).isEmpty()
    verify(exactly = 1) { notifications.dismissed(id(THREE)) }
    assertThat(finished).containsExactly(THREE)
  }

  @Test
  fun the_x_of_a_folded_stack_keeps_its_path() = posting { bubble ->
    fullStackWith(bubble, listOf(ONE, TWO), ONE)
    val finished = mutableListOf<String>()

    x(TWO, finished)
    advanceUntilIdle()

    assertThat(finished).containsExactly(TWO)
    verify(exactly = 1) { notifications.dismissed(id(TWO)) }
    assertThat(returned).containsExactly(ONE)
  }

  private companion object {
    const val URL = "https://example.com/bubble"
    /** How long a return waits for the delete intents of one drag. */
    const val SETTLE_MS = 500L
    const val ICON_AND_POST_MS = 400L
    const val ONE = "https://one.example/"
    const val TWO = "https://two.example/"
    const val THREE = "https://three.example/"
    const val FOUR = "https://four.example/"
    const val FIVE = "https://five.example/"
  }
}
