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

import arun.com.chromer.bubbles.webheads.motion.PageState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** What a head shows for each page state, at the front and behind it, with and without motion. */
class WebHeadRingTest {

  private fun look(state: PageState, front: Boolean = true, reduced: Boolean = false, closing: Boolean = false) =
    headLook(state, isFront = front, reducedMotion = reduced, closing = closing)

  private val atRest = HeadLook(Ring.Solid, turning = false, faded = false, warning = false)

  @Test
  fun anUnknownPageShowsTheHeadOfToday() {
    assertThat(look(PageState.Unknown)).isEqualTo(atRest)
    assertThat(look(PageState.Unknown, front = false)).isEqualTo(atRest)
    assertThat(look(PageState.Unknown, reduced = true)).isEqualTo(atRest)
  }

  @Test
  fun aLoadingFrontHeadTurnsItsArcAndFadesItsFill() {
    assertThat(look(PageState.Loading)).isEqualTo(HeadLook(Ring.Arc, turning = true, faded = true, warning = false))
  }

  @Test
  fun aLoadingHeadBehindTheFrontShowsAStillArcAndAFadedFill() {
    assertThat(look(PageState.Loading, front = false)).isEqualTo(HeadLook(Ring.Arc, turning = false, faded = true, warning = false))
  }

  @Test
  fun withReducedMotionALoadingHeadShowsTheTrackAndAFadedFillAndNothingTurns() {
    val expected = HeadLook(Ring.Track, turning = false, faded = true, warning = false)
    assertThat(look(PageState.Loading, reduced = true)).isEqualTo(expected)
    assertThat(look(PageState.Loading, front = false, reduced = true)).isEqualTo(expected)
  }

  @Test
  fun aLoadedPageShowsTheHeadOfToday() {
    assertThat(look(PageState.Loaded)).isEqualTo(atRest)
    assertThat(look(PageState.Loaded, front = false)).isEqualTo(atRest)
  }

  @Test
  fun aFailedPageStopsTheRingKeepsTheFillFullAndShowsTheWarningMark() {
    val expected = HeadLook(Ring.Track, turning = false, faded = false, warning = true)
    assertThat(look(PageState.Failed)).isEqualTo(expected)
    assertThat(look(PageState.Failed, front = false)).isEqualTo(expected)
    assertThat(look(PageState.Failed, reduced = true)).isEqualTo(expected)
  }

  @Test
  fun aHeadClosingToTheTrashKeepsItsLookInEveryPageState() {
    for (state in PageState.entries) {
      for (front in listOf(true, false)) {
        for (reduced in listOf(true, false)) {
          val closing = look(state, front, reduced, closing = true)
          assertThat(closing).isEqualTo(atRest)
          assertThat(closing.faded).isFalse()
          assertThat(closing.warning).isFalse()
        }
      }
    }
  }

  @Test
  fun theRingClosesUnderThreeHundredMillisecondsAndTurnsOnceInAboutASecond() {
    assertThat(RING_CLOSE_MS).isLessThan(300)
    assertThat(RING_TURN_MS).isEqualTo(1_100)
    assertThat(LOADING_FILL_ALPHA).isEqualTo(0.45f)
  }
}
