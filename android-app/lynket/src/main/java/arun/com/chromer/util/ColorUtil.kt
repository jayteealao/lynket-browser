/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

// Phase 7: Converted from Java to Kotlin

package arun.com.chromer.util

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import arun.com.chromer.shared.Constants.NO_COLOR
import java.util.LinkedList
import java.util.TreeMap

/**
 * Created by Arun on 12/06/2016.
 */
object ColorUtil {

  val ACCENT_COLORS = intArrayOf(
    Color.parseColor("#FF1744"),
    Color.parseColor("#F50057"),
    Color.parseColor("#D500F9"),
    Color.parseColor("#651FFF"),
    Color.parseColor("#3D5AFE"),
    Color.parseColor("#2979FF"),
    Color.parseColor("#00B0FF"),
    Color.parseColor("#00E5FF"),
    Color.parseColor("#1DE9B6"),
    Color.parseColor("#00E676"),
    Color.parseColor("#76FF03"),
    Color.parseColor("#C6FF00"),
    Color.parseColor("#FFEA00"),
    Color.parseColor("#FFC400"),
    Color.parseColor("#FF9100"),
    Color.parseColor("#FF3D00")
  )

  val PLACEHOLDER_COLORS = intArrayOf(
    Color.parseColor("#D32F2F"),
    Color.parseColor("#C2185B"),
    Color.parseColor("#303F9F"),
    Color.parseColor("#6A1B9A"),
    Color.parseColor("#37474F"),
    Color.parseColor("#2E7D32")
  )

  val ACCENT_COLORS_700 = intArrayOf(
    Color.parseColor("#D32F2F"),
    Color.parseColor("#C2185B"),
    Color.parseColor("#7B1FA2"),
    Color.parseColor("#6200EA"),
    Color.parseColor("#304FFE"),
    Color.parseColor("#2962FF"),
    Color.parseColor("#0091EA"),
    Color.parseColor("#00B8D4"),
    Color.parseColor("#00BFA5"),
    Color.parseColor("#00C853"),
    Color.parseColor("#64DD17"),
    Color.parseColor("#AEEA00"),
    Color.parseColor("#FFD600"),
    Color.parseColor("#FFAB00"),
    Color.parseColor("#FF6D00"),
    Color.parseColor("#DD2C00"),
    Color.parseColor("#455A64")
  )

  /**
   * Percentage to darken a color by when setting the status bar color.
   */
  private const val DARKEN_COLOR_FRACTION = 0.6f
  private const val CONTRAST_LIGHT_ITEM_THRESHOLD = 3f

  fun getSwatchListFromPalette(palette: Palette): List<Palette.Swatch?> {
    val swatchList = LinkedList<Palette.Swatch?>()
    val prominentSwatch = palette.dominantSwatch
    val vibrantSwatch = palette.vibrantSwatch
    val vibrantDarkSwatch = palette.darkVibrantSwatch
    val vibrantLightSwatch = palette.lightVibrantSwatch
    val mutedSwatch = palette.mutedSwatch
    val mutedDarkSwatch = palette.darkMutedSwatch
    val mutedLightSwatch = palette.lightMutedSwatch

    swatchList.add(prominentSwatch)
    swatchList.add(vibrantSwatch)
    swatchList.add(vibrantDarkSwatch)
    swatchList.add(vibrantLightSwatch)
    swatchList.add(mutedSwatch)
    swatchList.add(mutedDarkSwatch)
    swatchList.add(mutedLightSwatch)
    return swatchList
  }

  private fun colorDifference(@ColorInt a: Int, @ColorInt b: Int): Double {
    val aLab = DoubleArray(3)
    val bLab = DoubleArray(3)
    ColorUtils.colorToLAB(a, aLab)
    ColorUtils.colorToLAB(b, bLab)
    return ColorUtils.distanceEuclidean(aLab, bLab)
  }

  @ColorInt
  fun getClosestAccentColor(@ColorInt color: Int): Int {
    val set = TreeMap<Double, Int>()
    val invertedColor = (0xFFFFFF - color) or 0xFF000000.toInt()
    for (i in ACCENT_COLORS_700.indices) {
      set[colorDifference(invertedColor, ACCENT_COLORS_700[i])] = i
    }
    return ACCENT_COLORS_700[set[set.firstKey()]!!]
  }

  @ColorInt
  fun getBestFaviconColor(palette: Palette?): Int {
    if (palette != null) {
      val sortedSwatch = getSwatchListFromPalette(palette)
      // We want the vibrant color but we will avoid it if it is the most prominent one.
      // Instead we will choose the next prominent color
      val vibrantColor = palette.getVibrantColor(NO_COLOR)
      val prominentColor = if (sortedSwatch[0] != null) sortedSwatch[0]!!.rgb else NO_COLOR
      if (vibrantColor == NO_COLOR) {
        val darkVibrantColor = palette.getDarkVibrantColor(NO_COLOR)
        if (darkVibrantColor != NO_COLOR) {
          return darkVibrantColor
        } else {
          val mutedColor = palette.getMutedColor(NO_COLOR)
          if (mutedColor != NO_COLOR) {
            return mutedColor
          } else {
            return prominentColor
          }
        }
      } else return vibrantColor
    }
    return NO_COLOR
  }

  @ColorInt
  fun getBestColorFromPalette(palette: Palette?): Int {
    if (palette == null) {
      return NO_COLOR
    }
    val vibrantColor = palette.getVibrantColor(NO_COLOR)
    if (vibrantColor != NO_COLOR) {
      return vibrantColor
    } else {
      val darkVibrantColor = palette.getDarkVibrantColor(NO_COLOR)
      if (darkVibrantColor != NO_COLOR) {
        return darkVibrantColor
      } else {
        return palette.getDarkMutedColor(NO_COLOR)
      }
    }
  }

  /**
   * Calculates the contrast between the given color and white, using the algorithm provided by
   * the WCAG v2 in http://www.w3.org/TR/WCAG20/#contrast-ratiodef.
   *
   * {@see https://chromium.googlesource.com/chromium/src/+/66.0.3335.4/chrome/android/java/src/org/chromium/chrome/browser/util/ColorUtils.java}
   */
  private fun getContrastForColor(color: Int): Float {
    var bgR = Color.red(color) / 255f
    var bgG = Color.green(color) / 255f
    var bgB = Color.blue(color) / 255f
    bgR = if (bgR < 0.03928f) bgR / 12.92f else Math.pow(((bgR + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    bgG = if (bgG < 0.03928f) bgG / 12.92f else Math.pow(((bgG + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    bgB = if (bgB < 0.03928f) bgB / 12.92f else Math.pow(((bgB + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    val bgL = 0.2126f * bgR + 0.7152f * bgG + 0.0722f * bgB
    return Math.abs(1.05f / (bgL + 0.05f))
  }

  /**
   * Darkens the given color to use on the status bar.
   *
   * {@see https://chromium.googlesource.com/chromium/src/+/66.0.3335.4/chrome/android/java/src/org/chromium/chrome/browser/util/ColorUtils.java}
   *
   * @param color Color which should be darkened.
   * @return Color that should be used for Android status bar.
   */
  fun getDarkenedColorForStatusBar(color: Int): Int {
    return getDarkenedColor(color, DARKEN_COLOR_FRACTION)
  }

  /**
   * Darken a color to a fraction of its current brightness.
   *
   * @param color          The input color.
   * @param darkenFraction The fraction of the current brightness the color should be.
   * @return The new darkened color.
   */
  fun getDarkenedColor(color: Int, darkenFraction: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[2] *= darkenFraction
    return Color.HSVToColor(hsv)
  }

  /**
   * Check whether lighter or darker foreground elements (i.e. text, drawables etc.)
   * should be used depending on the given background color.
   *
   * @param backgroundColor The background color value which is being queried.
   * @return Whether light colored elements should be used.
   */
  fun shouldUseLightForegroundOnBackground(backgroundColor: Int): Boolean {
    return getContrastForColor(backgroundColor) >= CONTRAST_LIGHT_ITEM_THRESHOLD
  }

  /**
   * Returns white or black based on color luminance
   *
   * @param backgroundColor the color to get foreground for
   * @return White for darker colors and black for ligher colors
   */
  @ColorInt
  fun getForegroundWhiteOrBlack(@ColorInt backgroundColor: Int): Int {
    return if (shouldUseLightForegroundOnBackground(backgroundColor)) {
      Color.WHITE
    } else {
      Color.BLACK
    }
  }

  fun getRippleDrawableCompat(@ColorInt color: Int): Drawable {
    return if (Utils.isLollipopAbove()) {
      RippleDrawable(
        ColorStateList.valueOf(color),
        null,
        null
      )
    } else {
      val translucentColor = ColorUtils.setAlphaComponent(color, 0x44)
      val stateListDrawable = StateListDrawable()
      val states = intArrayOf(android.R.attr.state_pressed)
      stateListDrawable.addState(states, ColorDrawable(translucentColor))
      stateListDrawable
    }
  }
}
