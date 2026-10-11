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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import arun.com.chromer.bubbles.webheads.motion.TrashState
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion

/**
 * The trash target under the screen centre. It appears when a head is dragged, and morphs from a
 * neutral circle into a red rounded square when a head locks onto it.
 */
@Composable
fun TrashTarget(trash: TrashState, modifier: Modifier = Modifier) {
  val scale by animateFloatAsState(
    targetValue = trash.scale,
    animationSpec = if (trash.exiting) tween(WebHeadMotion.TRASH_EXIT_MS.toInt()) else WebHeadMotion.TrashScale,
    label = "trashScale",
  )
  val colours = MaterialTheme.colorScheme
  // 36 dp is half of the 72 dp target: a circle. Locked, the corners morph to 24 dp.
  val corner by animateDpAsState(if (trash.locked) 24.dp else 36.dp, label = "trashCorner")
  val container by animateColorAsState(if (trash.locked) colours.error else colours.surfaceContainerHighest, label = "trashColour")
  val content by animateColorAsState(if (trash.locked) colours.onError else colours.onSurfaceVariant, label = "trashIcon")
  Box(modifier = modifier.size(WebHeadMotion.TRASH_WINDOW_DP.dp), contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          alpha = if (scale <= 0.01f) 0f else 1f
        }
        .size(72.dp)
        .shadow(6.dp, RoundedCornerShape(corner))
        .background(container, RoundedCornerShape(corner)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (trash.locked) Icons.Filled.Close else Icons.Filled.Delete,
        contentDescription = null,
        tint = content,
        modifier = Modifier.size(28.dp),
      )
    }
  }
}
