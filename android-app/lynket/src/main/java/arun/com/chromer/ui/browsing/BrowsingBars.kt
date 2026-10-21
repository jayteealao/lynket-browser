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

package arun.com.chromer.ui.browsing

import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.browsing.menu.BrowsingMenuItem
import arun.com.chromer.browsing.menu.MenuPlacement

/** The test tag of a menu or bottom-bar item: `menu-<id name>`, for example `menu-menu_share`. */
@Composable
fun menuTag(item: BrowsingMenuItem): String {
  val resources = LocalContext.current.resources
  return remember(item.id) { "menu-" + runCatching { resources.getResourceEntryName(item.id) }.getOrDefault(item.id.toString()) }
}

@Composable
private fun MenuItemIcon(item: BrowsingMenuItem, tint: Color, modifier: Modifier = Modifier) {
  when {
    item.appIcon != null -> Image(item.appIcon, contentDescription = null, modifier = modifier.size(24.dp))
    item.iconRes != null -> Icon(painterResource(item.iconRes), contentDescription = null, tint = tint, modifier = modifier.size(24.dp))
    item.id == R.id.menu_text_size -> Icon(Icons.Outlined.FormatSize, contentDescription = null, tint = tint, modifier = modifier)
  }
}

/**
 * The top-bar actions of the reader and the web view: the always and if-room items as icon
 * buttons in order, then the overflow menu with the other items in order.
 */
@Composable
fun RowScope.BrowsingTopBarActions(items: List<BrowsingMenuItem>, contentColour: Color, onItem: (BrowsingMenuItem) -> Unit) {
  items.filter { it.placement != MenuPlacement.Overflow }.forEach { item ->
    IconButton(onClick = { onItem(item) }, modifier = Modifier.testTag(menuTag(item))) {
      Box(Modifier.semantics { contentDescription = item.title }) { MenuItemIcon(item, contentColour) }
    }
  }
  val overflow = items.filter { it.placement == MenuPlacement.Overflow }
  if (overflow.isEmpty()) return
  var open by rememberSaveable { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }, modifier = Modifier.testTag("browsing-overflow")) {
      Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.overflow_menu_description), tint = contentColour)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.testTag("browsing-overflow-menu")) {
      overflow.forEach { item ->
        DropdownMenuItem(
          text = { Text(item.title) },
          onClick = {
            open = false
            onItem(item)
          },
          modifier = Modifier.heightIn(min = 48.dp).testTag(menuTag(item)),
        )
      }
    }
  }
}

/**
 * The bottom bar of the reader and the web view: labelled actions in one row in the old order, on
 * [containerColour] with [contentColour] (the Custom Tab bottom bar's icons and labels), above the
 * navigation bar. Each action is at least 48 dp tall.
 */
@Composable
fun BrowsingBottomBar(
  items: List<BrowsingMenuItem>,
  containerColour: Color,
  contentColour: Color,
  onItem: (BrowsingMenuItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (items.isEmpty()) return
  Surface(color = containerColour, contentColor = contentColour, modifier = modifier.fillMaxWidth().testTag("browsing-bottom-bar")) {
    Row(
      Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
      horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
      items.forEach { item ->
        Column(
          Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = item.title) { onItem(item) }
            .padding(vertical = 6.dp)
            .testTag(menuTag(item)),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          MenuItemIcon(item, contentColour)
          Text(
            item.title,
            style = MaterialTheme.typography.labelSmall,
            color = contentColour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/**
 * Bars that hide while the content scrolls down and return when it scrolls up (enter-always).
 * [offset] runs from 0 (shown) to -[limit] (hidden) in px; [fraction] is how far they are hidden.
 */
@Stable
class HideOnScrollState(initialOffset: Float = 0f) {
  var limit by mutableFloatStateOf(0f)
  var offset by mutableFloatStateOf(initialOffset)
    private set

  val fraction: Float get() = if (limit <= 0f) 0f else (-offset / limit).coerceIn(0f, 1f)

  /** Bumped by each scroll, so a settle in progress stops as soon as a finger moves the bars. */
  private var scrollGeneration = 0
  private val settleMutex = MutatorMutex()

  /** Applies a scroll by [dy] px (positive when the content moves up, that is scrolling down). */
  fun onScroll(dy: Float) {
    scrollGeneration++
    offset = (offset - dy).coerceIn(-limit, 0f)
  }

  /** Snaps fully shown or fully hidden at rest. */
  fun settle() {
    offset = if (fraction > 0.5f) -limit else 0f
  }

  fun show() {
    offset = 0f
  }

  /** Slides fully shown or fully hidden from where the finger left the bars, as the top bar does. */
  suspend fun animateSettle() = animateTo(if (fraction > 0.5f) -limit else 0f)

  /** Slides the bars fully into view. */
  suspend fun animateShow() = animateTo(0f)

  /**
   * Springs the bars to [target]. A new settle cancels the one running, and a scroll that starts
   * meanwhile stops it at once, so the finger and the animation never fight over [offset].
   */
  private suspend fun animateTo(target: Float) {
    if (offset == target) return
    settleMutex.mutate {
      val started = scrollGeneration
      try {
        animate(offset, target, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { value, _ ->
          if (scrollGeneration != started) throw SettleInterrupted()
          offset = value
        }
      } catch (_: SettleInterrupted) {
        // The finger took over; the bars stay where the scroll puts them.
      }
    }
  }

  /** Ends a settle when a scroll starts; caught in [animateTo], never seen by callers. */
  private class SettleInterrupted : CancellationException("A scroll took over the bars")

  /** Reads list scroll deltas without consuming them, and settles the bars when the list comes to rest. */
  val connection: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      onScroll(-available.y)
      return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      animateSettle()
      return Velocity.Zero
    }
  }

}

@Composable
fun rememberHideOnScrollState(): HideOnScrollState = remember { HideOnScrollState() }
