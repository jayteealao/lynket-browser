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

import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion.ScreenBounds
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion.StackAnchor
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion.StackAnchor.Side
import com.google.common.collect.Range
import com.google.common.truth.IntegerSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The spring numbers must equal the earlier physics after conversion, so the heads feel the same. */
class WebHeadMotionTest {

  @Test
  fun fling_spring_matches_the_old_origami_50_5() {
    assertThat(WebHeadMotion.tension(50.0)).isWithin(1e-9).of(266.4)
    assertThat(WebHeadMotion.friction(5.0)).isWithin(1e-9).of(16.0)
    assertThat(WebHeadMotion.Fling.stiffness).isWithin(1e-3f).of(266.4f)
    assertThat(WebHeadMotion.Fling.dampingRatio).isWithin(1e-3f).of(0.490f)
  }

  @Test
  fun snap_spring_matches_the_old_origami_100_7() {
    assertThat(WebHeadMotion.Snap.stiffness).isWithin(1e-3f).of(447.4f)
    assertThat(WebHeadMotion.Snap.dampingRatio).isWithin(1e-3f).of(0.520f)
  }

  @Test
  fun the_resnap_spring_is_the_snap_stiffness_with_no_bounce() {
    assertThat(WebHeadMotion.Resnap.dampingRatio).isEqualTo(1f)
    assertThat(WebHeadMotion.Resnap.stiffness).isEqualTo(WebHeadMotion.Snap.stiffness)
  }

  @Test
  fun followers_further_back_are_more_damped() {
    val second = WebHeadMotion.followerSpring(2)
    val third = WebHeadMotion.followerSpring(3)
    assertThat(second.stiffness).isWithin(1e-3f).of(WebHeadMotion.tension(90.0).toFloat())
    assertThat(second.dampingRatio).isLessThan(third.dampingRatio)
  }

  @Test
  fun zero_origami_values_convert_to_zero() {
    assertThat(WebHeadMotion.tension(0.0)).isEqualTo(0.0)
    assertThat(WebHeadMotion.friction(0.0)).isEqualTo(0.0)
  }

  @Test
  fun fling_decay_uses_the_drag_friction() {
    assertThat(WebHeadMotion.DragFriction).isWithin(1e-9).of(6.4)
    assertThat(WebHeadMotion.FlingDecayMultiplier).isWithin(1e-3f).of(1.524f)
  }

  @Test
  fun bounds_leave_thirty_percent_of_the_head_off_screen() {
    val bounds = ScreenBounds.of(dispWidth = 1080, dispHeight = 2400, headWidth = 200, density = 2.625f)
    assertThat(bounds.left).isEqualTo(-60)
    assertThat(bounds.right).isEqualTo(1080 - 140)
    assertThat(bounds.top).isEqualTo(66)
    assertThat(bounds.bottom).isEqualTo(2040)
  }

  @Test(expected = IllegalArgumentException::class)
  fun bounds_refuse_a_zero_size() {
    ScreenBounds.of(0, 2400, 200, 1f)
  }

  @Test
  fun only_five_heads_are_visible() {
    assertThat(WebHeadMotion.isQueued(5)).isFalse()
    assertThat(WebHeadMotion.isQueued(6)).isTrue()
  }

  @Test
  fun velocity_signs_follow_the_last_movement() {
    assertThat(WebHeadMotion.adjustVelocities(0f, 0f, 10f, 10f, -5f, -7f)).isEqualTo(5f to 7f)
    assertThat(WebHeadMotion.adjustVelocities(0f, 10f, 10f, 0f, -5f, 7f)).isEqualTo(5f to -7f)
    assertThat(WebHeadMotion.adjustVelocities(10f, 10f, 0f, 0f, 5f, 7f)).isEqualTo(-5f to -7f)
    assertThat(WebHeadMotion.adjustVelocities(10f, 0f, 0f, 10f, 5f, -7f)).isEqualTo(-5f to 7f)
  }

  @Test
  fun a_slow_rightward_fling_is_raised_to_reach_the_edge() {
    val v = WebHeadMotion.interpolateXVelocity(velocityX = 100f, upRawX = 540f, dispWidth = 1080, minHorizontalVelocity = 1000f)
    assertThat(v).isEqualTo(500f)
  }

  @Test
  fun a_leftward_fling_keeps_the_earlier_rule() {
    val v = WebHeadMotion.interpolateXVelocity(velocityX = -100f, upRawX = 270f, dispWidth = 1080, minHorizontalVelocity = 1000f)
    assertThat(v).isEqualTo(-250f)
  }

  @Test
  fun minimum_fling_is_ten_screen_widths_per_second() {
    assertThat(WebHeadMotion.minHorizontalFlingVelocity(411, 2f)).isEqualTo(8220f)
  }

  @Test
  fun the_fling_floor_stops_growing_past_a_600_dp_window() {
    assertThat(WebHeadMotion.minHorizontalFlingVelocity(360, 2f)).isEqualTo(7_200f)
    assertThat(WebHeadMotion.minHorizontalFlingVelocity(600, 2f)).isEqualTo(12_000f)
    assertThat(WebHeadMotion.minHorizontalFlingVelocity(768, 2f)).isEqualTo(12_000f)
    assertThat(WebHeadMotion.minHorizontalFlingVelocity(1_280, 2f)).isEqualTo(12_000f)
  }

  @Test
  fun the_edge_spring_is_the_fling_stiffness_with_no_bounce() {
    assertThat(WebHeadMotion.FlingToEdge.dampingRatio).isEqualTo(1f)
    assertThat(WebHeadMotion.FlingToEdge.stiffness).isEqualTo(WebHeadMotion.Fling.stiffness)
  }

  @Test
  fun a_fling_reaches_its_edge_when_the_decay_projects_past_it() {
    assertThat(WebHeadMotion.flingReachesEdge(x = 1_212f, velocityX = 6_000f, headWidth = 136, dispWidth = 2_560)).isFalse()
    assertThat(WebHeadMotion.flingReachesEdge(x = 1_212f, velocityX = 12_800f, headWidth = 136, dispWidth = 2_560)).isTrue()
    assertThat(WebHeadMotion.flingReachesEdge(x = 1_340f, velocityX = -6_600f, headWidth = 136, dispWidth = 2_560)).isFalse()
    assertThat(WebHeadMotion.flingReachesEdge(x = 1_340f, velocityX = -12_000f, headWidth = 136, dispWidth = 2_560)).isTrue()
    assertThat(WebHeadMotion.flingReachesEdge(x = 471f, velocityX = 5_394f, headWidth = 178, dispWidth = 1_080)).isTrue()
  }

  @Test
  fun followers_step_away_from_the_master_on_its_side() {
    assertThat(WebHeadMotion.chainOffset(1, masterOnRight = true, density = 2f)).isEqualTo(8 to 3)
    assertThat(WebHeadMotion.chainOffset(2, masterOnRight = false, density = 2f)).isEqualTo(-16 to 6)
  }

  private val portrait = ScreenBounds.of(1080, 2400, 178, 2.625f)
  private val landscape = ScreenBounds.of(2400, 1080, 178, 2.625f)

  @Test
  fun the_anchor_side_follows_the_half_of_the_screen() {
    assertThat(StackAnchor.of(-53, 800, portrait, 1080).side).isEqualTo(Side.LEFT)
    assertThat(StackAnchor.of(955, 800, portrait, 1080).side).isEqualTo(Side.RIGHT)
  }

  @Test
  fun the_anchor_ratio_runs_from_the_top_to_the_bottom_rest_position() {
    assertThat(StackAnchor.of(-53, portrait.top, portrait, 1080).ratio).isEqualTo(0f)
    assertThat(StackAnchor.of(-53, portrait.bottom, portrait, 1080).ratio).isEqualTo(1f)
    assertThat(StackAnchor.of(-53, portrait.top - 40, portrait, 1080).ratio).isEqualTo(0f)
    assertThat(StackAnchor.of(-53, portrait.bottom + 300, portrait, 1080).ratio).isEqualTo(1f)
  }

  @Test
  fun the_anchor_position_is_on_its_edge_at_its_share_of_the_height() {
    assertThat(StackAnchor(Side.LEFT, 0.5f).position(portrait)).isEqualTo(portrait.left to 66 + 987)
    assertThat(StackAnchor(Side.RIGHT, 0f).position(portrait)).isEqualTo(portrait.right to portrait.top)
    assertThat(StackAnchor(Side.RIGHT, 1f).position(landscape)).isEqualTo(landscape.right to landscape.bottom)
  }

  @Test
  fun a_portrait_seventy_percent_maps_into_the_landscape_range() {
    val y = portrait.top + (0.7f * (portrait.bottom - portrait.top)).toInt()
    val (lx, ly) = StackAnchor.of(-53, y, portrait, 1080).position(landscape)
    assertThat(lx).isEqualTo(landscape.left)
    assertThat(ly).isIn(Range.closed(landscape.top, landscape.bottom))
    assertThat(ly).isWithin1Of(66 + Math.round(0.7f * (landscape.bottom - landscape.top)))
  }

  @Test
  fun a_window_too_short_for_a_range_rests_at_the_top() {
    val short = ScreenBounds(left = -53, right = 300, top = 66, bottom = 50)
    val anchor = StackAnchor.of(-53, 60, short, 400)
    assertThat(anchor.ratio).isEqualTo(0f)
    assertThat(anchor.position(short)).isEqualTo(-53 to 66)
    assertThat(StackAnchor(Side.LEFT, 1f).position(short)).isEqualTo(-53 to 66)
  }

  private fun IntegerSubject.isWithin1Of(expected: Int) = isIn(Range.closed(expected - 1, expected + 1))
}
