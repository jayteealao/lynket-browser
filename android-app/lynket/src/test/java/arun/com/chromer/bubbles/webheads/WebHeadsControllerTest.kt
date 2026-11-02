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
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import arun.com.chromer.bubbles.webheads.motion.TrashTick
import arun.com.chromer.bubbles.webheads.motion.WebHeadHost
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion.StackAnchor
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion.StackAnchor.Side
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
import kotlin.coroutines.EmptyCoroutineContext
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

  /** Every window move of each head, in order. */
  val moves = HashMap<String, MutableList<Pair<Int, Int>>>()
  val hidden = HashSet<String>()
  val clicked = ArrayList<String>()
  val destroyed = ArrayList<Pair<String, Boolean>>()
  var longPresses = 0

  /** Every time the trash showed (true) or went away (false), in order. */
  val trashShown = mutableListOf<Boolean>()

  override fun addHeadWindow(head: WebHeadState, x: Int, y: Int) {
    windows[head.url] = x to y
  }

  override fun moveHeadWindow(head: WebHeadState, x: Int, y: Int) {
    windows[head.url] = x to y
    moves.getOrPut(head.url) { ArrayList() }.add(x to y)
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

  override fun onTrashShown(shown: Boolean) {
    trashShown.add(shown)
  }

  /** Every haptic tick, as the head's url to the feedback constant, in order. */
  val haptics = mutableListOf<Pair<String, Int>>()

  override fun onTrashTick(head: WebHeadState, tick: TrashTick) {
    haptics.add(head.url to tick.feedbackConstant())
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

  /** [motionScale] is the system's animator duration scale; 0 is "Remove animations". */
  private fun TestScope.controller(motionScale: Float? = null) = WebHeadsController(
    host = host,
    scope = backgroundScope + TestFrameClock() +
      (motionScale?.let { scale -> object : MotionDurationScale { override val scaleFactor = scale } } ?: EmptyCoroutineContext),
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
  /** Leaves [head] resting on the right edge at [ratio] of the height range. */
  private fun TestScope.restOnRightAt(c: WebHeadsController, head: WebHeadState, ratio: Float) {
    dragAndRelease(c, head, 900, c.yAt(ratio))
    assertThat(host.windows.getValue(head.url)).isEqualTo(c.bounds.right to c.yAt(ratio))
  }

  private fun threeHeads(c: WebHeadsController): WebHeadState {
    c.add("https://a.example")
    c.add("https://b.example")
    return c.add("https://c.example")
  }

  /**
   * The master rests on the [side] edge at [ratio] of the height range of the current bounds, each
   * follower trails it by its chain offset, and every visible head is on that half of the screen
   * with its top inside the rest range (plus the chain offsets).
   */
  private fun assertStackAt(c: WebHeadsController, side: Side, ratio: Float) {
    val master = c.master!!
    val (mx, my) = StackAnchor(side, ratio).position(c.bounds)
    assertNear(host.windows[master.url], mx to my)
    val m = host.windows.getValue(master.url)
    val visible = c.all.filter { !it.queued }
    visible.dropLast(1).asReversed().forEachIndexed { i, follower ->
      val (dx, dy) = WebHeadMotion.chainOffset(i + 1, side == Side.RIGHT, density)
      assertThat(host.windows[follower.url]).isEqualTo(m.first + dx to m.second + dy)
    }
    visible.forEach { head ->
      val (x, y) = host.windows.getValue(head.url)
      val centre = x + headPx / 2
      if (side == Side.RIGHT) {
        assertThat(centre).isGreaterThan(c.dispWidth / 2)
      } else {
        assertThat(centre).isLessThan(c.dispWidth / 2)
      }
      assertThat(y).isIn(Range.closed(c.bounds.top, c.bounds.bottom + 5 * 4))
    }
  }

  @Test
  fun open_heads_move_to_the_stack_edge_after_a_rotation() = runTest {
    val c = controller()
    val master = threeHeads(c)
    restOnRightAt(c, master, 0.5f)
    val before = c.anchor!!
    rotate(c)
    settle()
    assertStackAt(c, Side.RIGHT, 0.5f)
    assertThat(c.anchor!!.side).isEqualTo(Side.RIGHT)
    assertThat(c.anchor!!.ratio).isWithin(0.001f).of(before.ratio)
  }

  @Test
  fun six_heads_low_on_the_left_stay_inside_after_a_rotation() = runTest {
    val c = controller()
    (1..6).forEach { c.add("https://$it.example") }
    val master = c.master!!
    restOnLeftAt(c, master, 0.9f)
    rotate(c)
    settle()
    assertStackAt(c, Side.LEFT, 0.9f)
    assertThat(c["https://1.example"]!!.queued).isTrue()
    assertThat(host.windows["https://1.example"]).isEqualTo(host.windows[master.url])
  }

  @Test
  fun the_resnap_does_not_bounce() = runTest {
    val c = controller()
    val master = threeHeads(c)
    restOnRightAt(c, master, 0.5f)
    val from = host.moves[master.url]?.size ?: 0
    rotate(c)
    val (tx, ty) = StackAnchor(Side.RIGHT, c.anchor!!.ratio).position(c.bounds)
    settle()
    val after = host.moves[master.url].orEmpty().drop(from)
    assertThat(after).isNotEmpty()
    after.zipWithNext().forEach { (a, b) ->
      assertThat(b.first).isAtLeast(a.first)
      assertThat(b.second).isAtMost(a.second)
    }
    after.forEach { (x, y) ->
      assertThat(x).isAtMost(tx)
      assertThat(y).isAtLeast(ty)
    }
    assertThat(after.last()).isEqualTo(tx to ty)
  }

  @Test
  fun a_head_under_the_finger_stays_there_during_a_rotation() = runTest {
    val c = controller()
    val master = threeHeads(c)
    c.onTouchDown(master, 1000f, 800f)
    val initial = host.windows.getValue(master.url)
    c.onTouchMove(master, 700f, 900f)
    runCurrent()
    val held = host.windows.getValue(master.url)
    rotate(c)
    settle(1_000)
    assertThat(host.windows[master.url]).isEqualTo(held)
    c.onTouchMove(master, 800f, 600f)
    assertThat(host.windows[master.url]).isEqualTo(initial.first - 200 to initial.second - 200)
    val release = StackAnchor.of(initial.first - 200, initial.second - 200, c.bounds, c.dispWidth)
    c.onTouchUp(master, 0f, 0f, 800f, 600f)
    settle()
    assertStackAt(c, release.side, release.ratio)
  }

  @Test
  fun a_tap_during_a_rotation_resnaps_after_the_release() = runTest {
    val c = controller()
    val master = threeHeads(c)
    restOnRightAt(c, master, 0.5f)
    val rest = host.windows.getValue(master.url)
    c.onTouchDown(master, 1000f, 800f)
    rotate(c)
    settle(1_000)
    assertThat(host.windows[master.url]).isEqualTo(rest)
    c.onTouchUp(master, 0f, 0f, 1000f, 800f)
    settle()
    assertStackAt(c, Side.RIGHT, 0.5f)
  }

  @Test
  fun a_new_head_under_a_held_finger_does_not_stall_the_next_rotation() = runTest {
    val c = controller()
    val held = threeHeads(c)
    c.onTouchDown(held, 1000f, 800f)
    // A link opens while the finger is down: the held head is a follower now, so its release is dropped.
    c.add("https://d.example")
    c.onTouchUp(held, 0f, 0f, 1000f, 800f)
    settle()
    val before = c.anchor!!
    rotate(c)
    settle()
    assertStackAt(c, before.side, before.ratio)
  }

  @Test
  fun closing_the_held_master_does_not_stall_the_next_rotation() = runTest {
    val c = controller()
    threeHeads(c)
    val held = c.master!!
    c.onTouchDown(held, 1000f, 800f)
    c.close(held.url)
    settle()
    val before = c.anchor!!
    rotate(c)
    settle()
    assertStackAt(c, before.side, before.ratio)
  }

  @Test
  fun a_drag_interrupts_the_resnap() = runTest {
    val c = controller()
    val master = threeHeads(c)
    restOnRightAt(c, master, 0.5f)
    rotate(c)
    advanceTimeBy(48)
    runCurrent()
    c.onTouchDown(master, 1000f, 800f)
    val atTouch = host.windows.getValue(master.url)
    c.onTouchMove(master, 700f, 900f)
    runCurrent()
    settle(1_000)
    assertThat(host.windows[master.url]).isEqualTo(atTouch.first - 300 to atTouch.second + 100)
  }

  @Test
  fun with_animations_off_heads_jump_after_a_rotation() = runTest {
    val c = controller(motionScale = 0f)
    val master = threeHeads(c)
    restOnRightAt(c, master, 0.5f)
    val (x0, y0) = host.windows.getValue(master.url)
    host.moves.clear()
    rotate(c)
    advanceTimeBy(16)
    runCurrent()
    assertStackAt(c, Side.RIGHT, 0.5f)
    val (tx, ty) = host.windows.getValue(master.url)
    // Each axis jumps from where it was to its end, with no position in between.
    host.moves.getValue(master.url).forEach { (x, y) ->
      assertThat(x).isAnyOf(x0, tx)
      assertThat(y).isAnyOf(y0, ty)
    }
  }

  @Test
  fun a_configuration_change_with_the_same_size_leaves_the_heads() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    settle(WebHeadMotion.COASTING_DELAY_MS + 3_000)
    val coasted = host.windows.getValue(head.url)
    assertThat(coasted.first).isEqualTo(c.bounds.right + headPx / 4)
    c.updateDisplay(width, height, 411)
    settle()
    assertThat(host.windows[head.url]).isEqualTo(coasted)
  }

  @Test
  fun a_resize_without_a_turn_resnaps_too() = runTest {
    val c = controller()
    val master = threeHeads(c)
    restOnRightAt(c, master, 0.5f)
    c.updateDisplay(1840, 2208, 701)
    settle()
    assertStackAt(c, Side.RIGHT, 0.5f)
  }

  /**
   * A controller on a window [widthDp] wide at density 2 (a 56 dp head in a 68 dp window), with
   * its own [wideHost], so one test can try several widths.
   */
  private fun TestScope.wideController(widthDp: Int, wideHost: RecordingHost) = WebHeadsController(
    host = wideHost,
    scope = backgroundScope + TestFrameClock(),
    dispWidth = widthDp * 2,
    dispHeight = 1600,
    density = 2f,
    screenWidthDp = widthDp,
    headWindowPx = 136,
    trashWindowPx = 184,
    touchSlop = 16f,
    minFlingVelocity = 100f,
    spawnOnRight = true,
    closeOnOpen = { false },
  )

  /**
   * Drags [head] from its window's centre to [releaseShare] of the window width in 100 px steps, the
   * last step pointing the way of the fling, then lets go at [dpPerSecond] towards the right or the
   * left; the release velocity is [dpPerSecond] times [density], in px per second.
   */
  private fun TestScope.flick(
    c: WebHeadsController,
    on: RecordingHost,
    head: WebHeadState,
    releaseShare: Float,
    towardsRight: Boolean,
    dpPerSecond: Float,
    density: Float = 2f,
    clearMovesBeforeRelease: Boolean = false,
  ) {
    val (x0, y0) = on.windows.getValue(head.url)
    val downX = x0 + c.headWindowPx / 2f
    val y = y0 + c.headWindowPx / 2f
    val releaseX = releaseShare * c.dispWidth
    val dir = if (towardsRight) 1f else -1f
    val approachX = releaseX - dir * 100f
    c.onTouchDown(head, downX, y)
    var x = downX
    val step = if (approachX > x) 100f else -100f
    while (abs(approachX - x) > 100f) {
      x += step
      c.onTouchMove(head, x, y)
    }
    c.onTouchMove(head, approachX, y)
    c.onTouchMove(head, releaseX, y)
    runCurrent()
    if (clearMovesBeforeRelease) on.moves.clear()
    c.onTouchUp(head, dir * dpPerSecond * density, 0f, releaseX, y)
  }

  @Test
  fun a_normal_flick_snaps_to_the_edge_it_points_to_on_every_width() = runTest {
    listOf(360, 768, 1_280).forEach { widthDp ->
      val toRight = RecordingHost()
      val r = wideController(widthDp, toRight)
      val a = r.add("https://a.example")
      flick(r, toRight, a, 0.45f, towardsRight = true, dpPerSecond = PHONE_FLICK_DP_S)
      settle()
      assertThat(toRight.windows.getValue(a.url).first).isEqualTo(r.bounds.right)

      val toLeft = RecordingHost()
      val l = wideController(widthDp, toLeft)
      val b = l.add("https://b.example")
      flick(l, toLeft, b, 0.55f, towardsRight = false, dpPerSecond = PHONE_FLICK_DP_S)
      settle()
      assertThat(toLeft.windows.getValue(b.url).first).isEqualTo(l.bounds.left)
    }
  }

  @Test
  fun a_gentle_flick_on_a_wide_window_still_reaches_its_edge() = runTest {
    val toRight = RecordingHost()
    val r = wideController(1_280, toRight)
    val a = r.add("https://a.example")
    flick(r, toRight, a, 0.45f, towardsRight = true, dpPerSecond = 300f)
    settle()
    assertThat(toRight.windows.getValue(a.url).first).isEqualTo(r.bounds.right)

    val toLeft = RecordingHost()
    val l = wideController(1_280, toLeft)
    val b = l.add("https://b.example")
    flick(l, toLeft, b, 0.55f, towardsRight = false, dpPerSecond = 300f)
    settle()
    assertThat(toLeft.windows.getValue(b.url).first).isEqualTo(l.bounds.left)
  }

  @Test
  fun a_wide_window_fling_does_not_bounce_past_the_edge() = runTest {
    val toRight = RecordingHost()
    val r = wideController(1_280, toRight)
    val a = r.add("https://a.example")
    flick(r, toRight, a, 0.5f, towardsRight = true, dpPerSecond = PHONE_FLICK_DP_S, clearMovesBeforeRelease = true)
    settle()
    val right = toRight.moves.getValue(a.url).map { it.first }
    assertThat(right).isNotEmpty()
    right.forEach { assertThat(it).isAtMost(r.bounds.right) }
    assertThat(right.last()).isEqualTo(r.bounds.right)

    val toLeft = RecordingHost()
    val l = wideController(1_280, toLeft)
    val b = l.add("https://b.example")
    flick(l, toLeft, b, 0.5f, towardsRight = false, dpPerSecond = PHONE_FLICK_DP_S, clearMovesBeforeRelease = true)
    settle()
    val left = toLeft.moves.getValue(b.url).map { it.first }
    assertThat(left).isNotEmpty()
    left.forEach { assertThat(it).isAtLeast(l.bounds.left) }
    assertThat(left.last()).isEqualTo(l.bounds.left)
  }

  @Test
  fun a_slow_release_on_a_wide_window_snaps_to_the_nearer_edge() = runTest {
    // Let go with no speed left of the middle, then just under the fling speed towards the right.
    listOf(0f, 40f).forEach { dpPerSecond ->
      val on = RecordingHost()
      val c = wideController(1_280, on)
      val head = c.add("https://a.example")
      flick(c, on, head, 0.45f, towardsRight = true, dpPerSecond = dpPerSecond)
      settle()
      assertThat(on.windows.getValue(head.url).first).isEqualTo(c.bounds.left)
    }
    val on = RecordingHost()
    val c = wideController(1_280, on)
    val head = c.add("https://a.example")
    flick(c, on, head, 0.55f, towardsRight = true, dpPerSecond = 0f)
    settle()
    assertThat(on.windows.getValue(head.url).first).isEqualTo(c.bounds.right)
  }

  @Test
  fun the_cap_follows_a_resize_to_a_wide_window() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    settle()
    // 1 280 dp at the phone's density of 2.625.
    c.updateDisplay(3360, 2100, 1_280)
    settle()
    flick(c, host, head, 0.5f, towardsRight = true, dpPerSecond = PHONE_FLICK_DP_S, density = density, clearMovesBeforeRelease = true)
    settle()
    val xs = host.moves.getValue(head.url).map { it.first }
    assertThat(xs).isNotEmpty()
    xs.forEach { assertThat(it).isAtMost(c.bounds.right) }
    assertThat(xs.last()).isEqualTo(c.bounds.right)
  }

  @Test
  fun every_head_knows_its_place_in_the_stack_front_first() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    val b = c.add("https://b.example")
    val m = c.add("https://m.example")
    assertThat(m.stackPosition).isEqualTo(1)
    assertThat(b.stackPosition).isEqualTo(2)
    assertThat(a.stackPosition).isEqualTo(3)
    assertThat(listOf(a, b, m).map { it.badgeCount }).containsExactly(3, 3, 3)
  }

  @Test
  fun closing_a_head_renumbers_the_stack() = runTest {
    val c = controller()
    val a = c.add("https://a.example")
    val b = c.add("https://b.example")
    val cc = c.add("https://c.example")
    c.add("https://d.example")
    c.close("https://d.example")
    runCurrent()
    assertThat(listOf(cc, b, a).map { it.stackPosition }).containsExactly(1, 2, 3).inOrder()
    assertThat(listOf(cc, b, a).map { it.badgeCount }).containsExactly(3, 3, 3)
    c.close("https://b.example")
    runCurrent()
    assertThat(listOf(cc, a).map { it.stackPosition }).containsExactly(1, 2).inOrder()
    assertThat(listOf(cc, a).map { it.badgeCount }).containsExactly(2, 2)
  }

  @Test
  fun a_lone_head_is_one_of_one() = runTest {
    val c = controller()
    val only = c.add("https://a.example")
    assertThat(only.stackPosition).isEqualTo(1)
    assertThat(only.badgeCount).isEqualTo(1)
    c.add("https://b.example")
    c.removeAll()
    val fresh = c.add("https://c.example")
    assertThat(fresh.stackPosition).isEqualTo(1)
    assertThat(fresh.badgeCount).isEqualTo(1)
  }

  /** Holds [head] down at its centre, as a finger does before a drag. */
  private fun WebHeadsController.touchDownOn(head: WebHeadState) {
    val (x0, y0) = host.windows.getValue(head.url)
    onTouchDown(head, x0 + headPx / 2f, y0 + headPx / 2f)
  }

  @Test
  fun the_trash_locks_once_while_a_head_stays_on_it() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    val trashCentreX = c.trashX + trashPx / 2f
    val trashCentreY = c.trashY + trashPx / 2f
    c.touchDownOn(head)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    repeat(8) { i -> c.onTouchMove(head, trashCentreX + (i % 4) * 5f, trashCentreY - (i % 3) * 5f) }
    runCurrent()
    assertThat(c.trash.locked).isTrue()
    assertThat(c.trash.lockCount).isEqualTo(1)
  }

  @Test
  fun leaving_and_returning_locks_the_trash_again() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    val trashCentreX = c.trashX + trashPx / 2f
    val trashCentreY = c.trashY + trashPx / 2f
    c.touchDownOn(head)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    runCurrent()
    assertThat(c.trash.lockCount).isEqualTo(1)
    c.onTouchMove(head, width / 2f, height / 2f)
    runCurrent()
    assertThat(c.trash.locked).isFalse()
    assertThat(c.trash.lockCount).isEqualTo(1)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    runCurrent()
    assertThat(c.trash.locked).isTrue()
    assertThat(c.trash.lockCount).isEqualTo(2)
    c.onTouchMove(head, width / 2f, height / 2f)
    c.onTouchUp(head, 0f, 0f, width / 2f, height / 2f)
    runCurrent()
    assertThat(c.trash.locked).isFalse()
    assertThat(c.trash.lockCount).isEqualTo(2)
  }

  @Test
  fun the_host_hears_when_the_trash_shows_and_hides() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    c.touchDownOn(head)
    c.onTouchMove(head, 700f, 900f)
    c.onTouchMove(head, 650f, 1000f)
    c.onTouchMove(head, 600f, 1100f)
    runCurrent()
    assertThat(host.trashShown).containsExactly(true)
    c.onTouchUp(head, 0f, 0f, 600f, 1100f)
    settle()
    assertThat(host.trashShown).containsExactly(true, false).inOrder()
    val trashCentreX = c.trashX + trashPx / 2f
    val trashCentreY = c.trashY + trashPx / 2f
    c.touchDownOn(head)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    settle(1_000)
    c.onTouchUp(head, 0f, 0f, trashCentreX, trashCentreY)
    settle(WebHeadMotion.TRASH_CLOSE_MS + 50)
    assertThat(host.destroyed).containsExactly(head.url to true)
    assertThat(host.trashShown).containsExactly(true, false, true, false).inOrder()
  }

  @Test
  fun a_release_on_the_trash_stops_its_touches_before_the_close_ends() = runTest {
    val c = controller()
    c.add("https://a.example")
    val head = c.add("https://b.example")
    val trashCentreX = c.trashX + trashPx / 2f
    val trashCentreY = c.trashY + trashPx / 2f
    c.touchDownOn(head)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    settle(1_000)
    c.onTouchUp(head, 0f, 0f, trashCentreX, trashCentreY)
    runCurrent()
    // The close still plays on the trash, but the trash window takes no touch meanwhile.
    assertThat(host.destroyed).isEmpty()
    assertThat(host.trashShown).containsExactly(true, false).inOrder()
    settle(WebHeadMotion.TRASH_CLOSE_MS + 50)
    assertThat(host.destroyed).containsExactly(head.url to false)
    assertThat(host.trashShown).containsExactly(true, false).inOrder()
  }

  @Test
  @Config(sdk = [34])
  fun locking_onto_the_trash_ticks_once_with_confirm() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    val trashCentreX = c.trashX + trashPx / 2f
    val trashCentreY = c.trashY + trashPx / 2f
    c.touchDownOn(head)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    repeat(8) { i -> c.onTouchMove(head, trashCentreX + (i % 4) * 5f, trashCentreY - (i % 3) * 5f) }
    runCurrent()
    assertThat(host.haptics).containsExactly(head.url to HapticFeedbackConstants.CONFIRM)
  }

  @Test
  @Config(sdk = [29])
  fun before_android_11_the_lock_ticks_with_virtual_key() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    val trashCentreX = c.trashX + trashPx / 2f
    val trashCentreY = c.trashY + trashPx / 2f
    c.touchDownOn(head)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    repeat(8) { i -> c.onTouchMove(head, trashCentreX + (i % 4) * 5f, trashCentreY - (i % 3) * 5f) }
    runCurrent()
    assertThat(host.haptics).containsExactly(head.url to HapticFeedbackConstants.VIRTUAL_KEY)
  }

  @Test
  @Config(sdk = [34])
  fun leaving_the_trash_ticks_once_with_clock_tick() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    val trashCentreX = c.trashX + trashPx / 2f
    val trashCentreY = c.trashY + trashPx / 2f
    val lock = head.url to HapticFeedbackConstants.CONFIRM
    val leave = head.url to HapticFeedbackConstants.CLOCK_TICK
    c.touchDownOn(head)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    runCurrent()
    c.onTouchMove(head, width / 2f, height / 2f)
    c.onTouchMove(head, width / 2f + 10f, height / 2f - 10f)
    c.onTouchMove(head, width / 2f - 10f, height / 2f + 10f)
    runCurrent()
    assertThat(host.haptics).containsExactly(lock, leave).inOrder()
    c.onTouchMove(head, trashCentreX, trashCentreY)
    runCurrent()
    assertThat(host.haptics).containsExactly(lock, leave, lock).inOrder()
    c.onTouchMove(head, width / 2f, height / 2f)
    c.onTouchUp(head, 0f, 0f, width / 2f, height / 2f)
    settle()
    assertThat(host.haptics).containsExactly(lock, leave, lock, leave).inOrder()
  }

  @Test
  fun closing_on_the_trash_gives_no_leave_tick() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    val trashCentreX = c.trashX + trashPx / 2f
    val trashCentreY = c.trashY + trashPx / 2f
    c.touchDownOn(head)
    c.onTouchMove(head, trashCentreX, trashCentreY)
    settle(1_000)
    c.onTouchUp(head, 0f, 0f, trashCentreX, trashCentreY)
    settle(WebHeadMotion.TRASH_CLOSE_MS + 50)
    assertThat(host.destroyed).containsExactly(head.url to true)
    assertThat(host.haptics).containsExactly(head.url to TrashTick.LOCK.feedbackConstant())
  }

  @Test
  fun a_drag_or_tap_away_from_the_trash_ticks_nothing() = runTest {
    val c = controller()
    val head = c.add("https://a.example")
    c.touchDownOn(head)
    c.onTouchMove(head, 700f, 900f)
    c.onTouchMove(head, 650f, 1000f)
    c.onTouchMove(head, 600f, 1100f)
    c.onTouchUp(head, 0f, 0f, 600f, 1100f)
    settle()
    c.touchDownOn(head)
    val (x0, y0) = host.windows.getValue(head.url)
    c.onTouchUp(head, 0f, 0f, x0 + headPx / 2f, y0 + headPx / 2f)
    c.onTap(head)
    settle()
    assertThat(host.haptics).isEmpty()
  }

  private companion object {
    /** A normal flick of a thumb on a phone, in dp per second. */
    const val PHONE_FLICK_DP_S = 1_000f
  }
}
