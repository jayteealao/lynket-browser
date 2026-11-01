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

package arun.com.chromer.ui.accessibility

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The accessibility checker itself: what it fails and what it lets through. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AccessibilityChecksTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private var visible by mutableStateOf(true)

  /** Shows [content]; [hide] removes it again so the after-test check sees a clean screen. */
  private fun show(content: @Composable () -> Unit) {
    composeRule.setContent { ChromerTheme { if (visible) content() } }
  }

  private fun hide() {
    visible = false
    composeRule.waitForIdle()
  }

  @Test
  fun anIconButtonWithNoLabelFailsOnClick() {
    show {
      IconButton(onClick = {}, modifier = Modifier.testTag("unlabelled")) {
        Icon(Icons.Filled.Add, contentDescription = null)
      }
    }

    val error = assertThrows(AssertionError::class.java) {
      composeRule.onNodeWithTag("unlabelled").performClick()
    }

    assertThat(error).hasMessageThat().contains("SpeakableLabel: tag=unlabelled")
    hide()
  }

  /**
   * Compose widens the touch area of a click action to 48 dp by itself, so a small clickable
   * passes; a long-press-only element is not widened, and a screen reader sees its real size.
   */
  @Test
  fun aLongPressTargetUnder48DpFailsTheTouchTargetCheck() {
    show {
      Box(
        Modifier.testTag("small").size(24.dp).semantics {
          contentDescription = "x"
          onLongClick { true }
        },
      )
    }

    val error = assertThrows(AssertionError::class.java) {
      composeRule.onNodeWithTag("small").performTouchInput { longClick() }
    }

    assertThat(error).hasMessageThat().contains("TouchTarget: tag=small")
    assertThat(error).hasMessageThat().doesNotContain("SpeakableLabel")
    hide()
  }

  @Test
  fun aSliderWithNoLabelFails() {
    show { Slider(value = 0.5f, onValueChange = {}, modifier = Modifier.testTag("slider")) }

    val error = assertThrows(AssertionError::class.java) {
      composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    assertThat(error).hasMessageThat().contains("SpeakableLabel: tag=slider")
    hide()
  }

  @Test
  fun aLabelledSliderPasses() {
    show {
      Slider(
        value = 0.5f,
        onValueChange = {},
        modifier = Modifier.testTag("slider").semantics { contentDescription = "Text size" },
      )
    }

    composeRule.onRoot().tryPerformAccessibilityChecks()
  }

  @Test
  fun aLabelledIconButtonPasses() {
    show {
      IconButton(onClick = {}, modifier = Modifier.testTag("labelled")) {
        Icon(Icons.Filled.Add, contentDescription = "Add")
      }
    }

    composeRule.onNodeWithTag("labelled").performClick()
  }

  @Test
  fun aTextLinkPasses() {
    show {
      Text(
        buildAnnotatedString {
          append("Read the ")
          withLink(LinkAnnotation.Url("https://example.com")) { append("guide") }
        },
      )
    }

    composeRule.onRoot().tryPerformAccessibilityChecks()
  }
}
