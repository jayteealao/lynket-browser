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
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
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
 * Goldens of the launcher icon: the full-colour adaptive icon as it is drawn today, under the
 * launcher mask shapes and at the dense-grid size. Record with
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
}

/** The squircle as the approved screen draws it. */
internal val Squircle = RoundedCornerShape(percent = 38)

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
