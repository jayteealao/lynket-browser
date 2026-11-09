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

package arun.com.chromer.ui.launcher

import android.app.Application
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The themed launcher icon: both adaptive icons carry the monochrome layer from Android 13, the
 * glyph stays inside the 66 dp safe zone in one colour with its cut-outs, and Android 12 and older
 * keep the full-colour icon.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class LauncherIconMonochromeTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun mipmap(id: Int): Drawable = ContextCompat.getDrawable(context, id)!!

  private fun px(dp: Float): Int = (dp * context.resources.displayMetrics.density).roundToInt()

  @Test
  @Config(sdk = [33, 36])
  fun bothLauncherIconsHaveTheMonochromeLayer() {
    for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
      val icon = mipmap(id)
      assertTrue("$icon is not an adaptive icon", icon is AdaptiveIconDrawable)
      icon as AdaptiveIconDrawable
      val monochrome = icon.monochrome
      assertTrue("monochrome layer is $monochrome", monochrome is VectorDrawable)
      assertEquals(px(108f), monochrome!!.intrinsicWidth)
      assertEquals(px(108f), monochrome.intrinsicHeight)
      assertTrue("foreground is ${icon.foreground}", icon.foreground is BitmapDrawable)
      assertTrue("background is ${icon.background}", icon.background is BitmapDrawable)
    }
  }
}
