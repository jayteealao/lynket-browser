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

package arun.com.chromer.ui.reader

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The image viewer: pinch and double-tap zoom, the hint, Close and Share. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ImageViewerTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val events = mutableListOf<String>()

  private fun show() {
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Dark) {
        ImageViewerScreen("file:///nowhere.png", onClose = { events += "close" }, onShare = { events += "share" })
      }
    }
  }

  @Test
  fun closeShareAndTheHintShow() {
    show()
    composeRule.onNodeWithText("Pinch to zoom").assertIsDisplayed()
    composeRule.onNodeWithTag("image-close").performClick()
    composeRule.onNodeWithTag("image-share").performClick()
    assertThat(events).containsExactly("close", "share").inOrder()
  }

  @Test
  fun doubleTapZoomsInAndOut() {
    show()
    composeRule.onNodeWithTag("image").performTouchInput { doubleClick() }
    composeRule.onNodeWithTag("image-zoomed").assertExists()
    composeRule.onNodeWithTag("image-hint").assertDoesNotExist()
    composeRule.onNodeWithTag("image-zoomed").performTouchInput { doubleClick() }
    composeRule.onNodeWithTag("image").assertExists()
  }

  @Test
  fun pinchZoomsIn() {
    show()
    composeRule.onNodeWithTag("image").performTouchInput {
      pinch(center - Offset(20f, 0f), center - Offset(200f, 0f), center + Offset(20f, 0f), center + Offset(200f, 0f))
    }
    composeRule.onNodeWithTag("image-zoomed").assertExists()
  }

  @Test
  fun zoomStaysInItsRangeAndPanStaysInsideTheImage() {
    val zoom = ImageZoom()
    zoom.size = androidx.compose.ui.unit.IntSize(1000, 500)
    zoom.transform(10f, Offset(5_000f, 5_000f))
    assertThat(zoom.scale).isEqualTo(IMAGE_MAX_SCALE)
    assertThat(zoom.offset).isEqualTo(Offset(1000f, 500f))
    zoom.transform(0.01f, Offset.Zero)
    assertThat(zoom.scale).isEqualTo(IMAGE_MIN_SCALE)
    assertThat(zoom.offset).isEqualTo(Offset.Zero)
    zoom.toggle()
    assertThat(zoom.scale).isEqualTo(IMAGE_DOUBLE_TAP_SCALE)
  }
}
