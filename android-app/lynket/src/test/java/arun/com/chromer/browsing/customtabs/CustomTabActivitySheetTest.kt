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

package arun.com.chromer.browsing.customtabs

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.data.preferences.BrowsingMode
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * How long the activity that hands a link to the Custom Tab stays: gone as soon as a sheet covers
 * it, so the app the link came from shows behind the sheet; with the sheet off, until the tab
 * closes and the activity comes back, as before.
 */
@HiltAndroidTest
class CustomTabActivitySheetTest : LynketRobolectricSuite() {

  private val provider = "com.android.chrome"

  @Before
  fun installProvider() {
    clearPreferences()
    shadowOf(application.packageManager).installPackage(PackageInfo().apply { packageName = provider })
    preferences.customTabPackage(provider)
    preferences.browsingMode(BrowsingMode.SlideOver)
  }

  private fun open() = Robolectric.buildActivity(
    CustomTabActivity::class.java,
    Intent(application, CustomTabActivity::class.java).setData(Uri.parse("https://example.com/page")),
  ).setup()

  @Test
  fun aSheetLaunchLeavesAtTheFirstPause() {
    preferences.customTabSheet(true)
    val controller = open()
    assertThat(controller.get().isFinishing).isFalse()

    controller.pause()

    assertThat(controller.get().isFinishing).isTrue()
  }

  @Test
  fun aFullScreenLaunchStaysUntilTheTabClosesAndTheActivityComesBack() {
    val controller = open()

    controller.pause()
    assertThat(controller.get().isFinishing).isFalse()

    controller.resume()
    assertThat(controller.get().isFinishing).isTrue()
  }
}
