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

package arun.com.chromer.screenshot

import android.app.Application
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the launcher icon: the full-colour adaptive icon as it is drawn today, and the
 * themed (monochrome) layer in a tint pair under the circle, squircle, rounded-square and teardrop
 * masks of launchers, at 108 dp and at the 36 dp of a dense grid. Record with
 * `./gradlew :lynket:recordRoborazziDebug`, compare with `./gradlew :lynket:verifyRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LauncherIconGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private fun launcherIcon(): Drawable =
    ContextCompat.getDrawable(ApplicationProvider.getApplicationContext(), R.mipmap.ic_launcher)!!

  private fun monochromeLayer(): Drawable =
    ContextCompat.getDrawable(ApplicationProvider.getApplicationContext(), R.drawable.ic_launcher_monochrome)!!

  /** The launcher's tint pair stands in as the platform's dynamic colours: background, glyph. */
  private fun tintPair(dark: Boolean): Pair<Color, Color> {
    val context = ApplicationProvider.getApplicationContext<Application>()
    fun colour(id: Int) = Color(context.getColor(id))
    return if (dark) {
      colour(android.R.color.system_neutral1_800) to colour(android.R.color.system_accent1_100)
    } else {
      colour(android.R.color.system_accent1_100) to colour(android.R.color.system_neutral2_700)
    }
  }

  /** The monochrome layer as a launcher draws a themed icon: the mask filled, the glyph tinted. */
  @Composable
  private fun ThemedTile(size: Dp, shape: Shape, dark: Boolean = false) {
    val (background, glyph) = tintPair(dark)
    val layer = monochromeLayer().apply { colorFilter = PorterDuffColorFilter(glyph.toArgb(), PorterDuff.Mode.SRC_IN) }
    Box(Modifier.size(size).clip(shape).background(background)) { IconTile(layer, size, RectangleShape) }
  }

  private fun capture(content: @Composable () -> Unit) {
    composeRule.setContent { Box(Modifier.padding(8.dp)) { content() } }
    composeRule.waitForIdle()
    composeRule.onRoot().captureRoboImage()
  }

  @Test
  fun launcherIcon_fullColour_circle_108() = capture { IconTile(launcherIcon(), 108.dp, CircleShape) }

  @Test
  fun launcherIcon_fullColour_squircle_108() = capture { IconTile(launcherIcon(), 108.dp, Squircle) }

  @Test
  fun launcherIcon_fullColour_circle_36() = capture { IconTile(launcherIcon(), 36.dp, CircleShape) }

  @Test
  @Config(sdk = [31])
  fun launcherIcon_fullColour_circle_108_api31() = capture { IconTile(launcherIcon(), 108.dp, CircleShape) }

  @Test
  fun launcherIcon_mono_circle_108_light() = capture { ThemedTile(108.dp, CircleShape) }

  @Test
  fun launcherIcon_mono_squircle_108_light() = capture { ThemedTile(108.dp, Squircle) }

  @Test
  fun launcherIcon_mono_roundedSquare_108_light() = capture { ThemedTile(108.dp, RoundedSquare) }

  @Test
  fun launcherIcon_mono_teardrop_108_light() = capture { ThemedTile(108.dp, Teardrop) }

  @Test
  fun launcherIcon_mono_circle_36_light() = capture { ThemedTile(36.dp, CircleShape) }

  @Test
  fun launcherIcon_mono_squircle_36_light() = capture { ThemedTile(36.dp, Squircle) }

  @Test
  fun launcherIcon_mono_roundedSquare_36_light() = capture { ThemedTile(36.dp, RoundedSquare) }

  @Test
  fun launcherIcon_mono_teardrop_36_light() = capture { ThemedTile(36.dp, Teardrop) }

  @Test
  fun launcherIcon_mono_circle_108_dark() = capture { ThemedTile(108.dp, CircleShape, dark = true) }

  /** The whole 108 dp layer at twice its size, with the 66 dp safe zone dashed, as the approved page draws it. */
  @Test
  fun launcherIcon_mono_grid_108() = capture {
    val (background, glyph) = tintPair(dark = false)
    val layer = monochromeLayer().apply { colorFilter = PorterDuffColorFilter(glyph.toArgb(), PorterDuff.Mode.SRC_IN) }
    Box(Modifier.size(216.dp).background(background)) {
      Canvas(Modifier.size(216.dp).semantics { contentDescription = "Lynket" }) {
        val side = this.size.width.toInt()
        layer.setBounds(0, 0, side, side)
        drawIntoCanvas { layer.draw(it.nativeCanvas) }
        drawCircle(
          color = glyph,
          radius = this.size.width * 33f / 108f,
          style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
        )
      }
    }
  }
}

/** The squircle as the approved screen draws it. */
internal val Squircle = RoundedCornerShape(percent = 38)

/** The rounded square and the teardrop as the approved screen draws them. */
internal val RoundedSquare = RoundedCornerShape(percent = 22)
internal val Teardrop = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 12)

/**
 * One launcher tile of [size], clipped to [shape]. A whole [AdaptiveIconDrawable] is drawn with its
 * own `draw()`, which scales its layers by 1.5 itself. A single layer is a 108-unit canvas of which
 * a launcher shows the middle 72: it is drawn at `size * 1.5`, offset by `-size / 4`.
 */
@Composable
internal fun IconTile(drawable: Drawable, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
  Canvas(
    modifier
      .size(size)
      .clip(shape)
      .semantics { contentDescription = "Lynket" },
  ) {
    val side = this.size.width.toInt()
    if (drawable is AdaptiveIconDrawable) {
      drawable.setBounds(0, 0, side, side)
    } else {
      val inset = side / 4
      drawable.setBounds(-inset, -inset, side + inset, side + inset)
    }
    drawIntoCanvas { drawable.draw(it.nativeCanvas) }
  }
}
