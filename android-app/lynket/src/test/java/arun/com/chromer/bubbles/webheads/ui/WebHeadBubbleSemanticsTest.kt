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
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.bubbles.webheads.motion.PageState
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadState
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader says for a web head: its title, its place in the stack, the number of heads
 * and whether its page is loading, loaded or did not load; and that the finish is said once.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WebHeadBubbleSemanticsTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  /** As in the service, so the head is a button with its actions. */
  private val gestures = object : WebHeadGestures {
    override fun onDown(head: WebHeadState, raw: Offset) = Unit
    override fun onMove(head: WebHeadState, raw: Offset) = Unit
    override fun onUp(head: WebHeadState, velocity: Offset, raw: Offset, tap: Boolean) = Unit
    override fun onLongPress(head: WebHeadState) = Unit
  }

  private val politeLiveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
  private val noLiveRegion = SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion)

  private fun head(
    url: String,
    title: String?,
    position: Int,
    count: Int,
    pageState: PageState = PageState.Unknown,
  ) = WebHeadState(url).apply {
    website = Website(url).apply { this.title = title }
    isMaster = position == 1
    revealed = true
    badgeCount = count
    stackPosition = position
    this.pageState = pageState
  }

  private fun exampleHead(pageState: PageState) =
    head("https://example.com/", "Example Domain", position = 1, count = 3, pageState = pageState)

  private fun theHead() = composeRule.onNodeWithTag(WEB_HEAD_TEST_TAG)

  private fun show(vararg heads: WebHeadState) {
    composeRule.setContent {
      ChromerTheme {
        Column { heads.forEach { WebHeadBubble(it, WebHeadMotion.HEAD_DP.dp, gestures) } }
      }
    }
  }

  @Test
  fun the_front_head_says_its_title_place_and_count() {
    val title = "Android 17 beta: what changes for floating apps"
    // Three heads as the controller numbers them: the newest is at the front.
    show(
      head("https://example.com/front", title, position = 1, count = 3),
      head("https://example.com/next", "Release notes", position = 2, count = 3),
      head("https://example.com/back", "Example Domain", position = 3, count = 3),
    )
    val nodes = composeRule.onAllNodesWithTag(WEB_HEAD_TEST_TAG)
    nodes[0].assertContentDescriptionEquals("Web head: $title, 1 of 3")
    nodes[1].assertContentDescriptionEquals("Web head: Release notes, 2 of 3")
    nodes[2].assertContentDescriptionEquals("Web head: Example Domain, 3 of 3")
  }

  @Test
  fun a_long_title_is_said_in_full_with_one_of_six() {
    val title = "Floating heads on large screens: how a flick, a rotation and a screen reader " +
      "should all behave in the same apps: a guide"
    assertThat(title.length).isEqualTo(120)
    show(head("https://example.com/long", title, position = 1, count = 6))
    composeRule.onNodeWithTag(WEB_HEAD_TEST_TAG).assertContentDescriptionEquals("Web head: $title, 1 of 6")
  }

  @Test
  fun a_head_without_a_title_says_its_address() {
    show(head("https://example.com", title = null, position = 1, count = 1))
    composeRule.onNodeWithTag(WEB_HEAD_TEST_TAG).assertContentDescriptionEquals("Web head: https://example.com, 1 of 1")
  }

  @Test
  fun a_loading_head_says_loading() {
    show(exampleHead(PageState.Loading))
    theHead().assertContentDescriptionEquals("Web head: Example Domain, 1 of 3, loading")
  }

  @Test
  fun a_loaded_head_says_loaded() {
    show(exampleHead(PageState.Loaded))
    theHead().assertContentDescriptionEquals("Web head: Example Domain, 1 of 3, loaded")
  }

  @Test
  fun a_failed_head_says_the_page_did_not_load() {
    show(exampleHead(PageState.Failed))
    theHead().assertContentDescriptionEquals("Web head: Example Domain, 1 of 3, page did not load")
  }

  @Test
  fun an_unknown_head_adds_nothing() {
    show(exampleHead(PageState.Unknown))
    theHead().assertContentDescriptionEquals("Web head: Example Domain, 1 of 3")
  }

  @Test
  fun a_long_loading_title_is_said_in_full_with_its_state() {
    val title = "Floating heads on large screens: how a flick, a rotation and a screen reader " +
      "should all behave in the same apps: a guide"
    show(head("https://example.com/long", title, position = 1, count = 6, pageState = PageState.Loading))
    theHead().assertContentDescriptionEquals("Web head: $title, 1 of 6, loading")
  }

  @Test
  fun a_head_shown_loading_is_not_a_live_region() {
    show(exampleHead(PageState.Loading))
    theHead().assert(noLiveRegion)
  }

  @Test
  fun a_finished_load_is_said_once_then_the_live_region_goes() {
    val head = exampleHead(PageState.Loading)
    show(head)
    composeRule.runOnIdle { head.pageState = PageState.Loaded }
    theHead().assert(politeLiveRegion).assertContentDescriptionEquals("Web head: Example Domain, 1 of 3, loaded")
    composeRule.mainClock.advanceTimeBy(PAGE_CHANGE_ANNOUNCE_MS + 100)
    theHead().assert(noLiveRegion).assertContentDescriptionEquals("Web head: Example Domain, 1 of 3, loaded")
  }

  @Test
  fun a_failed_load_is_said_once_then_the_live_region_goes() {
    val head = exampleHead(PageState.Loading)
    show(head)
    composeRule.runOnIdle { head.pageState = PageState.Failed }
    theHead().assert(politeLiveRegion).assertContentDescriptionEquals("Web head: Example Domain, 1 of 3, page did not load")
    composeRule.mainClock.advanceTimeBy(PAGE_CHANGE_ANNOUNCE_MS + 100)
    theHead().assert(noLiveRegion)
  }

  @Test
  fun a_load_that_runs_out_of_time_is_not_said() {
    val head = exampleHead(PageState.Loading)
    show(head)
    composeRule.runOnIdle { head.pageState = PageState.Unknown }
    theHead().assert(noLiveRegion).assertContentDescriptionEquals("Web head: Example Domain, 1 of 3")
  }

  @Test
  fun a_load_that_starts_is_not_said() {
    val head = exampleHead(PageState.Unknown)
    show(head)
    composeRule.runOnIdle { head.pageState = PageState.Loading }
    theHead().assert(noLiveRegion).assertContentDescriptionEquals("Web head: Example Domain, 1 of 3, loading")
  }

  @Test
  fun a_new_title_after_the_finish_is_not_said() {
    val head = exampleHead(PageState.Loading)
    show(head)
    composeRule.runOnIdle { head.pageState = PageState.Loaded }
    theHead().assert(politeLiveRegion)
    composeRule.mainClock.advanceTimeBy(PAGE_CHANGE_ANNOUNCE_MS + 100)
    theHead().assert(noLiveRegion)
    composeRule.runOnIdle { head.website = Website(head.url).apply { title = "Example Domain, again" } }
    theHead().assert(noLiveRegion).assertContentDescriptionEquals("Web head: Example Domain, again, 1 of 3, loaded")
  }

  @Test
  fun a_closing_head_whose_page_finishes_is_not_said() {
    val head = exampleHead(PageState.Loading)
    show(head)
    composeRule.runOnIdle {
      head.closing = true
      head.pageState = PageState.Loaded
    }
    theHead().assert(noLiveRegion)
  }
}
