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

package arun.com.chromer.data.apps

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import arun.com.chromer.shared.Constants.NO_COLOR
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The colour of another app comes from its icon's pixels when its theme gives none. The app itself,
 * the system, an empty name and an app that is not installed give no colour, and nothing throws.
 *
 * The icon colour is the palette's: it keeps 5 bits of each channel, so pure red reads as `#F80000`
 * and pure white as `#F8F8F8`. A white icon therefore still gives a (near-white) colour; only an icon
 * that cannot be read gives no colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppColorExtractorTest {
  private val application: Application get() = RuntimeEnvironment.getApplication()
  private lateinit var extractor: AppColorExtractor

  @Before
  fun setUp() {
    extractor = AppColorExtractor(application)
  }

  @Test
  fun aSolidRedIconGivesRed() {
    install(RED_PKG, Color.RED)

    assertThat(extractor.extract(RED_PKG)).isEqualTo(PALETTE_RED)
  }

  @Test
  fun anOpaqueWhiteIconGivesTheNearWhiteOfThePaletteNotNoColour() {
    install(WHITE_PKG, Color.WHITE)

    assertThat(extractor.extract(WHITE_PKG)).isEqualTo(PALETTE_WHITE)
  }

  @Test
  fun theAppItselfTheSystemAndAnEmptyNameGiveNoColour() {
    assertThat(extractor.extract(application.packageName)).isEqualTo(NO_COLOR)
    assertThat(extractor.extract("android")).isEqualTo(NO_COLOR)
    assertThat(extractor.extract("")).isEqualTo(NO_COLOR)
  }

  @Test
  fun anAppThatIsNotInstalledGivesNoColourWithoutThrowing() {
    assertThat(extractor.extract("com.example.not.installed")).isEqualTo(NO_COLOR)
  }

  private fun install(packageName: String, color: Int) {
    val info = PackageInfo().apply {
      this.packageName = packageName
      applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
    }
    val pm = shadowOf(application.packageManager)
    pm.installPackage(info)
    val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    pm.setApplicationIcon(packageName, BitmapDrawable(application.resources, bitmap))
  }

  private companion object {
    const val RED_PKG = "com.example.red"
    const val WHITE_PKG = "com.example.white"
    const val PALETTE_RED = 0xFFF80000.toInt()
    const val PALETTE_WHITE = 0xFFF8F8F8.toInt()
  }
}
