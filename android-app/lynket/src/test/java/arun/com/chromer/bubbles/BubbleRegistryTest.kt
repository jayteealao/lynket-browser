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

package arun.com.chromer.bubbles

import app.cash.turbine.test
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.bubbles.BubblePlace.OVERFLOW
import arun.com.chromer.bubbles.BubblePlace.STACK
import arun.com.chromer.bubbles.system.bubbleNotificationId
import arun.com.chromer.data.preferences.BrowsingMode.NativeBubbles
import arun.com.chromer.data.preferences.BrowsingMode.SlideOver
import arun.com.chromer.data.preferences.BrowsingMode.WebHeads
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.inject.Inject
import kotlin.concurrent.thread

/** The one list of open bubbles: its order, its identity and how it is read. */
class BubbleRegistryTest {

  private val first = "https://example.com/first"
  private val second = "https://example.com/second"
  private val third = "https://example.com/third"

  private val registry = BubbleRegistry()

  private fun urls(bubbles: List<OpenBubble>) = bubbles.map { it.url }

  private fun open(vararg links: String) =
    links.forEach { registry.add(NativeBubbles, it, incognito = false) }

  /** Android's answer: each named link at [place]. */
  private fun at(place: BubblePlace, vararg links: String) =
    links.associate { bubbleNotificationId(it) to place }

  private fun reconcileTo(places: Map<Int, BubblePlace>) = registry.reconcile(NativeBubbles) { places }

  /** Android holds the named links in its overflow and every other open bubble in the stack. */
  private fun inOverflow(vararg links: String) {
    val open = registry.list(NativeBubbles).map { it.url }
    reconcileTo(at(STACK, *open.toTypedArray()) + at(OVERFLOW, *links))
  }

  @Test
  fun three_links_read_in_open_order() {
    listOf(first, second, third).forEach { registry.add(NativeBubbles, it, incognito = false) }

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(first, second, third).inOrder()
  }

  @Test
  fun a_link_opened_twice_is_one_entry_and_keeps_its_place() {
    registry.add(NativeBubbles, first, incognito = false)
    registry.add(NativeBubbles, second, incognito = false)
    val again = registry.add(NativeBubbles, first, incognito = false)

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(first, second).inOrder()
    assertThat(again).isEqualTo(registry.find(NativeBubbles, first, incognito = false))
  }

  @Test
  fun a_link_removed_and_opened_again_goes_last() {
    val opened = registry.add(NativeBubbles, first, incognito = false)
    registry.add(NativeBubbles, second, incognito = false)

    registry.remove(opened.key)
    registry.add(NativeBubbles, first, incognito = false)

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(second, first).inOrder()
  }

  @Test
  fun a_normal_and_an_incognito_bubble_of_one_link_are_two_entries() {
    val normal = registry.add(NativeBubbles, first, incognito = false)
    val incognito = registry.add(NativeBubbles, first, incognito = true)

    assertThat(registry.list(NativeBubbles)).containsExactly(normal, incognito).inOrder()
    assertThat(normal.key).isNotEqualTo(incognito.key)
  }

  @Test
  fun find_returns_the_entry_of_that_mode_and_incognito_state() {
    val opened = registry.add(NativeBubbles, first, incognito = false)

    assertThat(registry.find(NativeBubbles, first, incognito = false)).isEqualTo(opened)
    assertThat(opened.mode).isEqualTo(NativeBubbles)
    assertThat(registry.find(NativeBubbles, second, incognito = false)).isNull()
    assertThat(registry.find(WebHeads, first, incognito = false)).isNull()
    assertThat(registry.find(NativeBubbles, first, incognito = true)).isNull()
    assertThat(registry.find(SlideOver, first, incognito = false)).isNull()
  }

  @Test
  fun a_read_of_a_mode_leaves_out_the_other_mode() {
    val native = registry.add(NativeBubbles, first, incognito = false)
    val head = registry.add(WebHeads, first, incognito = false)

    assertThat(registry.list(NativeBubbles)).containsExactly(native)
    assertThat(registry.list(WebHeads)).containsExactly(head)
    assertThat(registry.list(SlideOver)).isEmpty()
  }

  @Test
  fun a_key_refuses_slide_over() {
    assertThrows(IllegalArgumentException::class.java) { BubbleKey(SlideOver, 1) }
    assertThrows(IllegalArgumentException::class.java) {
      registry.add(SlideOver, first, incognito = false)
    }
  }

  @Test
  fun remove_of_an_unknown_key_changes_nothing() {
    registry.add(NativeBubbles, first, incognito = false)

    registry.remove(BubbleKey.of(NativeBubbles, second, incognito = false))
    registry.remove(BubbleKey.of(WebHeads, first, incognito = false))

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(first)
  }

  @Test
  fun bubbles_emits_on_a_change() = runTest {
    registry.bubbles.test {
      assertThat(awaitItem()).isEmpty()

      val opened = registry.add(NativeBubbles, first, incognito = false)
      assertThat(awaitItem()).containsExactly(opened)

      registry.remove(opened.key)
      assertThat(awaitItem()).isEmpty()
    }
  }

  @Test
  fun a_marked_entry_stays_listed_and_is_never_offered_for_a_return() {
    registry.add(NativeBubbles, first, incognito = false)
    val marked = registry.add(NativeBubbles, second, incognito = false)
    registry.add(NativeBubbles, third, incognito = false)

    inOverflow(first, second, third)

    registry.markClosedOnPurpose(marked.key)

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(first, second, third).inOrder()
    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(first, third).inOrder()
  }

  @Test
  fun the_entry_shows_its_marker() {
    val opened = registry.add(NativeBubbles, first, incognito = false)
    assertThat(opened.closedOnPurpose).isFalse()

    registry.markClosedOnPurpose(opened.key)

    assertThat(registry.find(NativeBubbles, first, incognito = false)?.closedOnPurpose).isTrue()
    assertThat(registry.isClosedOnPurpose(opened.key)).isTrue()
  }

  @Test
  fun a_marked_entry_stays_marked_after_it_is_removed() {
    val marked = registry.add(NativeBubbles, first, incognito = false)
    registry.add(NativeBubbles, second, incognito = false)
    inOverflow(first, second)

    registry.markClosedOnPurpose(marked.key)
    registry.remove(marked.key)

    assertThat(registry.isClosedOnPurpose(marked.key)).isTrue()
    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(second)
  }

  @Test
  fun remove_alone_never_marks() {
    val opened = registry.add(NativeBubbles, first, incognito = false)

    registry.remove(opened.key)

    assertThat(registry.isClosedOnPurpose(opened.key)).isFalse()
  }

  @Test
  fun marking_twice_or_marking_a_key_that_is_not_open_breaks_nothing() {
    val opened = registry.add(NativeBubbles, first, incognito = false)
    val notOpen = BubbleKey.of(NativeBubbles, second, incognito = false)

    registry.markClosedOnPurpose(opened.key)
    registry.markClosedOnPurpose(opened.key)
    registry.markClosedOnPurpose(notOpen)

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(first)
    assertThat(registry.isClosedOnPurpose(opened.key)).isTrue()
    assertThat(registry.isClosedOnPurpose(notOpen)).isTrue()
  }

  @Test
  fun opening_a_marked_link_that_is_still_open_clears_the_marker_and_keeps_its_place() {
    val marked = registry.add(NativeBubbles, first, incognito = false)
    registry.add(NativeBubbles, second, incognito = false)
    inOverflow(first, second)
    registry.markClosedOnPurpose(marked.key)

    val again = registry.add(NativeBubbles, first, incognito = false)

    assertThat(again.closedOnPurpose).isFalse()
    assertThat(registry.isClosedOnPurpose(marked.key)).isFalse()
    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(first, second).inOrder()
  }

  @Test
  fun opening_a_marked_link_after_it_was_removed_clears_the_marker() {
    val marked = registry.add(NativeBubbles, first, incognito = false)
    registry.markClosedOnPurpose(marked.key)
    registry.remove(marked.key)

    val again = registry.add(NativeBubbles, first, incognito = false)
    inOverflow(first)

    assertThat(again.closedOnPurpose).isFalse()
    assertThat(registry.isClosedOnPurpose(marked.key)).isFalse()
    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(first)
  }

  @Test
  fun a_return_read_leaves_out_the_other_mode() {
    registry.add(NativeBubbles, first, incognito = false)
    registry.add(WebHeads, second, incognito = false)
    inOverflow(first)

    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(first)
    assertThat(registry.returnCandidates(SlideOver)).isEmpty()
  }

  @Test
  fun a_bubble_in_the_stack_is_not_offered_for_a_return() {
    open(first, second)

    inOverflow(second)

    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(second)
  }

  @Test
  fun a_bubble_the_overflow_dropped_is_not_offered_for_a_return() {
    open(first, second)
    inOverflow(first, second)

    reconcileTo(at(OVERFLOW, second))

    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(second)
  }

  @Test
  fun a_link_removed_and_opened_again_is_not_offered_until_a_reconcile_says_so() {
    open(first)
    inOverflow(first)
    registry.remove(BubbleKey.of(NativeBubbles, first, false))

    open(first)
    assertThat(registry.find(NativeBubbles, first, false)?.place).isEqualTo(STACK)
    assertThat(registry.returnCandidates(NativeBubbles)).isEmpty()

    inOverflow(first)
    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(first)
  }

  @Test
  fun an_entry_prints_no_address_in_either_mode() {
    val incognito = registry.add(NativeBubbles, first, incognito = true)
    val normal = registry.add(NativeBubbles, second, incognito = false)

    assertThat(incognito.toString()).doesNotContain("example.com")
    assertThat(normal.toString()).doesNotContain("example.com")
  }

  @Test
  fun a_closed_incognito_bubble_answers_from_its_key_alone() {
    val opened = registry.add(NativeBubbles, first, incognito = true)

    registry.markClosedOnPurpose(opened.key)
    registry.remove(opened.key)

    val keyAlone = BubbleKey(NativeBubbles, opened.key.notificationId)
    assertThat(registry.isClosedOnPurpose(keyAlone)).isTrue()
    assertThat(registry.bubbles.value).isEmpty()
  }

  @Test
  fun eight_bubbles_with_three_in_the_overflow_read_as_eight_with_three_listed() {
    val links = (1..8).map { "https://example.com/$it" }
    open(*links.toTypedArray())

    reconcileTo(at(OVERFLOW, *links.take(3).toTypedArray()) + at(STACK, *links.drop(3).toTypedArray()))

    assertThat(urls(registry.list(NativeBubbles))).containsExactlyElementsIn(links).inOrder()
    assertThat(urls(registry.overflow(NativeBubbles))).containsExactlyElementsIn(links.take(3)).inOrder()
  }

  @Test
  fun a_bubble_android_no_longer_holds_leaves_unmarked_and_the_rest_keep_their_order() {
    open(first, second, third)

    reconcileTo(at(STACK, first, third))

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(first, third).inOrder()
    assertThat(registry.isClosedOnPurpose(BubbleKey.of(NativeBubbles, second, false))).isFalse()
  }

  @Test
  fun a_reconcile_returns_the_keys_that_left() {
    open(first, second, third)

    val left = reconcileTo(at(STACK, second))

    assertThat(left).containsExactly(
      BubbleKey.of(NativeBubbles, first, false),
      BubbleKey.of(NativeBubbles, third, false),
    ).inOrder()
  }

  @Test
  fun a_reconcile_that_takes_nothing_off_returns_an_empty_list() {
    open(first)

    assertThat(reconcileTo(at(STACK, first))).isEmpty()
  }

  @Test
  fun a_failed_read_changes_nothing_and_returns_null() {
    open(first, second)
    reconcileTo(at(STACK, first) + at(OVERFLOW, second))
    val before = registry.bubbles.value

    val left = registry.reconcile(NativeBubbles) { null }

    assertThat(left).isNull()
    assertThat(registry.bubbles.value).isEqualTo(before)
  }

  @Test
  fun a_notification_with_no_entry_adds_nothing() {
    open(first)

    reconcileTo(at(STACK, first, second) + (42 to OVERFLOW))

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(first)
  }

  @Test
  fun a_place_changes_both_ways_so_a_returned_bubble_reads_the_stack() {
    open(first)

    reconcileTo(at(OVERFLOW, first))
    assertThat(registry.find(NativeBubbles, first, false)?.place).isEqualTo(OVERFLOW)

    reconcileTo(at(STACK, first))
    assertThat(registry.find(NativeBubbles, first, false)?.place).isEqualTo(STACK)
    assertThat(registry.overflow(NativeBubbles)).isEmpty()
  }

  @Test
  fun a_new_bubble_reads_the_stack() {
    assertThat(registry.add(NativeBubbles, first, incognito = false).place).isEqualTo(STACK)
  }

  @Test
  fun a_web_head_with_the_same_id_is_left_alone() {
    val head = registry.add(WebHeads, first, incognito = false)
    open(first)

    reconcileTo(at(OVERFLOW, first))
    assertThat(registry.list(WebHeads)).containsExactly(head)

    reconcileTo(emptyMap())
    assertThat(registry.list(WebHeads)).containsExactly(head)
    assertThat(registry.list(NativeBubbles)).isEmpty()
  }

  @Test
  fun a_marked_entry_stays_marked_after_a_reconcile_takes_it_off() {
    val marked = registry.add(NativeBubbles, first, incognito = false)
    registry.markClosedOnPurpose(marked.key)

    reconcileTo(emptyMap())

    assertThat(registry.list(NativeBubbles)).isEmpty()
    assertThat(registry.isClosedOnPurpose(marked.key)).isTrue()
  }

  @Test
  fun a_marked_entry_that_stays_keeps_its_mark_and_takes_its_place() {
    val marked = registry.add(NativeBubbles, first, incognito = false)
    registry.markClosedOnPurpose(marked.key)

    reconcileTo(at(OVERFLOW, first))

    val entry = registry.find(NativeBubbles, first, false)
    assertThat(entry?.place).isEqualTo(OVERFLOW)
    assertThat(entry?.closedOnPurpose).isTrue()
  }

  @Test
  fun a_reconcile_that_changes_nothing_emits_nothing() = runTest {
    open(first)
    reconcileTo(at(OVERFLOW, first))

    registry.bubbles.test {
      awaitItem()

      reconcileTo(at(OVERFLOW, first))

      expectNoEvents()
    }
  }

  @Test
  fun a_link_opened_during_a_read_is_on_the_list_afterwards() {
    open(first)
    lateinit var opener: Thread

    registry.reconcile(NativeBubbles) {
      opener = thread { open(second) }
      // Long enough for the open to run if the read did not hold it back.
      opener.join(OPENER_HEAD_START_MS)
      at(STACK, first)
    }
    opener.join()

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(first, second).inOrder()
  }

  @Test
  fun a_dropped_link_sent_again_during_a_read_is_listed_once() {
    open(first, second)
    lateinit var opener: Thread

    registry.reconcile(NativeBubbles) {
      opener = thread { open(first) }
      opener.join(OPENER_HEAD_START_MS)
      at(STACK, second)
    }
    opener.join()

    assertThat(urls(registry.list(NativeBubbles))).containsExactly(second, first).inOrder()
  }

  @Test
  fun an_entry_prints_its_place_and_no_address() {
    open(first)
    reconcileTo(at(OVERFLOW, first))

    val printed = registry.find(NativeBubbles, first, false).toString()

    assertThat(printed).contains("place=OVERFLOW")
    assertThat(printed).doesNotContain("example.com")
  }

  @Test
  fun adds_from_several_threads_lose_nothing() = runTest {
    withContext(Dispatchers.Default) {
      repeat(200) { launch { registry.add(NativeBubbles, "https://example.com/$it", false) } }
    }

    assertThat(registry.list(NativeBubbles)).hasSize(200)
  }

  private companion object {
    const val OPENER_HEAD_START_MS = 200L
  }
}

/** Every part of the app must read the same list, so the app holds one registry. */
@HiltAndroidTest
class BubbleRegistryInjectionTest : LynketRobolectricSuite() {

  @Inject
  lateinit var first: BubbleRegistry

  @Inject
  lateinit var second: BubbleRegistry

  @Test
  fun the_app_holds_one_registry() {
    assertThat(first).isSameInstanceAs(second)
  }
}
