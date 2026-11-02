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

package arun.com.chromer.bubbles.webheads.motion

import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The web head motion numbers. They are the values of the earlier spring physics, converted with
 * that library's own formulas (origami tension and friction to spring tension and friction), so
 * the heads keep their feel:
 *
 * - tension = (origamiTension - 30) * 3.62 + 194, or 0 when the origami value is 0
 * - friction = (origamiFriction - 8) * 3 + 25, or 0 when the origami value is 0
 *
 * A spring of tension k and friction c with mass 1 is a Compose spring with stiffness k and
 * damping ratio c / (2 * sqrt(k)). The drag spring has no tension, so a fling is a velocity that
 * decays with friction c: Compose's exponential decay uses a friction of -4.2 * multiplier.
 */
object WebHeadMotion {

  /** Converted origami tension (spring stiffness). */
  fun tension(origamiTension: Double): Double =
    if (origamiTension == 0.0) 0.0 else (origamiTension - 30.0) * 3.62 + 194.0

  /** Converted origami friction (spring damping coefficient). */
  fun friction(origamiFriction: Double): Double =
    if (origamiFriction == 0.0) 0.0 else (origamiFriction - 8.0) * 3.0 + 25.0

  fun dampingRatio(tension: Double, friction: Double): Float = (friction / (2.0 * sqrt(tension))).toFloat()

  private fun spring(origamiTension: Double, origamiFriction: Double): SpringSpec<Float> {
    val k = tension(origamiTension)
    val c = friction(origamiFriction)
    return SpringSpec(dampingRatio = dampingRatio(k, c), stiffness = k.toFloat(), visibilityThreshold = 0.5f)
  }

  /** Release towards an edge, the return of a new master, and every bound correction. */
  val Fling: SpringSpec<Float> = spring(50.0, 5.0)

  /**
   * Carries a fling that would stop short of its edge on a wide window there: the fling spring's
   * stiffness with no bounce.
   */
  val FlingToEdge: SpringSpec<Float> = SpringSpec(dampingRatio = 1f, stiffness = Fling.stiffness, visibilityThreshold = 0.5f)

  /** Lock onto the trash. */
  val Snap: SpringSpec<Float> = spring(100.0, 7.0)

  /**
   * Moves the heads to their place after a rotation, a fold or a resize: the lock spring's
   * stiffness with no bounce.
   */
  val Resnap: SpringSpec<Float> = SpringSpec(dampingRatio = 1f, stiffness = tension(100.0).toFloat(), visibilityThreshold = 0.5f)

  /** Trash target scale. */
  val TrashScale: SpringSpec<Float> = spring(100.0, 9.0)

  /** Drag spring friction (origami 0, 1.8): a fling slows with this friction per second. */
  val DragFriction: Double = friction(1.8)

  /** Compose's exponential decay friction is -4.2 * multiplier per second. */
  val FlingDecayMultiplier: Float = (DragFriction / 4.2).toFloat()

  val FlingDecay: DecayAnimationSpec<Float> = exponentialDecay(frictionMultiplier = FlingDecayMultiplier, absVelocityThreshold = 1f)

  /**
   * Spring of a follower. [chainIndex] is the earlier chain index: 2 for the head right behind
   * the master, 3 for the next, and so on. Followers further back are slower.
   */
  fun followerSpring(chainIndex: Int): SpringSpec<Float> = spring(90.0, 9.0 + chainIndex * 5.0)

  /** Heads with an index above this (the oldest first) are queued and hidden. */
  const val MAX_VISIBLE_WEB_HEADS = 5

  /** Distance from the trash centre, in dp, inside which a dragged head locks to the trash. */
  const val MAGNETISM_DP = 120f

  /** Offsets of each follower behind the master, per follower, in dp. */
  const val CHAIN_X_DP = 4f
  const val CHAIN_Y_DP = 1.7f

  /** The master coasts partly off screen after this much time without a touch. */
  const val COASTING_DELAY_MS = 6_000L

  /** Close-on-open shrink of a tapped head. */
  const val CLOSE_ON_OPEN_MS = 125L

  /** Red close animation of a head released on the trash, before it is removed. */
  const val TRASH_CLOSE_MS = 700L

  /** Trash scale-out after the last head closes. */
  const val TRASH_EXIT_MS = 300L

  const val TOP_BOUND_DP = 25f

  /** Head circle size, and the window around it that leaves room for the badge and the shadow. */
  const val HEAD_DP = 56f
  const val SMALL_HEAD_DP = 48f
  const val HEAD_WINDOW_INSET_DP = 6f
  const val TRASH_WINDOW_DP = 92f

  /** Rest positions: a head is 30% off screen at the side edges. */
  data class ScreenBounds(val left: Int, val right: Int, val top: Int, val bottom: Int) {
    companion object {
      private const val DISPLACE = 0.7

      fun of(dispWidth: Int, dispHeight: Int, headWidth: Int, density: Float): ScreenBounds {
        require(headWidth > 0 && dispWidth > 0 && dispHeight > 0) { "Width of web head or screen size cannot be 0" }
        return ScreenBounds(
          left = (headWidth * (1 - DISPLACE) * -1).toInt(),
          right = (dispWidth - headWidth * DISPLACE).toInt(),
          top = (TOP_BOUND_DP * density + 0.5f).toInt(),
          bottom = (dispHeight * 0.85).toInt(),
        )
      }
    }
  }

  /**
   * The place of the stack as a screen edge and a share of the height a head can rest in, so it
   * means the same place after a rotation, a fold or a resize.
   */
  data class StackAnchor(val side: Side, val ratio: Float) {
    enum class Side { LEFT, RIGHT }

    /** The rest position on the anchor's edge, inside [bounds]. */
    fun position(bounds: ScreenBounds): Pair<Int, Int> {
      val x = if (side == Side.RIGHT) bounds.right else bounds.left
      val range = max(0, bounds.bottom - bounds.top)
      val y = bounds.top + (ratio * range).roundToInt()
      return x to y.coerceIn(bounds.top, bounds.top + range)
    }

    companion object {
      /** The anchor of a head window at ([x], [y]) on a display [dispWidth] wide. */
      fun of(x: Int, y: Int, bounds: ScreenBounds, dispWidth: Int): StackAnchor {
        val side = if (x > dispWidth / 2) Side.RIGHT else Side.LEFT
        val range = bounds.bottom - bounds.top
        val ratio = if (range <= 0) 0f else ((y - bounds.top).toFloat() / range).coerceIn(0f, 1f)
        return StackAnchor(side, ratio)
      }
    }
  }

  /** True when the head at this queue index (count down from the total, oldest first) hides. */
  fun isQueued(index: Int): Boolean = index > MAX_VISIBLE_WEB_HEADS

  /**
   * Corrects the sign of a fling velocity from the direction of the last movement, because raw
   * velocity tracker values sometimes have the wrong sign.
   */
  fun adjustVelocities(downX: Float, downY: Float, upX: Float, upY: Float, vx: Float, vy: Float): Pair<Float, Float> = when {
    upX >= downX && upY >= downY -> abs(vx) to abs(vy)
    upX >= downX && upY <= downY -> abs(vx) to negate(vy)
    upX <= downX && upY <= downY -> negate(vx) to negate(vy)
    else -> negate(vx) to abs(vy)
  }

  private fun negate(value: Float): Float = if (value > 0) -value else value

  /**
   * Raises a horizontal fling so it reaches the side it points to. The leftward case keeps the
   * earlier behaviour exactly: it always uses the minimum velocity for the distance to the left.
   */
  fun interpolateXVelocity(velocityX: Float, upRawX: Float, dispWidth: Int, minHorizontalVelocity: Float): Float {
    val x = upRawX / dispWidth
    return if (velocityX > 0) {
      max(velocityX, minHorizontalVelocity * (1 - x))
    } else {
      -max(velocityX, minHorizontalVelocity * x)
    }
  }

  /**
   * True when a head window at [x], flung at [velocityX] px per second, slows down past the point
   * where the bound correction takes it to the edge it points to: the right edge when the window
   * reaches the display's right side, the left edge when it reaches the left side.
   */
  fun flingReachesEdge(x: Float, velocityX: Float, headWidth: Int, dispWidth: Int): Boolean {
    val end = FlingDecay.calculateTargetValue(x, velocityX)
    return if (velocityX > 0) end + headWidth >= dispWidth else end - headWidth <= 0
  }

  /**
   * The fling floor stops growing at a 600 dp window, so a wider window moves a flung head no
   * faster than a tablet does, in dp per second.
   */
  const val MAX_MIN_FLING_DP_PER_S = 6_000f

  /**
   * Minimum horizontal fling velocity: ten times the window width in dp per second, at most
   * [MAX_MIN_FLING_DP_PER_S], as px per second.
   */
  fun minHorizontalFlingVelocity(screenWidthDp: Int, density: Float): Float =
    min(screenWidthDp * 10f, MAX_MIN_FLING_DP_PER_S) * density

  /** Offset of follower [n] (1 = right behind the master) from the master, in px. */
  fun chainOffset(n: Int, masterOnRight: Boolean, density: Float): Pair<Int, Int> {
    val xDiff = (CHAIN_X_DP * density + 0.5f).toInt()
    val yDiff = (CHAIN_Y_DP * density + 0.5f).toInt()
    return (if (masterOnRight) n * xDiff else -n * xDiff) to n * yDiff
  }
}
