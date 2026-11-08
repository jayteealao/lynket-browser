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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.history.HistoryListItem
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
import com.google.common.truth.Truth.assertWithMessage
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The motion of History and Tabs rows with the main clock paused: a removed row fades out while the
 * rows below slide up and never pass their place (no bounce), the day header leaves with its last
 * row, a row put back fades in while the rows below slide down, and a closed or reordered tab moves
 * the same way. Mid-motion frames are recorded as screenshots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RowMotionTest {

  @get:Rule(order = 0)
  val uiDispatcherReset = UiDispatcherResetRule()

  @get:Rule(order = 1)
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val clock get() = composeRule.mainClock

  // A row that fades out is drawn from its own recorded layer; only the hardware renderer of
  // Robolectric draws such layers into a capture.
  @Before
  fun hardwareCaptures() {
    System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
  }

  @After
  fun softwareCaptures() {
    System.clearProperty("robolectric.pixelCopyRenderMode")
  }

  private fun showHistory(sites: List<Website>): MutableStateFlow<PagingData<HistoryListItem>> {
    val data = MutableStateFlow(loaded(sites))
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        HistoryContent(
          state = HistoryUiState(),
          items = data.collectAsLazyPagingItems(),
          providerName = "Chrome",
          actions = HistoryActions(),
          now = TEST_NOW,
          timeZone = TEST_ZONE,
        )
      }
    }
    composeRule.waitForIdle()
    clock.autoAdvance = false
    drawOnce()
    return data
  }

  private fun showTabs(tabs: List<TabsManager.Tab>): MutableState<TabsUiState> {
    val state = mutableStateOf<TabsUiState>(TabsUiState.Loaded(tabs))
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        TabsContent(state = state.value, closeIcon = true, actions = TabsActions())
      }
    }
    composeRule.waitForIdle()
    clock.autoAdvance = false
    drawOnce()
    return state
  }

  /**
   * Robolectric draws only when asked. A row that fades out is drawn from what it last drew, so the
   * list is drawn once before a change, as a device does on every frame.
   */
  private fun drawOnce() {
    composeRule.onRoot().captureToImage()
  }

  private fun exists(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  private fun top(text: String) = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

  /** Advances frame by frame until [moved] holds, at most 10 frames; returns the frames it took. */
  private fun framesUntil(moved: () -> Boolean): Int {
    repeat(10) { frame ->
      clock.advanceTimeByFrame()
      composeRule.waitForIdle()
      if (moved()) return frame + 1
    }
    error("nothing moved in 10 frames")
  }

  /** The top of [text] now and every 16 ms after, for one second. */
  private fun samples(text: String): List<Float> = listOf(top(text)) + List(60) {
    clock.advanceTimeBy(16)
    composeRule.waitForIdle()
    top(text)
  }

  /**
   * Each sample moves the way of [down] (up when false) and none passes the place where the row
   * rests (within half a pixel): the spring does not bounce. Returns the resting place.
   */
  private fun assertMovesWithoutBounce(samples: List<Float>, down: Boolean): Float {
    val final = samples.last()
    samples.zipWithNext().forEach { (before, after) ->
      if (down) assertWithMessage("$samples").that(after).isAtLeast(before - 0.5f)
      else assertWithMessage("$samples").that(after).isAtMost(before + 0.5f)
    }
    samples.forEach {
      if (down) assertWithMessage("$samples").that(it).isAtMost(final + 0.5f)
      else assertWithMessage("$samples").that(it).isAtLeast(final - 0.5f)
    }
    assertWithMessage("$samples").that(samples[samples.size - 2]).isWithin(0.5f).of(final)
    return final
  }

  /**
   * How many pixels inside [area] differ from the list's own background, taken from the gutter at
   * the left edge: a row that fades out is drawn from its last frame, outside the semantics tree.
   */
  private fun inkIn(area: Rect): Int {
    val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
    var ink = 0
    for (y in area.top.toInt() until area.bottom.toInt() step 4) {
      val background = bitmap.getPixel(8, y)
      for (x in area.left.toInt() until area.right.toInt() step 4) if (bitmap.getPixel(x, y) != background) ink++
    }
    return ink
  }

  private fun bounds(text: String): Rect = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

  private fun capture(name: String) {
    composeRule.onRoot().captureRoboImage("src/test/screenshots/arun.com.chromer.ui.history.RowMotionTest.$name.png")
  }

  @Test
  fun aRemovedRowFadesOutAndTheRowsBelowSlideUpWithNoBounce() {
    val data = showHistory(twoDaySites)
    val removed = bounds("Navigation 3")
    val thirdTop = top("r/androiddev")
    data.value = loaded(twoDaySites - twoDaySites[1])
    framesUntil { top("r/androiddev") < thirdTop - 0.5f }
    // The removed row is still drawn, fading, while the row below has started up toward it.
    assertThat(inkIn(removed.copy(bottom = removed.top + removed.height / 2))).isGreaterThan(0)
    assertThat(top("r/androiddev")).isGreaterThan(removed.top)
    assertThat(top("r/androiddev")).isLessThan(thirdTop)
    val final = assertMovesWithoutBounce(samples("r/androiddev"), down = false)
    assertThat(final).isWithin(0.5f).of(removed.top)
    assertThat(exists("Navigation 3")).isFalse()
  }

  @Test
  fun theDayHeaderLeavesWithItsLastRow() {
    val data = showHistory(twoDaySites)
    val header = bounds("Yesterday")
    val row = bounds("lynket-browser")
    data.value = loaded(twoDaySites.dropLast(1))
    framesUntil { !exists("Yesterday") }
    // Both are gone from the list but still drawn while they fade out together.
    assertThat(exists("lynket-browser")).isFalse()
    assertThat(inkIn(header)).isGreaterThan(0)
    assertThat(inkIn(row)).isGreaterThan(0)
    clock.advanceTimeBy(1_000)
    composeRule.waitForIdle()
    assertThat(inkIn(header)).isEqualTo(0)
    assertThat(inkIn(row)).isEqualTo(0)
  }

  @Test
  fun aRowPutBackFadesInAndTheRowsBelowSlideDown() {
    val data = showHistory(twoDaySites - twoDaySites[1])
    val thirdBefore = top("r/androiddev")
    data.value = loaded(twoDaySites)
    framesUntil { exists("Navigation 3") }
    val final = assertMovesWithoutBounce(samples("r/androiddev"), down = true)
    assertThat(final).isGreaterThan(thirdBefore)
    assertThat(top("Navigation 3")).isWithin(0.5f).of(thirdBefore)
  }

  private fun tab(n: Int, title: String) =
    TabsManager.Tab("https://example.com/?t=$n", CUSTOM_TAB, Website(title, "https://example.com/?t=$n", null, null, null, null, false, 0, 1))

  private val fourTabs = listOf(tab(1, "Tab one"), tab(2, "Tab two"), tab(3, "Tab three"), tab(4, "Tab four"))

  @Test
  fun aClosedTabFadesOutAndTheRowsBelowSlideUp() {
    val state = showTabs(fourTabs)
    val removed = bounds("Tab two")
    val thirdTop = top("Tab three")
    state.value = TabsUiState.Loaded(fourTabs - fourTabs[1])
    framesUntil { top("Tab three") < thirdTop - 0.5f }
    assertThat(inkIn(removed.copy(bottom = removed.top + removed.height / 2))).isGreaterThan(0)
    assertThat(top("Tab three")).isGreaterThan(removed.top)
    val final = assertMovesWithoutBounce(samples("Tab three"), down = false)
    assertThat(final).isWithin(0.5f).of(removed.top)
    assertThat(exists("Tab two")).isFalse()
  }

  @Test
  fun reorderedTabsSlideToTheirNewPlaces() {
    val state = showTabs(fourTabs)
    val firstTop = top("Tab one")
    val secondTop = top("Tab two")
    state.value = TabsUiState.Loaded(listOf(fourTabs[1], fourTabs[0]) + fourTabs.drop(2))
    framesUntil { top("Tab one") > firstTop + 0.5f }
    val one = mutableListOf(top("Tab one"))
    val two = mutableListOf(top("Tab two"))
    repeat(60) {
      clock.advanceTimeBy(16)
      composeRule.waitForIdle()
      one += top("Tab one")
      two += top("Tab two")
    }
    assertThat(assertMovesWithoutBounce(one, down = true)).isWithin(0.5f).of(secondTop)
    assertThat(assertMovesWithoutBounce(two, down = false)).isWithin(0.5f).of(firstTop)
  }
}
