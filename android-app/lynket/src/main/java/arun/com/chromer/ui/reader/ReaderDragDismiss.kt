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

package arun.com.chromer.ui.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/** How far past the top or bottom edge a drag must go to close the reader (the legacy 192 dp). */
val DragDismissDistance = 192.dp

/** The column moves half as far as the finger past the edge. */
const val DRAG_RESISTANCE = 0.5f

/**
 * Drag past the top or bottom of the article to close the reader. The drag the list does not use
 * is collected; the column follows it with resistance. Released past [DragDismissDistance], or
 * flung on in the same direction, it closes; otherwise it returns without a bounce.
 */
@Stable
class ReaderDragDismissState(
  private val distancePx: Float,
  private val flingPx: Float,
  private val onDismiss: () -> Unit,
) {
  /** Collected drag past the edge in px (positive: down past the top). */
  var drag by mutableFloatStateOf(0f)
    private set

  /** Where the column draws, in px. */
  val offset: Float get() = drag * DRAG_RESISTANCE

  private var dismissed = false

  /** Adds [dy] px of drag the list did not use. */
  internal fun onOverscroll(dy: Float) {
    drag += dy
  }

  /** Release with [velocity] px/s: closes past the distance or on a fling away from the content. */
  internal suspend fun release(velocity: Float) {
    val flungOn = drag != 0f && sign(velocity) == sign(drag) && abs(velocity) > flingPx
    if (!dismissed && (abs(drag) >= distancePx || flungOn)) {
      dismissed = true
      onDismiss()
      return
    }
    animate(drag, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
      drag = value
    }
  }

  val connection: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      // While the column is pulled away, a drag back toward the edge first returns it.
      if (source != NestedScrollSource.UserInput || drag == 0f || sign(available.y) == sign(drag)) return Offset.Zero
      val next = drag + available.y
      val consumed = if (sign(next) != sign(drag)) -drag else available.y
      drag += consumed
      return Offset(0f, consumed)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
      onOverscroll(available.y)
      return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
      if (drag == 0f) return Velocity.Zero
      release(available.y)
      return available
    }
  }
}

@Composable
fun rememberReaderDragDismissState(onDismiss: () -> Unit): ReaderDragDismissState {
  val density = LocalDensity.current
  val dismiss by rememberUpdatedState(onDismiss)
  return remember(density) {
    with(density) { ReaderDragDismissState(DragDismissDistance.toPx(), 2_000.dp.toPx()) { dismiss() } }
  }
}
