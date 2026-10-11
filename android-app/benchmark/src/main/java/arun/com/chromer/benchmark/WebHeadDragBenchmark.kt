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

package arun.com.chromer.benchmark

import android.content.Intent
import android.graphics.Point
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Web head drag smoothness: three web heads are opened, then the front head is dragged along a
 * fixed path from its spawn point to the opposite screen edge. Frame times of the overlay are
 * recorded for every iteration.
 *
 * Web heads are switched on through the app's Quick Settings tile (the same toggle a person uses),
 * and the overlay permission is granted with appops, so no test hook is needed in the app.
 */
@RunWith(AndroidJUnit4::class)
class WebHeadDragBenchmark {

  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  private val shell: UiDevice
    get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

  /**
   * A web head rests partly off screen at a side edge, inside the zone where gesture navigation
   * reads a swipe as Back. Three-button navigation is used for the run, so the drag reaches the
   * head; the gesture mode is restored afterwards.
   */
  @Before
  fun useButtonNavigation() {
    shell.executeShellCommand("cmd overlay enable-exclusive --category $NAV_THREE_BUTTON")
    Thread.sleep(TILE_BIND_MS)
  }

  @After
  fun restoreGestureNavigation() {
    shell.executeShellCommand("cmd overlay enable-exclusive --category $NAV_GESTURAL")
  }

  @Test
  fun dragFrontHead() = benchmarkRule.measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = listOf(FrameTimingMetric()),
    iterations = ITERATIONS,
    compilationMode = CompilationMode.DEFAULT,
    setupBlock = { openThreeWebHeads() },
  ) {
    val width = device.displayWidth
    val height = device.displayHeight
    // The front head is found first, so the swipe always starts on the head itself: the head
    // spawns a third of the screen down and rests partly off screen at a side edge.
    val start = frontHeadCentre(width, height) ?: error("No web head on screen to drag")
    val endX = if (start.x > width / 2) START_X else width - START_X
    // Fixed path: from the head across to the opposite edge, 40 steps (~200 ms).
    device.swipe(start.x, start.y, endX, (height * END_Y_FRACTION).toInt(), SWIPE_STEPS)
    device.waitForIdle(IDLE_MS)
    Thread.sleep(IDLE_MS)
  }

  /**
   * Centre of the visible part of the front web head window. Overlay windows of another app are
   * not in the accessibility tree UiAutomator reads, so the window list of the window manager is
   * read instead. It lists windows from the top down, so the front head is the first touchable
   * overlay window of the app.
   */
  private fun MacrobenchmarkScope.frontHeadCentre(width: Int, height: Int): Point? {
    val deadline = System.currentTimeMillis() + HEAD_WAIT_MS
    while (System.currentTimeMillis() < deadline) {
      var front: Point? = null
      device.executeShellCommand("dumpsys window windows")
        .split(WINDOW_SPLIT)
        .filter { it.contains("u0 $TARGET_PACKAGE}") && it.contains("ty=APPLICATION_OVERLAY") && !it.contains("NOT_TOUCHABLE") }
        .forEach { window ->
          FRAME.find(window)?.destructured?.let { (l, t, r, b) ->
            val left = maxOf(l.toInt(), 0)
            val right = minOf(r.toInt(), width)
            val top = maxOf(t.toInt(), 0)
            val bottom = minOf(b.toInt(), height)
            if (front == null && right > left && bottom > top) front = Point((left + right) / 2, (top + bottom) / 2)
          }
        }
      if (front != null) return front
      Thread.sleep(250)
    }
    return null
  }

  private fun MacrobenchmarkScope.openThreeWebHeads() {
    killProcess()
    device.executeShellCommand("pm clear $TARGET_PACKAGE")
    device.executeShellCommand("appops set $TARGET_PACKAGE SYSTEM_ALERT_WINDOW allow")
    // A tile left from an earlier install is not bound again after a reinstall: remove and add it.
    device.executeShellCommand("cmd statusbar remove-tile $TARGET_PACKAGE/$WEB_HEAD_TILE")
    Thread.sleep(TILE_BIND_MS)
    device.executeShellCommand("cmd statusbar add-tile $TARGET_PACKAGE/$WEB_HEAD_TILE")
    // The tile toggles the mode, and a click can arrive before the tile service is bound. A link
    // that opens a browsing tab instead of a head shows the mode is still off: click again.
    var attempt = 0
    while (true) {
      Thread.sleep(TILE_BIND_MS)
      device.executeShellCommand("cmd statusbar click-tile $TARGET_PACKAGE/$WEB_HEAD_TILE")
      Thread.sleep(TILE_BIND_MS)
      device.executeShellCommand("cmd statusbar collapse")
      device.pressHome()
      openLink(1)
      if (frontHeadCentre(device.displayWidth, device.displayHeight) != null) break
      attempt++
      check(attempt < TILE_ATTEMPTS) { "Web heads did not turn on with the tile" }
      killProcess()
      device.pressHome()
    }
    for (i in 2..3) openLink(i)
    device.pressHome()
    device.waitForIdle(IDLE_MS)
  }

  private fun MacrobenchmarkScope.openLink(i: Int) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/head-$i")).apply {
      setClassName(TARGET_PACKAGE, INTERCEPT_ACTIVITY)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivityAndWait(intent)
    device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), HEAD_WAIT_MS)
    Thread.sleep(SPAWN_SETTLE_MS)
  }

  companion object {
    const val TARGET_PACKAGE = "arun.com.chromer.benchmark"
    const val INTERCEPT_ACTIVITY = "arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity"
    const val WEB_HEAD_TILE = "arun.com.chromer.bubbles.webheads.qs.WebHeadTile"
    const val ITERATIONS = 10
    const val START_X = 60
    private val WINDOW_SPLIT = Regex("\n  Window #")
    private val FRAME = Regex(""" frame=\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")
    const val END_Y_FRACTION = 0.5f
    const val SWIPE_STEPS = 40
    const val HEAD_WAIT_MS = 5_000L
    const val SPAWN_SETTLE_MS = 1_500L
    const val IDLE_MS = 1_000L
    const val TILE_BIND_MS = 2_000L
    const val TILE_ATTEMPTS = 3
    const val NAV_THREE_BUTTON = "com.android.internal.systemui.navbar.threebutton"
    const val NAV_GESTURAL = "com.android.internal.systemui.navbar.gestural"
  }
}
