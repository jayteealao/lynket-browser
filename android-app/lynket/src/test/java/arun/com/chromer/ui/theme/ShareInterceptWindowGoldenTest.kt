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

package arun.com.chromer.ui.theme

import android.content.Intent
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.shareintercept.ShareInterceptActivity
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The window the share hand-off shows while plain text becomes a search: its frame (bar, title and
 * background) is drawn before the search engine is read and the page opens. Record with
 * `./gradlew :lynket:recordRoborazziDebug --tests '*ShareInterceptWindowGoldenTest*'`.
 */
@HiltAndroidTest
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ShareInterceptWindowGoldenTest : LynketRobolectricSuite() {

  @Test
  fun the_share_window_frame_stays_the_same() {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT)
      .setClass(application, ShareInterceptActivity::class.java)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_PROCESS_TEXT, "plain words to search for")
    val controller = Robolectric.buildActivity(ShareInterceptActivity::class.java, intent)
    controller.create().start().resume().visible()
    controller.get().window.decorView.captureRoboImage()
  }
}
