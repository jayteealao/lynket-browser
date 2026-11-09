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
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * The title, the address, the count and the icons of the bars are drawn in the readable colour of the
 * bar, so every site colour gives them at least the 4.5:1 contrast of body text.
 */
class ReadableContentTest {

  private fun ratio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
  }

  private fun assertReadable(bar: Color) {
    val content = bar.readableContentColor()
    assertWithMessage("contrast of %s on %s", content, bar).that(ratio(content, bar)).isAtLeast(4.5f)
  }

  private fun hex(rgb: Long) = Color(0xFF000000 or rgb)

  @Test
  fun very_dark_site_colours_get_white_text() {
    for (bar in listOf(hex(0x000000), hex(0x0B1F33), hex(0x121212))) {
      assertThat(bar.readableContentColor()).isEqualTo(Color.White)
      assertReadable(bar)
    }
  }

  @Test
  fun very_light_site_colours_get_black_text() {
    for (bar in listOf(hex(0xFFFFFF), hex(0xFFF8E1), hex(0xF1F8E9))) {
      assertThat(bar.readableContentColor()).isEqualTo(Color.Black)
      assertReadable(bar)
    }
  }

  @Test
  fun the_app_colour_and_a_green_site_colour_are_readable() {
    assertReadable(hex(0x3F51B5))
    assertReadable(hex(0x0B6E4F))
  }

  @Test
  fun every_grey_and_every_colour_of_a_grid_is_readable() {
    for (v in 0..255) assertReadable(Color(v, v, v))
    val steps = (0..15).map { it * 17 }
    for (r in steps) for (g in steps) for (b in steps) assertReadable(Color(r, g, b))
  }
}
