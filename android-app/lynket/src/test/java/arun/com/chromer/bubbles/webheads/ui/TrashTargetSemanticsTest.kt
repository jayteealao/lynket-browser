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

import android.app.Application
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.bubbles.webheads.motion.TrashState
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What a screen reader hears from the trash: its name while it shows, and the lock as a polite announcement. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TrashTargetSemanticsTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val trash = TrashState()

  private val liveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

  private fun show() {
    composeRule.setContent { ChromerTheme { TrashTarget(trash) } }
  }

  /** The text of the live region, "" when it says nothing. */
  private fun announced(): String = composeRule.onNode(liveRegion, useUnmergedTree = true)
    .fetchSemanticsNode()
    .config
    .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
    .joinToString()

  @Test
  fun the_shown_trash_is_called_close_target() {
    trash.scale = 0.9f
    show()
    composeRule.onNodeWithContentDescription("Close target", useUnmergedTree = true).assertExists()
    composeRule.runOnIdle { trash.scale = 0f }
    composeRule.onNodeWithContentDescription("Close target", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun a_locked_trash_says_release_to_close_in_a_polite_live_region() {
    trash.scale = 1f
    trash.locked = true
    show()
    composeRule.onNode(liveRegion, useUnmergedTree = true).assertContentDescriptionEquals("Release to close")
    composeRule.onNodeWithContentDescription("Close target", useUnmergedTree = true).assertExists()
  }

  @Test
  fun release_to_close_comes_once_per_lock() {
    trash.scale = 0.9f
    show()
    val heard = mutableListOf(announced())
    composeRule.runOnIdle { trash.scale = 1f; trash.locked = true }
    heard += announced()
    // A head that stays on the trash: the lock is written again with the same value.
    composeRule.runOnIdle { trash.locked = true }
    heard += announced()
    composeRule.runOnIdle { trash.scale = 0.9f; trash.locked = false }
    heard += announced()
    composeRule.runOnIdle { trash.scale = 1f; trash.locked = true }
    heard += announced()
    assertThat(heard).containsExactly("", "Release to close", "Release to close", "", "Release to close").inOrder()
  }
}
