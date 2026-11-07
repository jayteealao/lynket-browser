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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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

/** The width of the ring, the same as the border of a head at rest. */
internal val RingStroke = 3.dp

/** The angle the arc last turned to, kept so a stopped arc stays where it stopped. */
private class ArcAngle(var degrees: Float = 0f)

/**
 * The ring of a head in place of its border, on its own layer: the thin `outline-variant` track
 * at [trackAlpha] and the `primary` arc of [sweep] degrees from the top. While [look] turns, one
 * infinite rotation turns the layer; its value is read only by the layer, so a frame recomposes
 * and redraws nothing. A head that does not turn has no rotation running at all.
 */
@Composable
internal fun HeadRing(look: HeadLook, sweep: () -> Float, trackAlpha: () -> Float, modifier: Modifier = Modifier) {
  val colours = MaterialTheme.colorScheme
  val track = colours.outlineVariant
  val arc = colours.primary
  val angle = remember { ArcAngle() }
  val turn = if (look.turning) {
    val transition = rememberInfiniteTransition(label = "headRing")
    // A head that starts to turn again carries on from where its arc stopped.
    val start = remember { angle.degrees }
    transition.animateFloat(
      initialValue = start,
      targetValue = start + 360f,
      animationSpec = infiniteRepeatable(tween(RING_TURN_MS, easing = LinearEasing)),
      label = "headRingTurn",
    )
  } else {
    null
  }
  Box(
    modifier
      .fillMaxSize()
      .graphicsLayer {
        if (turn != null) angle.degrees = turn.value % 360f
        rotationZ = angle.degrees
      }
      .drawBehind {
        val stroke = RingStroke.toPx()
        val inset = stroke / 2
        val ringSize = Size(size.width - stroke, size.height - stroke)
        val alpha = trackAlpha()
        if (alpha > 0f) {
          drawCircle(track, radius = ringSize.minDimension / 2, alpha = alpha, style = Stroke(stroke))
        }
        val degrees = sweep()
        if (degrees > 0f) {
          drawArc(
            arc,
            startAngle = -90f,
            sweepAngle = degrees,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = ringSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
          )
        }
      },
  )
}
