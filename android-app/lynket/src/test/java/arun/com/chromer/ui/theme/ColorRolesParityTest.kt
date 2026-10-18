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

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.settings.components.PrimaryPalette
import java.io.File
import java.security.MessageDigest
import java.util.Random
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The primary roles made from the person's colour stay the same for every colour: about 29,000
 * colours (a 16-step RGB grid, the app's colours, the colour picker palette and 25,000 random
 * colours), each in the light and the dark scheme. The kept table was recorded from Material
 * Components 1.12.0 (`MaterialColors.getColorRoles`), which the app used for these roles before it
 * computed them itself.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ColorRolesParityTest {

  @Test
  fun primary_roles_match_the_recorded_table() {
    val (listed, random) = corpus()
    // The listed colours are kept row by row; the random ones as one SHA-256 per block of 500,
    // which keeps the table small and still exact.
    val rows = listed.flatMap(::rowsOf) +
      random.chunked(RANDOM_BLOCK).mapIndexed { block, colours ->
        val text = colours.flatMap(::rowsOf).joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        "random block $block (${colours.size} colours) sha256=${digest.joinToString("") { "%02x".format(it) }}"
      }
    RecordedTable.check("color-roles.txt", rows)
  }

  private fun rowsOf(colour: Int): List<String> = listOf(true, false).map { isLight ->
    val scheme = (if (isLight) LightColours else DarkColours).withPrimary(Color(colour), isLight)
    val roles = listOf(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer)
    "%06X %s %s".format(colour and 0xFFFFFF, if (isLight) "light" else "dark", roles.joinToString(" ") { "%08X".format(it.toArgb()) })
  }

  /** The listed colours (grid, app colours, seeds, picker palette) and the random ones. */
  private fun corpus(): Pair<List<Int>, List<Int>> {
    val grid = (0 until 16 * 16 * 16).map { i ->
      val r = (i shr 8) * 17
      val g = ((i shr 4) and 15) * 17
      val b = (i and 15) * 17
      (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    val appColours = Regex("<color name=\"[^\"]+\">#([0-9A-Fa-f]{6,8})</color>")
      .findAll(File("src/main/res/values/colors.xml").readText())
      .map { (0xFF000000 or (it.groupValues[1].takeLast(6).toLong(16))).toInt() }
      .toList()
    val seeds = listOf(0xFF3F51B5.toInt(), 0xFFFF4081.toInt())
    val random = Random(0x4C594E4B).let { r -> List(25_000) { (0xFF shl 24) or (r.nextInt() and 0xFFFFFF) } }
    return (grid + appColours + seeds + PrimaryPalette) to random
  }

  private companion object {
    const val RANDOM_BLOCK = 500
  }
}
