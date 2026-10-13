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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arun.com.chromer.R
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadState
import arun.com.chromer.util.Utils
import arun.com.chromer.ui.theme.readableContentColor

/** What a head forwards from the finger; positions are raw screen coordinates. */
interface WebHeadGestures {
  fun onDown(head: WebHeadState, raw: Offset)
  fun onMove(head: WebHeadState, raw: Offset)
  fun onUp(head: WebHeadState, velocity: Offset, raw: Offset, tap: Boolean)
  fun onLongPress(head: WebHeadState)

  /** Opens the page without a touch: screen readers, switch access and keyboards. */
  fun onOpen(head: WebHeadState) {}

  /** Closes the head without dragging it to the trash. */
  fun onClose(head: WebHeadState) {}
}

const val WEB_HEAD_TEST_TAG = "web_head"

/**
 * A web head: a circle with a primary ring, the favicon or the first letter of the site on the
 * site colour, and a count badge on the side towards the screen. [headSize] is 56 dp, or 48 dp for
 * the small size setting; the window around it has room for the badge and the shadow.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WebHeadBubble(
  head: WebHeadState,
  headSize: Dp,
  gestures: WebHeadGestures?,
  modifier: Modifier = Modifier,
) {
  val inset = WebHeadMotion.HEAD_WINDOW_INSET_DP.dp
  val description = stringResource(R.string.web_head_content_description, head.website.safeLabel())
  val openLabel = stringResource(R.string.web_head_action_open)
  val listLabel = stringResource(R.string.web_head_action_list)
  val closeLabel = stringResource(R.string.web_head_action_close)
  val scale by animateFloatAsState(
    targetValue = when {
      head.shrinking -> 0f
      head.revealed -> 1f
      else -> 0f
    },
    animationSpec = if (head.shrinking) {
      tween(WebHeadMotion.CLOSE_ON_OPEN_MS.toInt())
    } else {
      spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
    },
    label = "headScale",
  )
  val alpha by animateFloatAsState(if (head.shrinking) 0.5f else 1f, tween(WebHeadMotion.CLOSE_ON_OPEN_MS.toInt()), label = "headAlpha")
  val colours = MaterialTheme.colorScheme
  val fill by animateColorAsState(
    if (head.closing) colours.error else head.colour ?: colours.primary,
    tween(400, delayMillis = 100),
    label = "headFill",
  )
  val xRotation by animateFloatAsState(if (head.closing) 180f else 0f, tween(250), label = "closeRotation")

  Box(
    modifier = modifier
      .size(headSize + inset * 2)
      .semantics {
        testTagsAsResourceId = true
        contentDescription = description
        // Every gesture has a non-drag way in: open, the list of heads, and close.
        if (gestures != null) {
          role = Role.Button
          onClick(label = openLabel) { gestures.onOpen(head); true }
          onLongClick(label = listLabel) { gestures.onLongPress(head); true }
          customActions = listOf(CustomAccessibilityAction(closeLabel) { gestures.onClose(head); true })
        }
      }
      .testTag(WEB_HEAD_TEST_TAG)
      .then(if (gestures != null) Modifier.webHeadGestures(head, gestures) else Modifier),
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          this.alpha = alpha
          transformOrigin = TransformOrigin(if (head.badgeOnLeft) 1f else 0f, 0.75f)
        }
        .size(headSize)
        .shadow(if (head.closing) 0.dp else 6.dp, CircleShape)
        .background(colours.surfaceContainerLowest, CircleShape)
        .border(3.dp, if (head.closing) colours.error else colours.primary, CircleShape)
        .padding(5.dp)
        .clip(CircleShape)
        .background(fill),
      contentAlignment = Alignment.Center,
    ) {
      val favicon = head.favicon
      when {
        head.closing -> Icon(
          Icons.Filled.Close,
          contentDescription = null,
          tint = colours.onError,
          modifier = Modifier.size(18.dp).rotate(xRotation),
        )
        favicon != null -> Image(
          bitmap = favicon,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(headSize - 10.dp).background(colours.surfaceContainerLowest),
        )
        else -> Text(
          text = Utils.getFirstLetter(head.url),
          color = fill.readableContentColor(),
          fontSize = if (headSize >= WebHeadMotion.HEAD_DP.dp) 22.sp else 18.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
    if (head.isMaster && !head.lockedToTrash && !head.closing) {
      Surface(
        color = colours.tertiary,
        contentColor = colours.onTertiary,
        shape = CircleShape,
        modifier = Modifier
          .align(if (head.badgeOnLeft) Alignment.TopStart else Alignment.TopEnd)
          .graphicsLayer { scaleX = scale; scaleY = scale },
      ) {
        Text(
          text = head.badgeCount.toString(),
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
      }
    }
  }
}

private fun PointerEvent.raw(fallback: Offset): Offset =
  motionEvent?.let { Offset(it.rawX, it.rawY) } ?: fallback

/** Raw-position drag, tap and long press for the master head. */
private fun Modifier.webHeadGestures(head: WebHeadState, gestures: WebHeadGestures): Modifier =
  pointerInput(head) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      val start = currentEvent.raw(down.position)
      val tracker = VelocityTracker()
      tracker.addPosition(down.uptimeMillis, start)
      gestures.onDown(head, start)
      var last = start
      var moved = false
      var longPressed = false
      while (true) {
        val waitFor = viewConfiguration.longPressTimeoutMillis - (currentEvent.changes.first().uptimeMillis - down.uptimeMillis)
        val event = if (!moved && !longPressed && waitFor > 0) {
          withTimeoutOrNull(waitFor) { awaitPointerEvent() }
        } else if (!moved && !longPressed) {
          null
        } else {
          awaitPointerEvent()
        }
        if (event == null) {
          longPressed = true
          gestures.onLongPress(head)
          continue
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        last = event.raw(last)
        tracker.addPosition(change.uptimeMillis, last)
        if (!change.pressed) {
          val v = tracker.calculateVelocity()
          gestures.onUp(head, Offset(v.x, v.y), last, tap = !moved && !longPressed)
          change.consume()
          break
        }
        if ((last - start).getDistance() > viewConfiguration.touchSlop) moved = true
        gestures.onMove(head, last)
        change.consume()
      }
    }
  }
