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

package arun.com.chromer.bubbles.webheads

import android.app.Application
import androidx.compose.runtime.MonotonicFrameClock
import arun.com.chromer.bubbles.webheads.motion.WebHeadHost
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadState
import arun.com.chromer.bubbles.webheads.motion.WebHeadsController
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.roundToInt

/** A frame every 16 ms of virtual time. */
private class TestFrameClock : MonotonicFrameClock {
  private var frameNanos = 0L

  override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
    delay(16)
    frameNanos += 16_000_000L
    return onFrame(frameNanos)
  }
}

private class RecordingHost : WebHeadHost {
  val windows = LinkedHashMap<String, Pair<Int, Int>>()
  val hidden = HashSet<String>()
  val clicked = ArrayList<String>()
  val destroyed = ArrayList<Pair<String, Boolean>>()
  var longPresses = 0

  override fun addHeadWindow(head: WebHeadState, x: Int, y: Int) {
    windows[head.url] = x to y
  }

  override fun moveHeadWindow(head: WebHeadState, x: Int, y: Int) {
    windows[head.url] = x to y
  }

  override fun setHeadWindowVisible(head: WebHeadState, visible: Boolean) {
    if (visible) hidden.remove(head.url) else hidden.add(head.url)
  }

  override fun removeHeadWindow(head: WebHeadState) {
    windows.remove(head.url)
  }

  override fun onHeadClick(head: WebHeadState) {
    clicked.add(head.url)
  }

  override fun onHeadsLongPress() {
    longPresses++
  }

  override fun onHeadDestroyed(head: WebHeadState, isLastHead: Boolean) {
    destroyed.add(head.url to isLastHead)
  }
}

/** Drag, fling, trash, chain, queue and coasting of the web heads, on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WebHeadsControllerTest {
  private val width = 1080
  private val height = 2400
  private val density = 2.625f
  private val headPx = 178
  private val trashPx = 241
  private val host = RecordingHost()
  private var closeOnOpen = false

  private fun TestScope.controller() = WebHeadsController(
    host = host,
    scope = backgroundScope + TestFrameClock(),
    dispWidth = width,
    dispHeight = height,
    density = density,
    screenWidthDp = 411,
    headWindowPx = headPx,
    trashWindowPx = trashPx,
    touchSlop = 21f,
    minFlingVelocity = 131f,
    spawnOnRight = true,
    closeOnOpen = { closeOnOpen },
  )

  private fun TestScope.settle(ms: Long = 3_000) {
    advanceTimeBy(ms)
    runCurrent()
  }

  @Test
  fun first_head_spawns_on_the_right_edge_a_third_down() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    assertThat(host.windows["https://a.example"]).isEqualTo(c.bounds.right to height / 3)
    assertThat(head.isMaster).isTrue()
    assertThat(head.badgeCount).isEqualTo(1)
    assertThat(head.revealed).isTrue()
  }

  @Test
  fun newest_head_is_the_master_and_every_badge_counts_all() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    val b = c.add("https://b.example")
    val m = c.add("https://c.example")
    assertThat(a.isMaster).isFalse()
    assertThat(b.isMaster).isFalse()
    assertThat(m.isMaster).isTrue()
    assertThat(c.all.map { it.badgeCount }).containsExactly(3, 3, 3)
  }

  @Test
  fun heads_beyond_five_are_queued_and_hidden() = runTest {
    val c = controller()
    (1..7).forEach { c.add("https://$it.example") }
    assertThat(host.hidden).containsExactly("https://1.example", "https://2.example")
    assertThat(c["https://1.example"]!!.queued).isTrue()
    assertThat(c["https://3.example"]!!.queued).isFalse()
  }

  @Test
  fun followers_trail_the_master_by_the_chain_offset() = runTest {
    val c = controller()
    c.add("https://a.example")
    c.add("https://b.example")
    settle()
    val master = host.windows.getValue("https://b.example")
    val follower = host.windows.getValue("https://a.example")
    val (dx, dy) = WebHeadMotion.chainOffset(1, masterOnRight = master.first > width / 2, density = density)
    assertThat(follower).isEqualTo(master.first + dx to master.second + dy)
  }

  @Test
  fun dragging_moves_the_head_with_the_finger_and_shows_the_trash() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    val (x0, y0) = host.windows.getValue(head.url)
    c.onTouchDown(head, 1000f, 800f)
    c.onTouchMove(head, 700f, 900f)
    runCurrent()
    assertThat(host.windows[head.url]).isEqualTo(x0 - 300 to y0 + 100)
    assertThat(c.trash.scale).isEqualTo(0.9f)
    assertThat(c.trash.locked).isFalse()
  }

  @Test
  fun a_move_inside_the_touch_slop_does_not_drag() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    val start = host.windows.getValue(head.url)
    c.onTouchDown(head, 1000f, 800f)
    c.onTouchMove(head, 1010f, 805f)
    runCurrent()
    assertThat(host.windows[head.url]).isEqualTo(start)
    assertThat(c.trash.scale).isEqualTo(0f)
  }

  @Test
  fun a_release_mid_screen_snaps_to_the_nearer_edge() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    c.onTouchDown(head, 1000f, 800f)
    c.onTouchMove(head, 400f, 900f)
    runCurrent()
    c.onTouchUp(head, 0f, 0f, 400f, 900f)
    settle()
    assertThat(host.windows.getValue(head.url).first).isEqualTo(c.bounds.left)
    assertThat(c.trash.scale).isEqualTo(0f)
  }

  @Test
  fun a_fling_ends_at_an_edge() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    c.onTouchDown(head, 1000f, 800f)
    c.onTouchMove(head, 900f, 800f)
    c.onTouchMove(head, 800f, 800f)
    runCurrent()
    c.onTouchUp(head, -6000f, 0f, 800f, 800f)
    settle()
    val x = host.windows.getValue(head.url).first
    assertThat(x).isAnyOf(c.bounds.left, c.bounds.right)
  }

  @Test
  fun near_the_trash_the_head_locks_and_a_release_closes_it() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    val trashCentreX = c.trashX + trashPx / 2f
    val trashCentreY = c.trashY + trashPx / 2f
    val (x0, y0) = host.windows.getValue(head.url)
    c.onTouchDown(head, x0 + headPx / 2f, y0 + headPx / 2f)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    settle(1_000)
    assertThat(head.lockedToTrash).isTrue()
    assertThat(c.trash.locked).isTrue()
    assertThat(c.trash.scale).isEqualTo(1f)
    c.onTouchUp(head, 0f, 0f, trashCentreX, trashCentreY)
    runCurrent()
    assertThat(head.closing).isTrue()
    settle(WebHeadMotion.TRASH_CLOSE_MS + 50)
    assertThat(host.windows).doesNotContainKey(head.url)
    assertThat(host.destroyed).containsExactly(head.url to true)
  }

  @Test
  fun closing_the_master_makes_the_next_head_master() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    c.add("https://b.example")
    c.close("https://b.example")
    runCurrent()
    assertThat(a.isMaster).isTrue()
    assertThat(a.badgeCount).isEqualTo(1)
    assertThat(host.destroyed).containsExactly("https://b.example" to false)
  }

  @Test
  fun a_tap_opens_at_once_without_close_on_open() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    c.onTap(head)
    assertThat(host.clicked).containsExactly(head.url)
    assertThat(head.shrinking).isFalse()
  }

  @Test
  fun a_tap_with_close_on_open_shrinks_first() = runTest {
    closeOnOpen = true
    val c = controller()
    val head = c.add("https://a.example")
    c.onTap(head)
    assertThat(head.shrinking).isTrue()
    assertThat(host.clicked).isEmpty()
    settle(WebHeadMotion.CLOSE_ON_OPEN_MS + 1)
    assertThat(host.clicked).containsExactly(head.url)
  }

  @Test
  fun a_long_press_asks_for_the_list() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    c.onLongPress(head)
    assertThat(host.longPresses).isEqualTo(1)
  }

  @Test
  fun followers_ignore_touches() = runTest {
    val c = controller()
    val follower = c.add("https://a.example")
    c.add("https://b.example")
    c.onTap(follower)
    c.onLongPress(follower)
    assertThat(host.clicked).isEmpty()
    assertThat(host.longPresses).isEqualTo(0)
  }

  @Test
  fun an_idle_master_coasts_further_off_screen() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    settle(WebHeadMotion.COASTING_DELAY_MS + 3_000)
    assertThat(host.windows.getValue(head.url).first).isEqualTo(c.bounds.right + headPx / 4)
  }

  @Test
  fun remove_all_drops_every_window_without_callbacks() = runTest {
    val c = controller()
    c.add("https://a.example")
    c.add("https://b.example")
    c.removeAll()
    assertThat(host.windows).isEmpty()
    assertThat(host.destroyed).isEmpty()
    assertThat(c.isEmpty).isTrue()
  }

  /** The y a head rests at, at [ratio] of the height range the controller's bounds allow. */
  private fun WebHeadsController.yAt(ratio: Float): Int = bounds.top + (ratio * (bounds.bottom - bounds.top)).roundToInt()

  /** Drags [head] so its window sits at ([x], [y]) and releases it there, then lets it rest. */
  private fun TestScope.dragAndRelease(c: WebHeadsController, head: WebHeadState, x: Int, y: Int) {
    val (x0, y0) = host.windows.getValue(head.url)
    val downX = 1000f
    val downY = 800f
    val upX = downX + (x - x0)
    val upY = downY + (y - y0)
    c.onTouchDown(head, downX, downY)
    c.onTouchMove(head, upX, upY)
    runCurrent()
    c.onTouchUp(head, 0f, 0f, upX, upY)
    settle()
  }

  /** Leaves [head] resting on the left edge at [ratio] of the height range. */
  private fun TestScope.restOnLeftAt(c: WebHeadsController, head: WebHeadState, ratio: Float) {
    dragAndRelease(c, head, 100, c.yAt(ratio))
    assertThat(host.windows.getValue(head.url)).isEqualTo(c.bounds.left to c.yAt(ratio))
  }

  /** Turns the test phone to landscape. */
  private fun rotate(c: WebHeadsController) = c.updateDisplay(2400, 1080, 914)

  private fun assertNear(actual: Pair<Int, Int>?, expected: Pair<Int, Int>) {
    assertThat(actual).isNotNull()
    assertThat(actual!!.first).isEqualTo(expected.first)
    assertThat(abs(actual.second - expected.second)).isAtMost(1)
  }

  @Test
  fun a_new_head_after_a_rotation_spawns_on_the_stack_side_at_its_height_ratio() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    restOnLeftAt(c, a, 0.7f)
    rotate(c)
    c.add("https://b.example")
    val b = host.windows["https://b.example"]
    val expectedY = c.yAt(0.7f)
    assertNear(b, c.bounds.left to expectedY)
    assertThat(b!!.second).isIn(Range.closed(c.bounds.top, c.bounds.bottom))
    assertThat(b).isNotEqualTo(c.bounds.right to 1080 / 3)
  }

  @Test
  fun frames_after_a_rotation_do_not_move_the_stack() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    restOnLeftAt(c, a, 0.7f)
    rotate(c)
    settle(WebHeadMotion.COASTING_DELAY_MS + 3_000)
    c.add("https://b.example")
    assertNear(host.windows["https://b.example"], c.bounds.left to c.yAt(0.7f))
  }

  @Test
  fun a_stack_low_on_the_left_stays_inside_the_landscape_bounds() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    restOnLeftAt(c, a, 0.9f)
    rotate(c)
    c.add("https://b.example")
    val b = host.windows.getValue("https://b.example")
    assertThat(b.first).isEqualTo(c.bounds.left)
    assertThat(b.second).isIn(Range.closed(c.bounds.top, c.bounds.bottom))
  }

  @Test
  fun a_stack_on_the_right_keeps_its_side_after_a_rotation() = runTest {
    val c = controller()
    c.add("https://a.example")
    settle(1_000)
    val portraitRatio = (height / 3 - c.bounds.top).toFloat() / (c.bounds.bottom - c.bounds.top)
    rotate(c)
    c.add("https://b.example")
    assertNear(host.windows["https://b.example"], c.bounds.right to c.yAt(portraitRatio))
  }

  @Test
  fun a_rotation_there_and_back_keeps_the_stack() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    restOnLeftAt(c, a, 0.7f)
    rotate(c)
    c.updateDisplay(width, height, 411)
    c.add("https://b.example")
    assertNear(host.windows["https://b.example"], c.bounds.left to c.yAt(0.7f))
  }

  @Test
  fun remove_all_forgets_the_stack() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    restOnLeftAt(c, a, 0.7f)
    c.removeAll()
    c.add("https://b.example")
    assertThat(host.windows["https://b.example"]).isEqualTo(c.bounds.right to height / 3)
  }

  @Test
  fun a_touch_after_a_rotation_moves_the_stack_again() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    restOnLeftAt(c, a, 0.7f)
    rotate(c)
    dragAndRelease(c, a, 2100, c.yAt(0.3f))
    assertThat(host.windows.getValue(a.url)).isEqualTo(c.bounds.right to c.yAt(0.3f))
    c.add("https://b.example")
    assertNear(host.windows["https://b.example"], c.bounds.right to c.yAt(0.3f))
  }
}
