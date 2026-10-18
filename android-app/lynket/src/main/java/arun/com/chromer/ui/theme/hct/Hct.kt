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
// lib/java/com/google/android/material/color/utilities/Hct.java. Only fromInt, setTone and
// toInt are kept (the stored tone, which none of them reads, is left out); their arithmetic is
// unchanged.

package arun.com.chromer.ui.theme.hct

/**
 * HCT: CAM16 hue and chroma with L* as tone, in the default viewing conditions. A tone
 * difference of 40 gives a contrast ratio of at least 3.0, and 50 at least 4.5.
 */
internal class Hct private constructor(argb: Int) {
  private var hue = 0.0
  private var chroma = 0.0
  private var argb = 0

  init {
    setInternalState(argb)
  }

  fun toInt(): Int = argb

  /** Sets the tone (0..100); the chroma may drop, because each hue and tone has its own maximum. */
  fun setTone(newTone: Double) {
    setInternalState(HctSolver.solveToInt(hue, chroma, newTone))
  }

  private fun setInternalState(argb: Int) {
    this.argb = argb
    val cam = Cam16.fromInt(argb)
    hue = cam.hue
    chroma = cam.chroma
  }

  companion object {
    fun fromInt(argb: Int): Hct = Hct(argb)

    /** The colour [argb] at [tone], with its hue and as much of its chroma as the tone allows. */
    fun tone(argb: Int, tone: Int): Int = fromInt(argb).also { it.setTone(tone.toDouble()) }.toInt()
  }
}
