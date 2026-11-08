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

package arun.com.chromer.ui.history

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.history.HistoryUiState
import arun.com.chromer.tabs.CUSTOM_TAB
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.ui.TabsUiState
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.tabs.TabsActions
import arun.com.chromer.ui.tabs.TabsContent
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * With the system's animations off (the animation duration scale at 0, which Android hands to
 * Compose as the motion duration scale), a removed row or a closed tab leaves at once and the rows
 * below are in their place on the first frame that shows the change.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RowMotionAnimationsOffTest {

  @get:Rule(order = 0)
  val uiDispatcherReset = UiDispatcherResetRule()

  @get:Rule(order = 1)
  val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
    override val scaleFactor = 0f
  })

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val clock get() = composeRule.mainClock

  private fun exists(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  private fun top(text: String) = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

  /** Advances one frame at a time until the row named [text] leaves its [top]; returns its new top. */
  private fun firstFrameThatMoves(text: String, top: Float): Float {
    repeat(10) {
      clock.advanceTimeByFrame()
      composeRule.waitForIdle()
      val now = top(text)
      if (now != top) return now
    }
    error("$text did not move in 10 frames")
  }

  @Test
  fun aRemovedHistoryRowSnapsAway() {
    val data = MutableStateFlow(loaded(twoDaySites))
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        HistoryContent(HistoryUiState(), data.collectAsLazyPagingItems(), "Chrome", HistoryActions(), now = TEST_NOW, timeZone = TEST_ZONE)
      }
    }
    composeRule.waitForIdle()
    clock.autoAdvance = false
    val secondTop = top("Navigation 3")
    val thirdTop = top("r/androiddev")
    data.value = loaded(twoDaySites - twoDaySites[1])
    assertThat(firstFrameThatMoves("r/androiddev", thirdTop)).isWithin(0.5f).of(secondTop)
    assertThat(exists("Navigation 3")).isFalse()
  }

  @Test
  fun aClosedTabSnapsAway() {
    fun tab(n: Int) = TabsManager.Tab("https://example.com/?t=$n", CUSTOM_TAB, Website("Tab $n", "https://example.com/?t=$n", null, null, null, null, false, 0, 1))
    val tabs = (1..4).map(::tab)
    val state = mutableStateOf<TabsUiState>(TabsUiState.Loaded(tabs))
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) { TabsContent(state.value, closeIcon = true, actions = TabsActions()) }
    }
    composeRule.waitForIdle()
    clock.autoAdvance = false
    val secondTop = top("Tab 2")
    val thirdTop = top("Tab 3")
    state.value = TabsUiState.Loaded(tabs - tabs[1])
    assertThat(firstFrameThatMoves("Tab 3", thirdTop)).isWithin(0.5f).of(secondTop)
    assertThat(exists("Tab 2")).isFalse()
  }
}
