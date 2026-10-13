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

package arun.com.chromer.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import arun.com.chromer.R

/**
 * Pulse of the skeleton; held still when the person turned animations off. It returns a reader
 * for the draw phase ([Modifier.skeletonPulse]), so the rows do not recompose on every frame.
 */
@Composable
internal fun skeletonAlpha(): () -> Float {
  val resolver = LocalContext.current.contentResolver
  val animationsOff = remember(resolver) {
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
  }
  if (animationsOff) return { STILL_ALPHA }
  val transition = rememberInfiniteTransition(label = "skeleton")
  val alpha = transition.animateFloat(
    initialValue = 0.45f,
    targetValue = 0.9f,
    animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
    label = "skeleton-alpha",
  )
  return { alpha.value }
}

private const val STILL_ALPHA = 0.6f

/** Applies the skeleton pulse in the draw phase. */
internal fun Modifier.skeletonPulse(alpha: () -> Float): Modifier = graphicsLayer { this.alpha = alpha() }

/** Skeleton rows for a loading list: no spinner in a content area. */
@Composable
fun LoadingList(
  modifier: Modifier = Modifier,
  rows: Int = 6,
  loadingLabel: String = stringResource(R.string.loading),
) {
  val alpha = skeletonAlpha()
  val block = MaterialTheme.colorScheme.surfaceContainerHighest
  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("loading-list")
      .semantics { contentDescription = loadingLabel },
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    repeat(rows) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).skeletonPulse(alpha),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(40.dp).background(block, CircleShape))
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(Modifier.fillMaxWidth(0.6f).height(14.dp).background(block, RoundedCornerShape(7.dp)))
          Box(Modifier.fillMaxWidth(0.4f).height(12.dp).background(block, RoundedCornerShape(6.dp)))
        }
      }
    }
  }
}

/** Skeleton tiles for a loading grid (the home recents). */
@Composable
fun LoadingTiles(
  columns: Int,
  modifier: Modifier = Modifier,
  rows: Int = 2,
  loadingLabel: String = stringResource(R.string.loading),
) {
  val alpha = skeletonAlpha()
  val block = MaterialTheme.colorScheme.surfaceContainerHighest
  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("loading-tiles")
      .semantics { contentDescription = loadingLabel },
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    repeat(rows) {
      Row(Modifier.fillMaxWidth().skeletonPulse(alpha), horizontalArrangement = Arrangement.SpaceEvenly) {
        repeat(columns) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(Modifier.size(56.dp).background(block, RoundedCornerShape(18.dp)))
            Box(Modifier.width(44.dp).height(10.dp).background(block, RoundedCornerShape(5.dp)))
          }
        }
      }
    }
  }
}
