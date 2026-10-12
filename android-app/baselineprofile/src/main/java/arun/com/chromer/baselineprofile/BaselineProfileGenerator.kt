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

package arun.com.chromer.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the Baseline Profile the app ships. Two journeys:
 *
 * 1. A link from another app: a VIEW intent for an https address sent to the browser intercept
 *    activity, the same intent the start-up benchmark measures, until the Custom Tab hand-off.
 * 2. A launcher start: the home screen, its drawer opened and closed (the intro is skipped when
 *    a fresh install shows it).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

  @get:Rule
  val rule = BaselineProfileRule()

  @Test
  fun generate() = rule.collect(
    packageName = TARGET_PACKAGE,
    maxIterations = 5,
    stableIterations = 2,
    includeInStartupProfile = true,
  ) {
    // 1. Link open
    device.executeShellCommand("am force-stop $PROVIDER_PACKAGE")
    pressHome()
    // Started without waiting for a frame of the app: the intercept activity draws nothing and
    // hands the link to the Custom Tab, and a busy emulator can take longer than the start
    // command's own time limit.
    InstrumentationRegistry.getInstrumentation().context.startActivity(
      Intent(Intent.ACTION_VIEW, Uri.parse("$LINK?i=${iteration ?: 0}")).apply {
        setClassName(TARGET_PACKAGE, INTERCEPT_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    )
    device.wait(Until.hasObject(By.pkg(PROVIDER_PACKAGE).depth(0)), WAIT_MS)

    // 2. Launcher start to home, drawer open and closed
    pressHome()
    InstrumentationRegistry.getInstrumentation().context.startActivity(
      Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(TARGET_PACKAGE, MAIN_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    device.wait(Until.findObject(By.res("intro-skip")), SHORT_WAIT_MS)?.click()
    device.wait(Until.findObject(By.res("home-menu")), WAIT_MS)?.let { menu ->
      menu.click()
      device.waitForIdle()
      device.pressBack()
      device.waitForIdle()
    }
  }

  companion object {
    const val TARGET_PACKAGE = "arun.com.chromer"
    const val INTERCEPT_ACTIVITY = "arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity"
    const val LINK = "https://example.com/"
    const val PROVIDER_PACKAGE = "com.android.chrome"
    const val MAIN_ACTIVITY = "arun.com.chromer.MainActivity"
    const val WAIT_MS = 60_000L
    const val SHORT_WAIT_MS = 10_000L
  }
}
