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

package arun.com.chromer.browsing.webview

import android.webkit.WebViewClient.ERROR_BAD_URL
import android.webkit.WebViewClient.ERROR_CONNECT
import android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
import android.webkit.WebViewClient.ERROR_HOST_LOOKUP
import android.webkit.WebViewClient.ERROR_TIMEOUT
import android.webkit.WebViewClient.ERROR_UNKNOWN
import arun.com.chromer.ui.webview.LoadFailure
import arun.com.chromer.ui.webview.PageLoad
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * A page load goes from loading to loaded, or to failed with one cause; a finish after a failure
 * keeps the failure, and only a new start clears it.
 */
class PageLoadTrackerTest {
  private var online = true
  private val changes = mutableListOf<PageLoad>()
  private val tracker = PageLoadTracker(isOnline = { online }, onChange = { changes += it })
  private val address = "https://example.com/a"

  @Test
  fun theCauseFollowsTheErrorCodeAndTheNetwork() {
    val table = listOf(
      Triple(ERROR_HOST_LOOKUP, false, LoadFailure.Offline),
      Triple(ERROR_HOST_LOOKUP, true, LoadFailure.CannotReach),
      Triple(ERROR_CONNECT, true, LoadFailure.CannotReach),
      Triple(ERROR_CONNECT, false, LoadFailure.Offline),
      Triple(ERROR_TIMEOUT, true, LoadFailure.CannotReach),
      Triple(ERROR_TIMEOUT, false, LoadFailure.Offline),
      Triple(ERROR_UNKNOWN, true, LoadFailure.General),
      Triple(ERROR_UNKNOWN, false, LoadFailure.General),
      Triple(ERROR_FAILED_SSL_HANDSHAKE, true, LoadFailure.General),
      Triple(ERROR_BAD_URL, true, LoadFailure.General),
    )
    table.forEach { (code, online, cause) ->
      assertWithMessage("code $code online $online").that(PageLoadTracker.causeOf(code, online)).isEqualTo(cause)
    }
  }

  @Test
  fun aStartedPageIsLoading() {
    tracker.started(address)

    assertThat(tracker.state).isEqualTo(PageLoad.Loading)
  }

  @Test
  fun aStartedPageThatFinishesIsLoaded() {
    tracker.started(address)
    tracker.finished(address)

    assertThat(tracker.state).isEqualTo(PageLoad.Loaded)
    assertThat(changes).containsExactly(PageLoad.Loaded)
  }

  @Test
  fun aPageErrorFailsThePageWithItsCauseAndTheFailingAddress() {
    online = false
    tracker.started(address)
    tracker.mainFrameError(ERROR_HOST_LOOKUP, "https://example.com/b")

    assertThat(tracker.state).isEqualTo(PageLoad.Failed(LoadFailure.Offline, "https://example.com/b"))
  }

  @Test
  fun aFinishAfterAFailureKeepsTheFailure() {
    tracker.started(address)
    tracker.mainFrameError(ERROR_CONNECT, address)
    tracker.finished(address)

    assertThat(tracker.state).isEqualTo(PageLoad.Failed(LoadFailure.CannotReach, address))
  }

  @Test
  fun aCertificateErrorFailsThePage() {
    tracker.started(address)
    tracker.certificateError(address)

    assertThat(tracker.state).isEqualTo(PageLoad.Failed(LoadFailure.Certificate, address))
  }

  @Test
  fun aSecondErrorKeepsTheFirstCause() {
    tracker.started(address)
    tracker.certificateError(address)
    tracker.mainFrameError(ERROR_UNKNOWN, address)

    assertThat(tracker.state).isEqualTo(PageLoad.Failed(LoadFailure.Certificate, address))
  }

  @Test
  fun aNewStartAfterAFailureLoadsAgain() {
    tracker.started(address)
    tracker.mainFrameError(ERROR_TIMEOUT, address)
    tracker.started(address)

    assertThat(tracker.state).isEqualTo(PageLoad.Loading)

    tracker.finished(address)

    assertThat(tracker.state).isEqualTo(PageLoad.Loaded)
  }

  @Test
  fun aRepeatedStateIsNotReported() {
    tracker.started(address)
    tracker.started(address)
    tracker.finished(address)
    tracker.finished(address)
    tracker.started(address)
    tracker.mainFrameError(ERROR_UNKNOWN, address)
    tracker.mainFrameError(ERROR_UNKNOWN, address)

    assertThat(changes)
      .containsExactly(PageLoad.Loaded, PageLoad.Loading, PageLoad.Failed(LoadFailure.General, address))
      .inOrder()
  }
}
