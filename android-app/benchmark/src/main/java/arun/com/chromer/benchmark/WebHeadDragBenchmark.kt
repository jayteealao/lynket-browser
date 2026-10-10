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
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
    // Fixed path: from the spawn edge (top start) across to the opposite edge, 40 steps (~200 ms).
    device.swipe(START_X, (height * START_Y_FRACTION).toInt(), width - START_X, (height * END_Y_FRACTION).toInt(), SWIPE_STEPS)
    device.waitForIdle(IDLE_MS)
    Thread.sleep(IDLE_MS)
  }

  private fun MacrobenchmarkScope.openThreeWebHeads() {
    killProcess()
    device.executeShellCommand("pm clear $TARGET_PACKAGE")
    device.executeShellCommand("appops set $TARGET_PACKAGE SYSTEM_ALERT_WINDOW allow")
    device.executeShellCommand("cmd statusbar add-tile $TARGET_PACKAGE/$WEB_HEAD_TILE")
    // The tile service must be bound before a click reaches it.
    Thread.sleep(TILE_BIND_MS)
    device.executeShellCommand("cmd statusbar click-tile $TARGET_PACKAGE/$WEB_HEAD_TILE")
    Thread.sleep(TILE_BIND_MS)
    device.executeShellCommand("cmd statusbar collapse")
    device.pressHome()
    for (i in 1..3) {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/head-$i")).apply {
        setClassName(TARGET_PACKAGE, INTERCEPT_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      startActivityAndWait(intent)
      device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), HEAD_WAIT_MS)
      Thread.sleep(SPAWN_SETTLE_MS)
    }
    device.pressHome()
    device.waitForIdle(IDLE_MS)
  }

  companion object {
    const val TARGET_PACKAGE = "arun.com.chromer.benchmark"
    const val INTERCEPT_ACTIVITY = "arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity"
    const val WEB_HEAD_TILE = "arun.com.chromer.bubbles.webheads.qs.WebHeadTile"
    const val ITERATIONS = 10
    const val START_X = 60
    const val START_Y_FRACTION = 0.25f
    const val END_Y_FRACTION = 0.5f
    const val SWIPE_STEPS = 40
    const val HEAD_WAIT_MS = 5_000L
    const val SPAWN_SETTLE_MS = 1_500L
    const val IDLE_MS = 1_000L
    const val TILE_BIND_MS = 2_000L
  }
}
