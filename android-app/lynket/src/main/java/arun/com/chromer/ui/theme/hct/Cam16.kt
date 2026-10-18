/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Ported to Kotlin from Material Components for Android 1.12.0,
// lib/java/com/google/android/material/color/utilities/Cam16.java. Only the ARGB to hue and
// chroma path is kept; the constants, the arithmetic and its order are unchanged.

package arun.com.chromer.ui.theme.hct

/** CAM16 hue and chroma of a colour, in the default viewing conditions. */
internal class Cam16 private constructor(val hue: Double, val chroma: Double) {

  companion object {
    /** Transforms XYZ coordinates to the CAM16 'cone' responses. */
    val XYZ_TO_CAM16RGB: Array<DoubleArray> = arrayOf(
      doubleArrayOf(0.401288, 0.650173, -0.051461),
      doubleArrayOf(-0.250268, 1.204414, 0.045854),
      doubleArrayOf(-0.002079, 0.048952, 0.953127),
    )

    fun fromInt(argb: Int): Cam16 = fromIntInViewingConditions(argb, ViewingConditions.DEFAULT)

    private fun fromIntInViewingConditions(argb: Int, viewingConditions: ViewingConditions): Cam16 {
      val red = (argb and 0x00ff0000) shr 16
      val green = (argb and 0x0000ff00) shr 8
      val blue = (argb and 0x000000ff)
      val redL = ColorUtils.linearized(red)
      val greenL = ColorUtils.linearized(green)
      val blueL = ColorUtils.linearized(blue)
      val x = 0.41233895 * redL + 0.35762064 * greenL + 0.18051042 * blueL
      val y = 0.2126 * redL + 0.7152 * greenL + 0.0722 * blueL
      val z = 0.01932141 * redL + 0.11916382 * greenL + 0.95034478 * blueL
      return fromXyzInViewingConditions(x, y, z, viewingConditions)
    }

    private fun fromXyzInViewingConditions(
      x: Double,
      y: Double,
      z: Double,
      viewingConditions: ViewingConditions,
    ): Cam16 {
      // XYZ to 'cone'/'rgb' responses.
      val matrix = XYZ_TO_CAM16RGB
      val rT = (x * matrix[0][0]) + (y * matrix[0][1]) + (z * matrix[0][2])
      val gT = (x * matrix[1][0]) + (y * matrix[1][1]) + (z * matrix[1][2])
      val bT = (x * matrix[2][0]) + (y * matrix[2][1]) + (z * matrix[2][2])

      // Discount illuminant.
      val rD = viewingConditions.rgbD[0] * rT
      val gD = viewingConditions.rgbD[1] * gT
      val bD = viewingConditions.rgbD[2] * bT

      // Chromatic adaptation.
      val rAF = Math.pow(viewingConditions.fl * Math.abs(rD) / 100.0, 0.42)
      val gAF = Math.pow(viewingConditions.fl * Math.abs(gD) / 100.0, 0.42)
      val bAF = Math.pow(viewingConditions.fl * Math.abs(bD) / 100.0, 0.42)
      val rA = Math.signum(rD) * 400.0 * rAF / (rAF + 27.13)
      val gA = Math.signum(gD) * 400.0 * gAF / (gAF + 27.13)
      val bA = Math.signum(bD) * 400.0 * bAF / (bAF + 27.13)

      // Redness-greenness and yellowness-blueness.
      val a = (11.0 * rA + -12.0 * gA + bA) / 11.0
      val b = (rA + gA - 2.0 * bA) / 9.0

      // Auxiliary components.
      val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
      val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0

      // Hue.
      val atan2 = Math.atan2(b, a)
      val atanDegrees = Math.toDegrees(atan2)
      val hue = if (atanDegrees < 0) {
        atanDegrees + 360.0
      } else if (atanDegrees >= 360) {
        atanDegrees - 360.0
      } else {
        atanDegrees
      }

      // Achromatic response and lightness.
      val ac = p2 * viewingConditions.nbb
      val j = 100.0 * Math.pow(ac / viewingConditions.aw, viewingConditions.c * viewingConditions.z)

      // Chroma.
      val huePrime = if (hue < 20.14) hue + 360 else hue
      val eHue = 0.25 * (Math.cos(Math.toRadians(huePrime) + 2.0) + 3.8)
      val p1 = 50000.0 / 13.0 * eHue * viewingConditions.nc * viewingConditions.ncb
      val t = p1 * Math.hypot(a, b) / (u + 0.305)
      val alpha = Math.pow(1.64 - Math.pow(0.29, viewingConditions.n), 0.73) * Math.pow(t, 0.9)
      val c = alpha * Math.sqrt(j / 100.0)

      return Cam16(hue, c)
    }
  }
}
