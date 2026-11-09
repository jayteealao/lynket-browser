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

package arun.com.chromer.ui.tabs

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.ARTICLE
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.ui.TabsUiState
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.history.WORST_LONG_URL
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.util.displayAddress
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The second line of a tab row on a phone: the type label, the dot and the address on one line at
 * the normal font size; at 200% the address goes under the label at the full width and the dot is
 * not drawn, while the address keeps its whole text (the cut is only drawn).
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: the legacy graphics mode gives text one pixel per character.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TabAddressLayoutTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val tab = TabsManager.Tab(
    WORST_LONG_URL,
    ARTICLE,
    Website("Release notes", WORST_LONG_URL, null, null, null, null, false, 0, 1),
  )

  private fun show(fontScale: Float) {
    composeRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        ChromerTheme(themeMode = ThemeMode.Light) {
          TabsContent(state = TabsUiState.Loaded(listOf(tab)), closeIcon = true, actions = TabsActions())
        }
      }
    }
    composeRule.waitForIdle()
  }

  private fun bounds(tag: String): Rect =
    composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  @Test
  fun theAddressStacksOnlyWhenItWouldGetUnderAThirdOfTheLine() {
    assertThat(stackTabAddress(lineWidth = 300, labelWidth = 150)).isFalse()
    assertThat(stackTabAddress(lineWidth = 300, labelWidth = 200)).isFalse()
    assertThat(stackTabAddress(lineWidth = 300, labelWidth = 201)).isTrue()
  }

  @Test
  fun atTheNormalFontTheLabelDotAndAddressShareOneLine() {
    show(fontScale = 1f)
    val label = bounds("tab-type")
    val dot = bounds("tab-dot")
    val address = bounds("tab-address")
    assertThat(address.top).isWithin(1f).of(label.top)
    assertThat(dot.left).isAtLeast(label.right)
    assertThat(address.left).isAtLeast(dot.right)
    composeRule.onNodeWithTag("tab-dot", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun atTwiceTheFontTheAddressGoesUnderTheLabelWithNoDot() {
    show(fontScale = 2f)
    val line = bounds("tab-address-line")
    val label = bounds("tab-type")
    val address = bounds("tab-address")
    assertThat(address.top).isAtLeast(label.bottom)
    assertThat(address.left).isWithin(0.5f).of(label.left)
    assertThat(address.width).isWithin(0.5f).of(line.width)
    composeRule.onNodeWithTag("tab-dot", useUnmergedTree = true).assertIsNotDisplayed()
    composeRule.onNodeWithTag("tab-address", useUnmergedTree = true).assertTextEquals(displayAddress(WORST_LONG_URL))
  }
}
