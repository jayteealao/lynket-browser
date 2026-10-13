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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Black or white, whichever has the higher contrast ratio on this colour. The two are equal at a
 * relative luminance of about 0.18, so mid-tone site and accent colours get black text and icons.
 */
fun Color.readableContentColor(dark: Color = Color.Black, light: Color = Color.White): Color {
  val l = luminance()
  val withBlack = (l + 0.05f) / 0.05f
  val withWhite = 1.05f / (l + 0.05f)
  return if (withBlack >= withWhite) dark else light
}
