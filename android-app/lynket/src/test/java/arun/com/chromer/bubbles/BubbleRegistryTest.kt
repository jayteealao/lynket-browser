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
import arun.com.chromer.data.preferences.BrowsingMode.NativeBubbles
import arun.com.chromer.data.preferences.BrowsingMode.SlideOver
import arun.com.chromer.data.preferences.BrowsingMode.WebHeads
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/** The one list of open bubbles: its order, its identity and how it is read. */
class BubbleRegistryTest {

  private val first = "https://example.com/first"
  private val second = "https://example.com/second"
  private val third = "https://example.com/third"

  private val registry = BubbleRegistry()

  private fun urls(bubbles: List<OpenBubble>) = bubbles.map { it.url }

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

    assertThat(again.closedOnPurpose).isFalse()
    assertThat(registry.isClosedOnPurpose(marked.key)).isFalse()
    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(first)
  }

  @Test
  fun a_return_read_leaves_out_the_other_mode() {
    registry.add(NativeBubbles, first, incognito = false)
    registry.add(WebHeads, second, incognito = false)

    assertThat(urls(registry.returnCandidates(NativeBubbles))).containsExactly(first)
    assertThat(registry.returnCandidates(SlideOver)).isEmpty()
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
}
