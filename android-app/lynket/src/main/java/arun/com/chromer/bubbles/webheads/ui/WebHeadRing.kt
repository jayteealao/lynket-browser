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

package arun.com.chromer.bubbles.webheads.ui

import androidx.compose.animation.core.CubicBezierEasing
import arun.com.chromer.bubbles.webheads.motion.PageState

/** How long the arc of a loading head takes to turn once. */
internal const val RING_TURN_MS = 1_100

/** How long the arc takes to close to the full ring, and the head to come back to full, when the page finishes. */
internal const val RING_CLOSE_MS = 250

/** How much of the ring the arc of a loading head covers, in degrees. */
internal const val RING_ARC_SWEEP = 100f

/** How opaque the fill of a head is while its page loads. */
internal const val LOADING_FILL_ALPHA = 0.45f

/** The strong ease-out curve of the ring's close and the head's fade back, with no bounce. */
internal val RingEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** What the ring of a head draws. */
internal enum class Ring {
  /** The full ring of a head at rest. */
  Solid,

  /** A short arc on a thin track: the page is loading. */
  Arc,

  /** The thin track alone: the page failed, or it loads with reduced motion. */
  Track,
}

/**
 * What a head shows for its page: the [ring], whether the arc [turning], whether the fill is
 * [faded], and whether the [warning] mark sits on the head.
 */
internal data class HeadLook(val ring: Ring, val turning: Boolean, val faded: Boolean, val warning: Boolean)

private val AtRest = HeadLook(Ring.Solid, turning = false, faded = false, warning = false)

/**
 * The look of a head for its [pageState]. Only the head at the front of the stack ([isFront])
 * turns its arc, so the heads behind it draw no frames; with [reducedMotion] nothing turns and the
 * thin track and the faded fill carry the state. A head [closing] to the trash keeps its look of
 * today in every page state.
 */
internal fun headLook(pageState: PageState, isFront: Boolean, reducedMotion: Boolean, closing: Boolean): HeadLook = when {
  closing -> AtRest
  pageState == PageState.Loading && reducedMotion -> HeadLook(Ring.Track, turning = false, faded = true, warning = false)
  pageState == PageState.Loading -> HeadLook(Ring.Arc, turning = isFront, faded = true, warning = false)
  pageState == PageState.Failed -> HeadLook(Ring.Track, turning = false, faded = false, warning = true)
  else -> AtRest
}
