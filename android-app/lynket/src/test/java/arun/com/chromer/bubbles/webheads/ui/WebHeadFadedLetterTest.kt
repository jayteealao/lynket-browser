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

package arun.com.chromer.bubbles.webheads.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import arun.com.chromer.ui.theme.DarkColours
import arun.com.chromer.ui.theme.LightColours
import arun.com.chromer.ui.theme.readableContentColor
import arun.com.chromer.ui.theme.toBlack
import com.google.common.truth.Truth.assertWithMessage
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min

/**
 * The first letter of a loading head stays readable: only the fill fades, and the letter takes the
 * colour that reads on the faded fill as the head draws it, over the head's own background.
 */
@RunWith(AndroidJUnit4::class)
class WebHeadFadedLetterTest {

  private fun ratio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
  }

  private val siteColours = listOf(0x3F51B5, 0x0B6E4F, 0x0B1F33, 0xD32F2F, 0xFFC107, 0xFFFFFF, 0x000000)
    .map { Color(0xFF000000 or it.toLong()) }

  @Test
  fun theLetterOfALoadingHeadReadsInEveryTheme() {
    val grounds = listOf(LightColours, DarkColours, DarkColours.toBlack()).map { it.surfaceContainerLowest }
    for (ground in grounds) {
      for (site in siteColours) {
        val shown = site.copy(alpha = LOADING_FILL_ALPHA).compositeOver(ground)
        val letter = shown.readableContentColor()
        assertWithMessage("letter on %s faded over %s", site, ground).that(ratio(letter, shown)).isAtLeast(4.5f)
      }
    }
  }
}
