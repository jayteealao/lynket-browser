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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

const val IMAGE_MIN_SCALE = 1f
const val IMAGE_MAX_SCALE = 3f
const val IMAGE_DOUBLE_TAP_SCALE = 1.75f

/**
 * Zoom and pan of the image viewer: pinch from 1x to 3x, pan while zoomed (kept inside the image),
 * double tap between 1x and 1.75x.
 */
class ImageZoom(scale: Float = IMAGE_MIN_SCALE, offset: Offset = Offset.Zero) {
  var scale by mutableFloatStateOf(scale)
    private set
  var offset by mutableStateOf(offset)
    private set
  var size = IntSize.Zero

  val zoomed: Boolean get() = scale > IMAGE_MIN_SCALE

  fun transform(zoomChange: Float, pan: Offset) {
    scale = (scale * zoomChange).coerceIn(IMAGE_MIN_SCALE, IMAGE_MAX_SCALE)
    offset = clamp(offset + pan)
  }

  fun toggle() {
    scale = if (zoomed) IMAGE_MIN_SCALE else IMAGE_DOUBLE_TAP_SCALE
    offset = clamp(offset)
  }

  private fun clamp(value: Offset): Offset {
    val maxX = (scale - 1f) * size.width / 2f
    val maxY = (scale - 1f) * size.height / 2f
    return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
  }
}

/**
 * The image viewer: the image fitted to the width on a dark surface, Close and Share at the top,
 * a "Pinch to zoom" hint at the bottom until the first zoom, and a message when the image cannot
 * be loaded.
 */
@Composable
fun ImageViewerScreen(url: String, onClose: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
  val zoom = rememberSaveable(saver = imageZoomSaver) { ImageZoom() }
  // Recomposes when zoomed flips, not on every pinch frame.
  val zoomed by remember(zoom) { derivedStateOf { zoom.zoomed } }
  var hintSeen by rememberSaveable { mutableStateOf(false) }
  val transformState = rememberTransformableState { zoomChange, pan, _ ->
    zoom.transform(zoomChange, pan)
    if (zoom.zoomed) hintSeen = true
  }
  Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize().testTag("image-viewer")) {
    Box(Modifier.fillMaxSize()) {
      val zoomIn = stringResource(R.string.image_zoom_in)
      val zoomOut = stringResource(R.string.image_zoom_out)
      SubcomposeAsyncImage(
        model = url,
        contentDescription = stringResource(R.string.reader_article_image),
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
          .fillMaxSize()
          .onSizeChanged { zoom.size = it }
          .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = {
              zoom.toggle()
              hintSeen = true
            })
          }
          .transformable(transformState, canPan = { zoom.zoomed })
          // Zoom without a two-finger gesture: screen readers, switch access and keyboards.
          .semantics {
            customActions = listOf(
              CustomAccessibilityAction(zoomIn) { zoom.transform(ZOOM_STEP, Offset.Zero); hintSeen = true; true },
              CustomAccessibilityAction(zoomOut) { zoom.transform(1f / ZOOM_STEP, Offset.Zero); true },
            )
          }
          .graphicsLayer {
            scaleX = zoom.scale
            scaleY = zoom.scale
            translationX = zoom.offset.x
            translationY = zoom.offset.y
          }
          .testTag(if (zoomed) "image-zoomed" else "image"),
      ) {
        val state by painter.state.collectAsState()
        when (state) {
          is AsyncImagePainter.State.Error -> Column(
            Modifier.fillMaxSize().testTag("image-error"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Text(
              stringResource(R.string.image_load_failed),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 12.dp),
            )
          }
          else -> SubcomposeAsyncImageContent()
        }
      }
      Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("image-close")) {
          Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onShare, modifier = Modifier.testTag("image-share")) {
          Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share), tint = MaterialTheme.colorScheme.onSurface)
        }
      }
      if (!hintSeen) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = 24.dp)
            .testTag("image-hint"),
        ) {
          Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(
              stringResource(R.string.pinch_to_zoom),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(start = 8.dp),
            )
          }
        }
      }
    }
  }
}

private val imageZoomSaver = androidx.compose.runtime.saveable.Saver<ImageZoom, List<Float>>(
  save = { listOf(it.scale, it.offset.x, it.offset.y) },
  restore = { ImageZoom(it[0], Offset(it[1], it[2])) },
)

/** One zoom in or out step of the accessibility actions. */
private const val ZOOM_STEP = 1.5f
