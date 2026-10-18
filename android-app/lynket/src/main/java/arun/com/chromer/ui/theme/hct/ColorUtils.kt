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
// lib/java/com/google/android/material/color/utilities/ColorUtils.java. Only the functions the
// tone calculation reaches are kept; the constants, the arithmetic and its order are unchanged.

package arun.com.chromer.ui.theme.hct

/** Colour science helpers: sRGB, linear RGB and L*. */
internal object ColorUtils {

  val WHITE_POINT_D65: DoubleArray = doubleArrayOf(95.047, 100.0, 108.883)

  fun argbFromRgb(red: Int, green: Int, blue: Int): Int =
    (255 shl 24) or ((red and 255) shl 16) or ((green and 255) shl 8) or (blue and 255)

  fun argbFromLinrgb(linrgb: DoubleArray): Int {
    val r = delinearized(linrgb[0])
    val g = delinearized(linrgb[1])
    val b = delinearized(linrgb[2])
    return argbFromRgb(r, g, b)
  }

  /** The grey ARGB colour whose lightness is [lstar]. */
  fun argbFromLstar(lstar: Double): Int {
    val y = yFromLstar(lstar)
    val component = delinearized(y)
    return argbFromRgb(component, component, component)
  }

  /** Y in XYZ for an L* value. */
  fun yFromLstar(lstar: Double): Double = 100.0 * labInvf((lstar + 16.0) / 116.0)

  /** A channel of 0..255 as linear RGB, 0.0..100.0. */
  fun linearized(rgbComponent: Int): Double {
    val normalized = rgbComponent / 255.0
    return if (normalized <= 0.040449936) {
      normalized / 12.92 * 100.0
    } else {
      Math.pow((normalized + 0.055) / 1.055, 2.4) * 100.0
    }
  }

  /** A linear RGB channel of 0.0..100.0 as 0..255. */
  fun delinearized(rgbComponent: Double): Int {
    val normalized = rgbComponent / 100.0
    val delinearized = if (normalized <= 0.0031308) {
      normalized * 12.92
    } else {
      1.055 * Math.pow(normalized, 1.0 / 2.4) - 0.055
    }
    return MathUtils.clampInt(0, 255, Math.round(delinearized * 255.0).toInt())
  }

  fun whitePointD65(): DoubleArray = WHITE_POINT_D65

  fun labInvf(ft: Double): Double {
    val e = 216.0 / 24389.0
    val kappa = 24389.0 / 27.0
    val ft3 = ft * ft * ft
    return if (ft3 > e) {
      ft3
    } else {
      (116 * ft - 16) / kappa
    }
  }
}
