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

package arun.com.chromer.bubbles.webheads.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import timber.log.Timber

/**
 * One small overlay window that shows Compose content over other apps. Each web head and the
 * trash have their own window, so touches outside them reach the app below.
 */
class OverlayWindow(
  context: Context,
  owner: OverlayLifecycleOwner,
  touchable: Boolean,
  x: Int,
  y: Int,
  content: @Composable () -> Unit,
) {
  private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

  val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
      (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
    PixelFormat.TRANSLUCENT,
  ).apply {
    // Absolute screen coordinates, as the drag uses raw positions.
    gravity = Gravity.TOP or Gravity.LEFT
    this.x = x
    this.y = y
  }

  val view: ComposeView = ComposeView(context).apply {
    // Compose looks these three owners up from the view tree; without them it throws.
    setViewTreeLifecycleOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent(content)
  }

  var isAdded = false
    private set

  fun add() {
    if (isAdded) return
    try {
      windowManager.addView(view, params)
      isAdded = true
    } catch (e: RuntimeException) {
      // BadTokenException (overlay permission gone) or IllegalStateException (added twice).
      Timber.e(e, "Could not add overlay window")
    }
  }

  fun move(x: Int, y: Int) {
    if (params.x == x && params.y == y) return
    params.x = x
    params.y = y
    if (!isAdded) return
    try {
      windowManager.updateViewLayout(view, params)
    } catch (e: IllegalArgumentException) {
      Timber.e("Overlay window moved after it was removed")
    }
  }

  var visible: Boolean
    get() = view.visibility == View.VISIBLE
    set(value) {
      view.visibility = if (value) View.VISIBLE else View.GONE
    }

  fun remove() {
    if (!isAdded) return
    isAdded = false
    try {
      view.disposeComposition()
      windowManager.removeViewImmediate(view)
    } catch (e: IllegalArgumentException) {
      Timber.e("Overlay window removed twice")
    }
  }
}
