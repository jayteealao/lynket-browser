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
// lib/java/com/google/android/material/color/utilities/MathUtils.java. Only the functions the
// tone calculation reaches are kept; the arithmetic and its order are unchanged.

package arun.com.chromer.ui.theme.hct

internal object MathUtils {

  /** 1 if [num] > 0, -1 if [num] < 0, and 0 if [num] = 0. */
  fun signum(num: Double): Int = when {
    num < 0 -> -1
    num == 0.0 -> 0
    else -> 1
  }

  /** [start] if [amount] = 0 and [stop] if [amount] = 1. */
  fun lerp(start: Double, stop: Double, amount: Double): Double = (1.0 - amount) * start + amount * stop

  fun clampInt(min: Int, max: Int, input: Int): Int = when {
    input < min -> min
    input > max -> max
    else -> input
  }

  fun clampDouble(min: Double, max: Double, input: Double): Double = when {
    input < min -> min
    input > max -> max
    else -> input
  }

  /** A degree measure between 0.0 (inclusive) and 360.0 (exclusive). */
  fun sanitizeDegreesDouble(degrees: Double): Double {
    var result = degrees % 360.0
    if (result < 0) {
      result += 360.0
    }
    return result
  }

  /** Multiplies a 1x3 row vector with a 3x3 matrix. */
  fun matrixMultiply(row: DoubleArray, matrix: Array<DoubleArray>): DoubleArray {
    val a = row[0] * matrix[0][0] + row[1] * matrix[0][1] + row[2] * matrix[0][2]
    val b = row[0] * matrix[1][0] + row[1] * matrix[1][1] + row[2] * matrix[1][2]
    val c = row[0] * matrix[2][0] + row[1] * matrix[2][1] + row[2] * matrix[2][2]
    return doubleArrayOf(a, b, c)
  }
}
