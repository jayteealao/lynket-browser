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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion.ScreenBounds
import arun.com.chromer.data.website.model.Website
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/** What a web head shows. The composable reads it; the controller writes it. */
class WebHeadState(val url: String, val fromAmp: Boolean = false, val incognito: Boolean = false) {
  var website by mutableStateOf(Website().apply { url = this@WebHeadState.url })
  var favicon by mutableStateOf<ImageBitmap?>(null)

  /** Colour of the website, when one was found; else the theme colour is used. */
  var colour by mutableStateOf<Color?>(null)
  var isMaster by mutableStateOf(false)
  var badgeCount by mutableIntStateOf(1)
  var badgeOnLeft by mutableStateOf(false)
  var lockedToTrash by mutableStateOf(false)
  var revealed by mutableStateOf(false)
  var shrinking by mutableStateOf(false)
  var closing by mutableStateOf(false)
  var queued by mutableStateOf(false)

  internal var destroyed = false
  internal var chainIndex = 0
  internal lateinit var x: Animatable<Float, AnimationVector1D>
  internal lateinit var y: Animatable<Float, AnimationVector1D>

  /** Current end value of a spring on each axis, or null while the axis decays or snaps. */
  internal var xTarget: Float? = null
  internal var yTarget: Float? = null

  val windowX: Int get() = x.value.toInt()
  val windowY: Int get() = y.value.toInt()
}

/** The trash target. */
class TrashState {
  /** 0 hidden, 0.9 shown, 1.0 grown under a dragged head. */
  var scale by mutableFloatStateOf(0f)
  var locked by mutableStateOf(false)
  var exiting by mutableStateOf(false)
}

/** What the controller needs from the overlay: windows and the service actions. */
interface WebHeadHost {
  fun addHeadWindow(head: WebHeadState, x: Int, y: Int)
  fun moveHeadWindow(head: WebHeadState, x: Int, y: Int)
  fun setHeadWindowVisible(head: WebHeadState, visible: Boolean)
  fun removeHeadWindow(head: WebHeadState)
  fun onHeadClick(head: WebHeadState)
  fun onHeadsLongPress()
  fun onHeadDestroyed(head: WebHeadState, isLastHead: Boolean)
}

/**
 * Owns the web heads and moves them: drag, fling, edge snap, trash lock and close, the follower
 * chain, the queue beyond five heads and the coasting of the master. The rules follow the earlier
 * View web heads step for step. Every position change moves a window and recomposes nothing.
 *
 * [scope] runs on the main thread with a frame clock (the overlay uses AndroidUiDispatcher.Main).
 */
class WebHeadsController(
  private val host: WebHeadHost,
  private val scope: CoroutineScope,
  dispWidth: Int,
  dispHeight: Int,
  private val density: Float,
  screenWidthDp: Int,
  /** Width of a head window in px. */
  val headWindowPx: Int,
  /** Width of the trash window in px. */
  val trashWindowPx: Int,
  private val touchSlop: Float,
  private val minFlingVelocity: Float,
  private val spawnOnRight: Boolean,
  private val closeOnOpen: () -> Boolean,
) {
  /** Oldest first; the last one is the master. */
  private val heads = LinkedHashMap<String, WebHeadState>()
  val trash = TrashState()

  var dispWidth = dispWidth
    private set
  var dispHeight = dispHeight
    private set
  private var minHorizontalFlingVelocity = WebHeadMotion.minHorizontalFlingVelocity(screenWidthDp, density)
  var bounds: ScreenBounds = ScreenBounds.of(dispWidth, dispHeight, headWindowPx, density)
    private set

  private var lastMasterX = 0
  private var lastMasterY = 0
  private var masterDownX = 0
  private var masterDownY = 0

  private var chain: List<WebHeadState> = emptyList()
  private var displacementEnabled = true

  // Gesture state of the master.
  private var dragging = false
  private var userMovedHead = false
  private var initialDownX = 0
  private var initialDownY = 0
  private var downRawX = 0f
  private var downRawY = 0f
  private val recentMoves = ArrayDeque<Pair<Float, Float>>()
  private var coasting = false
  private var coastingJob: Job? = null

  val all: List<WebHeadState> get() = heads.values.toList()
  val master: WebHeadState? get() = heads.values.lastOrNull()
  val size: Int get() = heads.size
  val isEmpty: Boolean get() = heads.isEmpty()

  operator fun get(url: String): WebHeadState? = heads[url]

  operator fun contains(url: String): Boolean = heads.containsKey(url)

  /** Trash window position (top left) and centre. */
  val trashX: Int get() = dispWidth / 2 - trashWindowPx / 2
  val trashY: Int get() = dispHeight - dispHeight / 6 - trashWindowPx / 2
  private val trashCentreX: Int get() = trashX + trashWindowPx / 2
  private val trashCentreY: Int get() = trashY + trashWindowPx / 2
  private val trashLockX: Int get() = trashCentreX - headWindowPx / 2
  private val trashLockY: Int get() = trashCentreY - headWindowPx / 2

  fun updateDisplay(width: Int, height: Int, screenWidthDp: Int) {
    dispWidth = width
    dispHeight = height
    minHorizontalFlingVelocity = WebHeadMotion.minHorizontalFlingVelocity(screenWidthDp, density)
    bounds = ScreenBounds.of(width, height, headWindowPx, density)
    lastMasterX = 0
    lastMasterY = 0
  }

  /** Adds a head for [url] as the new master. */
  fun add(url: String, fromAmp: Boolean = false, incognito: Boolean = false): WebHeadState {
    heads.values.forEach { it.isMaster = false }
    val head = WebHeadState(url, fromAmp, incognito)
    val spawnX: Int
    val spawnY: Int
    if (lastMasterX != 0 || lastMasterY != 0) {
      spawnX = lastMasterX
      spawnY = lastMasterY
    } else {
      spawnX = if (spawnOnRight) bounds.right else bounds.left
      spawnY = dispHeight / 3
    }
    head.x = Animatable(spawnX.toFloat())
    head.y = Animatable(spawnY.toFloat())
    heads[url] = head
    head.isMaster = true
    coasting = false
    host.addHeadWindow(head, spawnX, spawnY)
    head.revealed = true
    updateBadges()
    updateChain()
    onMasterMoved(head)
    scheduleCoasting()
    return head
  }

  // region Gestures of the master

  fun onTouchDown(head: WebHeadState, rawX: Float, rawY: Float) {
    if (!acceptsTouch(head)) return
    dragging = false
    recentMoves.clear()
    initialDownX = head.windowX
    initialDownY = head.windowY
    downRawX = rawX
    downRawY = rawY
    masterDownX = rawX.toInt()
    masterDownY = rawY.toInt()
    cancelCoasting()
  }

  fun onTouchMove(head: WebHeadState, rawX: Float, rawY: Float) {
    if (!acceptsTouch(head)) return
    recentMoves.addLast(rawX to rawY)
    while (recentMoves.size > 10) recentMoves.removeFirst()
    val offsetX = rawX - downRawX
    val offsetY = rawY - downRawY
    if (hypot(offsetX, offsetY) > touchSlop) dragging = true
    if (!dragging) return

    revealTrash()
    userMovedHead = true
    val x = (initialDownX + offsetX).toInt()
    val y = (initialDownY + offsetY).toInt()
    if (isNearTrash(head, x, y)) {
      trash.scale = 1f
      trash.locked = true
      springTo(head, trashLockX.toFloat(), trashLockY.toFloat(), WebHeadMotion.Snap)
    } else {
      trash.locked = false
      if (trash.scale > 0f) trash.scale = 0.9f
      snapTo(head, x.toFloat(), y.toFloat())
    }
  }

  /**
   * Finger lifted. [velocityX] and [velocityY] are in px per second; [upRawX] and [upRawY] are the
   * raw screen position of the last event.
   */
  fun onTouchUp(head: WebHeadState, velocityX: Float, velocityY: Float, upRawX: Float, upRawY: Float) {
    if (!acceptsTouch(head)) return
    var flung = false
    if (dragging && (abs(velocityX) > minFlingVelocity || abs(velocityY) > minFlingVelocity)) {
      val (fromX, fromY) = if (recentMoves.size >= 2) recentMoves[recentMoves.size - 2] else downRawX to downRawY
      val (toX, toY) = recentMoves.lastOrNull() ?: (upRawX to upRawY)
      val (vx, vy) = WebHeadMotion.adjustVelocities(fromX, fromY, toX, toY, velocityX, velocityY)
      val flingX = WebHeadMotion.interpolateXVelocity(vx, upRawX, dispWidth, minHorizontalFlingVelocity)
      flung = true
      dragging = false
      decay(head, flingX, vy)
    }
    if (head.lockedToTrash) {
      destroy(head, receiveCallback = true)
      return
    }
    dragging = false
    recentMoves.clear()
    if (!flung && userMovedHead) stickToWall(head)
    hideTrash()
    scheduleCoasting()
  }

  fun onTap(head: WebHeadState) {
    if (!acceptsTouch(head)) return
    if (closeOnOpen()) {
      head.shrinking = true
      masterDownX = head.windowX
      masterDownY = head.windowY
      scope.launch {
        delay(WebHeadMotion.CLOSE_ON_OPEN_MS)
        hideTrash()
        host.onHeadClick(head)
      }
    } else {
      hideTrash()
      host.onHeadClick(head)
    }
  }

  fun onLongPress(head: WebHeadState) {
    if (!acceptsTouch(head)) return
    host.onHeadsLongPress()
  }

  private fun acceptsTouch(head: WebHeadState) = !head.destroyed && head.isMaster && !head.queued

  // endregion

  // region Motion

  private fun snapTo(head: WebHeadState, x: Float, y: Float) {
    head.xTarget = null
    head.yTarget = null
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      head.x.snapTo(x)
      head.y.snapTo(y)
      onFrame(head)
    }
  }

  private fun springTo(head: WebHeadState, x: Float?, y: Float?, spec: androidx.compose.animation.core.SpringSpec<Float>) {
    if (x != null && head.xTarget != x) {
      head.xTarget = x
      scope.launch { head.x.animateTo(x, spec) { onFrame(head) } }
    }
    if (y != null && head.yTarget != y) {
      head.yTarget = y
      scope.launch { head.y.animateTo(y, spec) { onFrame(head) } }
    }
  }

  private fun decay(head: WebHeadState, velocityX: Float, velocityY: Float) {
    head.xTarget = null
    head.yTarget = null
    scope.launch { head.x.animateDecay(velocityX, WebHeadMotion.FlingDecay) { onFrame(head) } }
    scope.launch { head.y.animateDecay(velocityY, WebHeadMotion.FlingDecay) { onFrame(head) } }
  }

  private fun onFrame(head: WebHeadState) {
    if (head.destroyed) return
    val x = head.windowX
    val y = head.windowY
    host.moveHeadWindow(head, x, y)
    if (head.isMaster) {
      lastMasterX = x
      lastMasterY = y
      onMasterMoved(head)
      checkBounds(head)
      head.badgeOnLeft = x > dispWidth / 2
    }
  }

  /** Pulls a released master back inside the screen, as the earlier physics did. */
  private fun checkBounds(head: WebHeadState) {
    if (dragging || !head.isMaster || head.queued || coasting) return
    val x = head.windowX
    val y = head.windowY
    val width = headWindowPx
    var targetX: Float? = null
    var targetY: Float? = null
    if (x + width >= dispWidth) targetX = bounds.right.toFloat()
    if (x - width <= 0) targetX = bounds.left.toFloat()
    if (y + width >= dispHeight) targetY = bounds.bottom.toFloat()
    if (y - width <= 0) targetY = bounds.top.toFloat()
    springTo(head, targetX, targetY, WebHeadMotion.Fling)
  }

  private fun stickToWall(head: WebHeadState) {
    val x = head.windowX
    val y = head.windowY
    val targetX = if (x > dispWidth / 2) bounds.right.toFloat() else bounds.left.toFloat()
    val targetY = when {
      y < bounds.top -> bounds.top.toFloat()
      y > bounds.bottom -> bounds.bottom.toFloat()
      else -> null
    }
    springTo(head, targetX, targetY, WebHeadMotion.Fling)
  }

  private fun onMasterMoved(master: WebHeadState) {
    val onRight = master.windowX > dispWidth / 2
    chain.asReversed().forEachIndexed { i, follower ->
      val (dx, dy) = if (displacementEnabled) WebHeadMotion.chainOffset(i + 1, onRight, density) else 0 to 0
      springTo(follower, (master.windowX + dx).toFloat(), (master.windowY + dy).toFloat(), WebHeadMotion.followerSpring(follower.chainIndex))
    }
  }

  /** Followers and queue, as the earlier spring chain: oldest heads beyond five are hidden. */
  private fun updateChain() {
    chain.forEach { follower ->
      follower.xTarget = null
      follower.yTarget = null
      scope.launch { follower.x.stop(); follower.y.stop() }
    }
    val followers = ArrayList<WebHeadState>()
    var chainIndex = heads.size
    var index = heads.size
    for (head in heads.values) {
      if (!head.isMaster) {
        if (WebHeadMotion.isQueued(index)) {
          if (!head.queued) {
            head.queued = true
            host.setHeadWindowVisible(head, false)
          }
        } else {
          if (head.queued) {
            head.queued = false
            host.setHeadWindowVisible(head, true)
          }
          head.chainIndex = chainIndex
          followers.add(head)
        }
        chainIndex--
      } else if (head.queued) {
        head.queued = false
        host.setHeadWindowVisible(head, true)
      }
      index--
    }
    chain = followers
    displacementEnabled = true
  }

  private fun updateBadges() {
    val count = heads.size
    heads.values.forEach { it.badgeCount = count }
  }

  private fun isNearTrash(head: WebHeadState, x: Int, y: Int): Boolean {
    val offset = headWindowPx / 2
    val distance = hypot((trashCentreX - (x + offset)).toFloat(), (trashCentreY - (y + offset)).toFloat())
    val near = distance < WebHeadMotion.MAGNETISM_DP * density
    head.lockedToTrash = near
    displacementEnabled = !near
    return near
  }

  private fun scheduleCoasting() {
    val master = master ?: return
    cancelCoasting()
    coastingJob = scope.launch {
      delay(WebHeadMotion.COASTING_DELAY_MS)
      coasting = true
      val quarter = headWindowPx / 4
      val target = if (master.windowX < dispWidth / 2) bounds.left - quarter else bounds.right + quarter
      springTo(master, target.toFloat(), null, WebHeadMotion.Fling)
    }
  }

  private fun cancelCoasting() {
    coasting = false
    coastingJob?.cancel()
    coastingJob = null
  }

  // endregion

  // region Trash

  private fun revealTrash() {
    trash.exiting = false
    if (trash.scale == 0f) trash.scale = 0.9f
  }

  fun hideTrash() {
    trash.locked = false
    trash.scale = 0f
  }

  // endregion

  // region Closing

  /** Closes the head for [url], as the context sheet and close-on-open do. */
  fun close(url: String) {
    heads[url]?.let { destroy(it, receiveCallback = true) }
  }

  /**
   * Closes [head]. On the trash it turns into a red X first; elsewhere it leaves at once. With
   * [receiveCallback] the host learns about it and the next head becomes the master.
   */
  fun destroy(head: WebHeadState, receiveCallback: Boolean) {
    if (head.destroyed) return
    if (head.isMaster) cancelCoasting()
    head.destroyed = true
    head.xTarget = null
    head.yTarget = null
    scope.launch { head.x.stop(); head.y.stop() }
    if (isAtTrash(head)) {
      head.closing = true
      scope.launch {
        delay(WebHeadMotion.TRASH_CLOSE_MS)
        finishDestroy(head, receiveCallback)
      }
    } else {
      finishDestroy(head, receiveCallback)
    }
  }

  private fun isAtTrash(head: WebHeadState): Boolean {
    val distance = hypot((head.windowX - trashLockX).toFloat(), (head.windowY - trashLockY).toFloat())
    if (distance < 15 * density) {
      host.moveHeadWindow(head, trashLockX, trashLockY)
      return true
    }
    return false
  }

  private fun finishDestroy(head: WebHeadState, receiveCallback: Boolean) {
    val wasMaster = head.isMaster
    head.isMaster = false
    heads.remove(head.url)
    host.removeHeadWindow(head)
    hideTrash()
    updateBadges()
    if (!receiveCallback) return
    val last = heads.isEmpty()
    host.onHeadDestroyed(head, last)
    if (!last) {
      if (wasMaster) selectNextMaster() else updateChain()
    }
  }

  private fun selectNextMaster() {
    val next = master ?: return
    next.isMaster = true
    next.lockedToTrash = false
    coasting = false
    updateChain()
    springTo(next, masterDownX.toFloat(), masterDownY.toFloat(), WebHeadMotion.Fling)
  }

  /** Scales the trash out; [onEnd] runs when it is gone. */
  fun exitTrash(onEnd: () -> Unit) {
    trash.exiting = true
    trash.scale = 0f
    scope.launch {
      delay(WebHeadMotion.TRASH_EXIT_MS)
      onEnd()
    }
  }

  /** Removes every head without callbacks (service stop). */
  fun removeAll() {
    cancelCoasting()
    heads.values.forEach { head ->
      head.destroyed = true
      host.removeHeadWindow(head)
    }
    heads.clear()
    chain = emptyList()
    lastMasterX = 0
    lastMasterY = 0
  }

  // endregion
}
