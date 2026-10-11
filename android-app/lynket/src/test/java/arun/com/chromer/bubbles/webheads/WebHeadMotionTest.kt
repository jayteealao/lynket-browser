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
  fun followers_step_away_from_the_master_on_its_side() {
    assertThat(WebHeadMotion.chainOffset(1, masterOnRight = true, density = 2f)).isEqualTo(8 to 3)
    assertThat(WebHeadMotion.chainOffset(2, masterOnRight = false, density = 2f)).isEqualTo(-16 to 6)
  }
}
