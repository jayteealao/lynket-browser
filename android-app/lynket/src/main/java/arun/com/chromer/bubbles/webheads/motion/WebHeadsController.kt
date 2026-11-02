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
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion.StackAnchor
import arun.com.chromer.data.website.model.Website
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
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

  /** Place in the stack that screen readers say, counted from the front: the newest head is 1 and the oldest is [badgeCount]. */
  var stackPosition by mutableIntStateOf(1)
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

  /** How many times a head has locked onto the trash: it grows by one on each lock, never while a head stays locked. */
  var lockCount by mutableIntStateOf(0)
    internal set
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

  /** The trash appeared under a drag or went away; screen readers see it only while it shows. */
  fun onTrashShown(shown: Boolean)

  /** A haptic tick on [head]'s window; [feedbackConstant] is one of `android.view.HapticFeedbackConstants`. */
  fun performHaptic(head: WebHeadState, feedbackConstant: Int)
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

  /**
   * Where the stack rests, as a screen edge and a share of the height, so a new head spawns there
   * after a rotation, a fold or a resize. Null until the first head and after every head is gone.
   */
  var anchor: StackAnchor? = null
    private set

  /**
   * True after a display change until a touch or a new head moves the master: its window still
   * has the pixels of the old display, so its frames must not move the anchor.
   */
  private var anchorHeld = false
  private var masterDownX = 0
  private var masterDownY = 0

  private var chain: List<WebHeadState> = emptyList()
  private var displacementEnabled = true

  // Gesture state of the master.
  private var dragging = false

  /**
   * The finger on the master and a display change that waits for it, as one state. [holdFinger],
   * [holdResnapForFinger] and [releaseFinger] are its only transitions.
   */
  private enum class FingerHold {
    /** No finger on the master. */
    NONE,

    /** A finger holds the master. */
    HELD,

    /** A finger holds the master and the display changed under it: the heads move after the release. */
    HELD_RESNAP_AFTER,
  }

  private var fingerHold = FingerHold.NONE

  /** A finger went down on the master; [interruptedResnap] when it stopped a running re-snap. */
  private fun holdFinger(interruptedResnap: Boolean) {
    fingerHold = if (interruptedResnap || fingerHold == FingerHold.HELD_RESNAP_AFTER) FingerHold.HELD_RESNAP_AFTER else FingerHold.HELD
  }

  /** The display changed: true when a finger holds the master and the re-snap must wait for it. */
  private fun holdResnapForFinger(): Boolean {
    if (fingerHold == FingerHold.NONE) return false
    fingerHold = FingerHold.HELD_RESNAP_AFTER
    return true
  }

  /** The master the finger held stopped leading or closed, so its release will not reach this controller. */
  private fun forgetFinger() {
    releaseFinger()
    dragging = false
    recentMoves.clear()
  }

  /** The finger is gone; returns whether a re-snap waited for it. */
  private fun releaseFinger(): Boolean {
    val resnapAfter = fingerHold == FingerHold.HELD_RESNAP_AFTER
    fingerHold = FingerHold.NONE
    return resnapAfter
  }
  private var userMovedHead = false
  private var initialDownX = 0
  private var initialDownY = 0
  private var downRawX = 0f
  private var downRawY = 0f
  private val recentMoves = ArrayDeque<Pair<Float, Float>>()
  private var coasting = false
  private var coastingJob: Job? = null

  /** The move of the heads after a display change, while it runs. */
  private var resnapJob: Job? = null

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

  /**
   * The display changed: a rotation, a fold, a resize, or another configuration change. When the
   * size changed, the open heads move to the stack's place in the new bounds.
   */
  fun updateDisplay(width: Int, height: Int, screenWidthDp: Int) {
    minHorizontalFlingVelocity = WebHeadMotion.minHorizontalFlingVelocity(screenWidthDp, density)
    // A theme, locale or font change keeps the size: the heads stay where they are.
    if (width == dispWidth && height == dispHeight) return
    dispWidth = width
    dispHeight = height
    bounds = ScreenBounds.of(width, height, headWindowPx, density)
    val anchor = anchor ?: return
    anchorHeld = true
    // A held head stays under the finger; the heads move when it lifts.
    if (!holdResnapForFinger()) resnap(anchor)
  }

  /**
   * Moves every open head to [target] in the current bounds with a spring that does not bounce:
   * the front head to the stack's edge and height, each follower behind it, and each queued head
   * (hidden) straight there. With animations off the spring ends at once. A touch, a new head or a
   * close stops it.
   */
  private fun resnap(target: StackAnchor) {
    cancelCoasting()
    resnapJob?.cancel()
    val visible = heads.values.filter { !it.destroyed && !it.queued }
    val leader = visible.lastOrNull() ?: return
    anchor = target
    anchorHeld = true
    val (mx, my) = target.position(bounds)
    val onRight = target.side == StackAnchor.Side.RIGHT
    heads.values.filter { !it.destroyed && it.queued }.forEach { snapTo(it, mx.toFloat(), my.toFloat()) }
    visible.dropLast(1).asReversed().forEachIndexed { i, follower ->
      val (x, y) = followerTarget(i, mx, my, onRight)
      springTo(follower, x.toFloat(), y.toFloat(), WebHeadMotion.Resnap)
    }
    leader.xTarget = mx.toFloat()
    leader.yTarget = my.toFloat()
    resnapJob = scope.launch {
      val moveX = launch { leader.x.animateTo(mx.toFloat(), WebHeadMotion.Resnap) { onFrame(leader) } }
      val moveY = launch { leader.y.animateTo(my.toFloat(), WebHeadMotion.Resnap) { onFrame(leader) } }
      joinAll(moveX, moveY)
      // A drag, a fling or a correction that took over an axis ends the re-snap without this.
      if (moveX.isCancelled || moveY.isCancelled || !leader.isMaster || leader.destroyed) return@launch
      // The front head has the new display's pixels again, so its frames may move the anchor.
      anchorHeld = false
      scheduleCoasting()
    }
  }

  private val resnapping: Boolean get() = resnapJob?.isActive == true

  /** Adds a head for [url] as the new master. */
  fun add(url: String, fromAmp: Boolean = false, incognito: Boolean = false): WebHeadState {
    resnapJob?.cancel()
    // A finger on the old master no longer holds the stack: its release goes to a follower.
    forgetFinger()
    heads.values.forEach { it.isMaster = false }
    val head = WebHeadState(url, fromAmp, incognito)
    val spawnX: Int
    val spawnY: Int
    val anchored = anchor?.position(bounds)
    if (anchored != null) {
      spawnX = anchored.first
      spawnY = anchored.second
    } else {
      spawnX = if (spawnOnRight) bounds.right else bounds.left
      spawnY = dispHeight / 3
    }
    anchor = StackAnchor.of(spawnX, spawnY, bounds, dispWidth)
    anchorHeld = false
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
    anchorHeld = false
    // The touch takes the head where it is; the stack finds its place again after the release.
    val interruptedResnap = resnapping
    if (interruptedResnap) resnapJob?.cancel()
    holdFinger(interruptedResnap)
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
      if (setTrashLocked(true)) host.performHaptic(head, WebHeadMotion.trashLockHaptic())
      springTo(head, trashLockX.toFloat(), trashLockY.toFloat(), WebHeadMotion.Snap)
    } else {
      if (setTrashLocked(false)) host.performHaptic(head, WebHeadMotion.TRASH_LEAVE_HAPTIC)
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
    val resnapAfterRelease = releaseFinger()
    val dragged = dragging
    anchorHeld = false
    var flung = false
    if (dragging && (abs(velocityX) > minFlingVelocity || abs(velocityY) > minFlingVelocity)) {
      val (fromX, fromY) = if (recentMoves.size >= 2) recentMoves[recentMoves.size - 2] else downRawX to downRawY
      val (toX, toY) = recentMoves.lastOrNull() ?: (upRawX to upRawY)
      val (vx, vy) = WebHeadMotion.adjustVelocities(fromX, fromY, toX, toY, velocityX, velocityY)
      val flingX = WebHeadMotion.interpolateXVelocity(vx, upRawX, dispWidth, minHorizontalFlingVelocity)
      flung = true
      dragging = false
      if (WebHeadMotion.flingReachesEdge(head.x.value, flingX, headWindowPx, dispWidth)) {
        decay(head, flingX, vy)
      } else {
        flingToEdge(head, flingX, vy)
      }
    }
    if (head.lockedToTrash) {
      // The trash plays its close but no longer takes touches: nothing is held over it now.
      stopTrashTouches()
      destroy(head, receiveCallback = true)
      return
    }
    dragging = false
    recentMoves.clear()
    if (resnapAfterRelease) {
      // The display changed during this touch. A fling ends at an edge of the new bounds by itself;
      // a drag leaves the stack on the edge where the head was let go; a tap returns it to its place.
      if (!flung) {
        val here = StackAnchor.of(head.windowX, head.windowY, bounds, dispWidth)
        resnap(if (dragged) here else anchor ?: here)
      }
    } else if (!flung && userMovedHead) {
      stickToWall(head)
    }
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

  /**
   * Carries a fling that would slow to a stop before its edge, as on a wide window, to the edge it
   * points to: the release speed hands over to a spring that does not bounce, and the height
   * decays as in any fling.
   */
  private fun flingToEdge(head: WebHeadState, velocityX: Float, velocityY: Float) {
    val edge = (if (velocityX > 0) bounds.right else bounds.left).toFloat()
    // A bound correction to the same edge then leaves this spring running.
    head.xTarget = edge
    head.yTarget = null
    scope.launch { head.x.animateTo(edge, WebHeadMotion.FlingToEdge, initialVelocity = velocityX) { onFrame(head) } }
    scope.launch { head.y.animateDecay(velocityY, WebHeadMotion.FlingDecay) { onFrame(head) } }
  }

  private fun onFrame(head: WebHeadState) {
    if (head.destroyed) return
    val x = head.windowX
    val y = head.windowY
    host.moveHeadWindow(head, x, y)
    if (head.isMaster) {
      if (!anchorHeld) anchor = StackAnchor.of(x, y, bounds, dispWidth)
      onMasterMoved(head)
      checkBounds(head)
      head.badgeOnLeft = x > dispWidth / 2
    }
  }

  /** Pulls a released master back inside the screen, as the earlier physics did. */
  private fun checkBounds(head: WebHeadState) {
    if (dragging || !head.isMaster || head.queued || coasting || resnapping) return
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
      val (x, y) = followerTarget(i, master.windowX, master.windowY, onRight)
      springTo(follower, x.toFloat(), y.toFloat(), WebHeadMotion.followerSpring(follower.chainIndex))
    }
  }

  /** Where follower [i] (0 is right behind the master) rests behind a master at ([masterX], [masterY]). */
  private fun followerTarget(i: Int, masterX: Int, masterY: Int, onRight: Boolean): Pair<Int, Int> {
    val (dx, dy) = if (displacementEnabled) WebHeadMotion.chainOffset(i + 1, onRight, density) else 0 to 0
    return masterX + dx to masterY + dy
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
    heads.values.reversed().forEachIndexed { index, head ->
      head.badgeCount = count
      head.stackPosition = index + 1
    }
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

  /** Whether the host last heard that the trash shows. */
  private var trashShown = false

  private fun revealTrash() {
    trash.exiting = false
    if (trash.scale == 0f) trash.scale = 0.9f
    updateTrashShown()
  }

  fun hideTrash() {
    setTrashLocked(false)
    trash.scale = 0f
    updateTrashShown()
  }

  /**
   * Writes the lock only when it changes, so each lock counts once and a held head never repeats it.
   * Returns whether it changed.
   */
  private fun setTrashLocked(locked: Boolean): Boolean {
    if (trash.locked == locked) return false
    trash.locked = locked
    if (locked) trash.lockCount++
    return true
  }

  /** Tells the host the trash is done with touches while it still draws, as when a head closes on it. */
  private fun stopTrashTouches() {
    if (!trashShown) return
    trashShown = false
    host.onTrashShown(false)
  }

  /** Tells the host when the trash starts or stops showing, once per change. */
  private fun updateTrashShown() {
    val shown = trash.scale > 0f
    if (shown == trashShown) return
    trashShown = shown
    host.onTrashShown(shown)
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
    if (head.isMaster) {
      cancelCoasting()
      resnapJob?.cancel()
      // The held master is gone, so its release never comes.
      forgetFinger()
    }
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
    updateTrashShown()
    scope.launch {
      delay(WebHeadMotion.TRASH_EXIT_MS)
      onEnd()
    }
  }

  /** Removes every head without callbacks (service stop). */
  fun removeAll() {
    cancelCoasting()
    resnapJob?.cancel()
    heads.values.forEach { head ->
      head.destroyed = true
      host.removeHeadWindow(head)
    }
    heads.clear()
    chain = emptyList()
    releaseFinger()
    anchor = null
    anchorHeld = false
  }

  // endregion
}
