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
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Link-open start-up: a VIEW intent for an https URL sent to the browser intercept entry point,
 * measured until the app's first frame (the Custom Tab hand-off activity; the page itself draws in
 * the provider's process). Cold and warm runs use the same intent so later builds compare against
 * the same definition.
 */
@RunWith(AndroidJUnit4::class)
class LinkOpenStartupBenchmark {

  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun startupCold() = measure(StartupMode.COLD)

  @Test
  fun startupWarm() = measure(StartupMode.WARM)

  private fun measure(mode: StartupMode) = benchmarkRule.measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = listOf(StartupTimingMetric()),
    iterations = ITERATIONS,
    startupMode = mode,
    compilationMode = CompilationMode.DEFAULT,
    setupBlock = {
      // The app brings an existing tab for the same URL to the front instead of opening a new
      // one, and the Custom Tab lives in the provider's process. Stop the provider and use a new
      // URL per iteration so every iteration measures a fresh link open.
      device.executeShellCommand("am force-stop $PROVIDER_PACKAGE")
      pressHome()
    },
  ) {
    startActivityAndWait(linkIntent("$LINK?i=${linkCount++}"))
  }

  private var linkCount = 0

  private fun linkIntent(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
    setClassName(TARGET_PACKAGE, INTERCEPT_ACTIVITY)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }

  companion object {
    const val TARGET_PACKAGE = "arun.com.chromer.benchmark"
    const val INTERCEPT_ACTIVITY = "arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity"
    const val LINK = "https://example.com/"
    const val PROVIDER_PACKAGE = "com.android.chrome"
    const val ITERATIONS = 10
  }
}
