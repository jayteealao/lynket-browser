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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import kotlin.math.hypot
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

  @Test
  fun glyphStaysInsideTheSafeZone() {
    val glyph = rasterise(ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome)!!)
    assertEquals("painted pixels outside the 66 dp safe zone", 0, paintedOutsideSafeZone(glyph))

    // The 0.72 scale and the centring: the glyph spans x 31.7-76.3 dp and y 43.9-64.1 dp.
    val bounds = paintedBoundsDp(glyph)
    val expected = floatArrayOf(toCanvas(23f, 52f).x, toCanvas(23f, 52f).y, toCanvas(85f, 80f).x, toCanvas(85f, 80f).y)
    for (i in 0..3) assertEquals("bounds $i: ${bounds.toList()}", expected[i], bounds[i], 0.5f)

    // Control: the same check on the full-colour foreground, which reaches outside the safe zone.
    val foreground = rasterise(ContextCompat.getDrawable(context, R.mipmap.ic_launcher_foreground)!!)
    assertTrue("the check must catch a layer outside the safe zone", paintedOutsideSafeZone(foreground) > 0)
  }

  @Test
  fun glyphHasOneColourAndItsCutOuts() {
    val glyph = rasterise(ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome)!!)

    val colours = mutableSetOf<Int>()
    glyph.forEachPainted { _, _, pixel -> colours += pixel and 0xFFFFFF }
    assertEquals("RGB values of the painted pixels: ${colours.map { Integer.toHexString(it) }}", 1, colours.size)

    // Points in the approved glyph's own coordinates (the screen page's path data).
    val opaque = mapOf(
      "tag outline, left edge" to toCanvas(25f, 66f),
      "globe ring" to toCanvas(35f, 59.75f),
      "document tile between lines 1 and 2" to toCanvas(73f, 63.15f),
      "document tile between lines 2 and 3" to toCanvas(73f, 68.85f),
    )
    val transparent = mapOf(
      "inside the tag outline, right of the globe" to toCanvas(45.5f, 66f),
      "inside the globe ring, above the land" to toCanvas(35f, 62f),
      "document line 1" to toCanvas(73f, 60.25f),
      "document line 2" to toCanvas(71.5f, 66f),
      "document line 3" to toCanvas(73f, 71.75f),
    )
    for ((name, point) in opaque) assertEquals("$name is painted", 255, alphaAt(glyph, point))
    for ((name, point) in transparent) assertEquals("$name is cut out", 0, alphaAt(glyph, point))
  }
}

private fun alphaAt(bitmap: Bitmap, point: CanvasPoint): Int =
  Color.alpha(bitmap.getPixel((point.x * PX_PER_DP).toInt(), (point.y * PX_PER_DP).toInt()))

/** Pixels per dp of the test rasters: the 108 dp canvas is 432 x 432 px. */
private const val PX_PER_DP = 4
private const val CANVAS_PX = 108 * PX_PER_DP

/** A point of the approved glyph's own coordinates, moved onto the 108 dp canvas as the layer draws it. */
internal data class CanvasPoint(val x: Float, val y: Float)

internal fun toCanvas(x: Float, y: Float) = CanvasPoint(54 + 0.72f * (x - 54), 54 + 0.72f * (y - 66))

private fun rasterise(drawable: Drawable): Bitmap {
  val bitmap = Bitmap.createBitmap(CANVAS_PX, CANVAS_PX, Bitmap.Config.ARGB_8888)
  drawable.setBounds(0, 0, CANVAS_PX, CANVAS_PX)
  drawable.draw(Canvas(bitmap))
  return bitmap
}

private inline fun Bitmap.forEachPainted(action: (x: Int, y: Int, pixel: Int) -> Unit) {
  for (y in 0 until height) for (x in 0 until width) {
    val pixel = getPixel(x, y)
    if (Color.alpha(pixel) > 0) action(x, y, pixel)
  }
}

/** Painted pixels whose centre lies farther than 33 dp from the centre of the canvas. */
private fun paintedOutsideSafeZone(bitmap: Bitmap): Int {
  val centre = CANVAS_PX / 2f
  var outside = 0
  bitmap.forEachPainted { x, y, _ -> if (hypot(x + 0.5f - centre, y + 0.5f - centre) > 33f * PX_PER_DP) outside++ }
  return outside
}

/** The painted bounding box in dp: left, top, right, bottom. */
private fun paintedBoundsDp(bitmap: Bitmap): FloatArray {
  var left = Int.MAX_VALUE
  var top = Int.MAX_VALUE
  var right = -1
  var bottom = -1
  bitmap.forEachPainted { x, y, _ ->
    left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
  }
  return intArrayOf(left, top, right, bottom).map { it.toFloat() / PX_PER_DP }.toFloatArray()
}
