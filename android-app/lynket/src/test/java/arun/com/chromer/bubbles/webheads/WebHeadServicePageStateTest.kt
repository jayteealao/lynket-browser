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

package arun.com.chromer.bubbles.webheads

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Looper
import android.util.Pair
import androidx.browser.customtabs.CustomTabsCallback
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.bubbles.webheads.motion.PAGE_STATE_LIMIT_MS
import arun.com.chromer.bubbles.webheads.motion.PageState
import arun.com.chromer.data.website.WebsiteLookup
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants.NO_COLOR
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowSettings
import java.time.Duration

/** Each head's page state through the real service: loading on open, then the first signal or the limit. */
@HiltAndroidTest
class WebHeadServicePageStateTest : LynketRobolectricSuite() {

  /** One lookup per address; a test emits into it to finish the site details fetch. */
  private val lookups = HashMap<String, MutableSharedFlow<WebsiteLookup>>()

  private val repository = mockk<WebsiteRepository>(relaxed = true) {
    every { lookUpWebsite(any(), any()) } answers { lookupOf(firstArg()) }
    every { getWebsiteRoundIconAndColor(any()) } returns Pair.create(mockk<Drawable>(), NO_COLOR)
  }

  @Synchronized
  private fun lookupOf(url: String) = lookups.getOrPut(url) { MutableSharedFlow(replay = 1) }

  private fun intentFor(url: String) = Intent(application, WebHeadService::class.java).setData(Uri.parse(url))

  /** A started service with one head per link, reading site details from [repository]. */
  private fun serviceWith(vararg urls: String): ServiceController<WebHeadService> {
    ShadowSettings.setCanDrawOverlays(true)
    val controller = Robolectric.buildService(WebHeadService::class.java, intentFor(urls.first())).create()
    controller.get().websiteRepository = repository
    controller.startCommand(0, 1)
    urls.drop(1).forEachIndexed { i, url -> controller.withIntent(intentFor(url)).startCommand(0, i + 2) }
    idle()
    return controller
  }

  private fun idle() = shadowOf(Looper.getMainLooper()).idle()

  private fun WebHeadService.head(url: String) = controller!![url]!!

  private fun finishLookup(url: String, fetchFailed: Boolean) {
    lookupOf(url).tryEmit(WebsiteLookup(Website(url).apply { title = "Page" }, fetchFailed))
  }

  /** The lookup is collected off the main thread and comes back to it; wait for that hop. */
  private fun awaitState(service: WebHeadService, url: String, expected: PageState) {
    val deadline = System.currentTimeMillis() + 5_000
    while (service.head(url).pageState != expected && System.currentTimeMillis() < deadline) {
      Thread.sleep(10)
      idle()
    }
    assertThat(service.head(url).pageState).isEqualTo(expected)
  }

  @Test
  fun a_new_head_is_loading() {
    val service = serviceWith(A).get()

    assertThat(service.head(A).pageState).isEqualTo(PageState.Loading)
  }

  @Test
  fun the_site_details_lookup_turns_the_head_loaded() {
    val service = serviceWith(A).get()

    finishLookup(A, fetchFailed = false)

    awaitState(service, A, PageState.Loaded)
  }

  @Test
  fun a_finished_navigation_of_the_opened_head_turns_it_loaded() {
    val service = serviceWith(A).get()

    service.onHeadClick(service.head(A))
    idle()
    service.onTabNavigation(CustomTabsCallback.NAVIGATION_FINISHED)

    assertThat(service.head(A).pageState).isEqualTo(PageState.Loaded)
  }

  @Test
  fun a_failed_site_details_fetch_turns_the_head_failed() {
    val service = serviceWith(A).get()

    finishLookup(A, fetchFailed = true)

    awaitState(service, A, PageState.Failed)
  }

  @Test
  fun a_failed_navigation_of_the_opened_head_turns_it_failed() {
    val service = serviceWith(A).get()

    service.onHeadClick(service.head(A))
    idle()
    service.onTabNavigation(CustomTabsCallback.NAVIGATION_FAILED)

    assertThat(service.head(A).pageState).isEqualTo(PageState.Failed)
  }

  @Test
  fun other_navigation_events_change_nothing() {
    val service = serviceWith(A).get()

    service.onHeadClick(service.head(A))
    idle()
    listOf(
      CustomTabsCallback.NAVIGATION_STARTED,
      CustomTabsCallback.NAVIGATION_ABORTED,
      CustomTabsCallback.TAB_SHOWN,
      CustomTabsCallback.TAB_HIDDEN,
    ).forEach(service::onTabNavigation)

    assertThat(service.head(A).pageState).isEqualTo(PageState.Loading)
  }

  @Test
  fun with_no_signal_the_head_turns_unknown_at_the_limit() {
    val service = serviceWith(A).get()

    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PAGE_STATE_LIMIT_MS - 1_000))
    assertThat(service.head(A).pageState).isEqualTo(PageState.Loading)

    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))
    assertThat(service.head(A).pageState).isEqualTo(PageState.Unknown)
  }

  @Test
  fun of_two_heads_only_the_one_whose_lookup_ends_turns_loaded() {
    val service = serviceWith(A, B).get()

    finishLookup(B, fetchFailed = false)

    awaitState(service, B, PageState.Loaded)
    assertThat(service.head(A).pageState).isEqualTo(PageState.Loading)
  }

  @Test
  fun a_closed_heads_late_lookup_changes_no_other_head() {
    val service = serviceWith(A, B).get()
    service.onClose(service.head(A))
    idle()
    assertThat(service.controller!![A]).isNull()

    finishLookup(A, fetchFailed = false)
    Thread.sleep(200)
    idle()

    assertThat(service.head(B).pageState).isEqualTo(PageState.Loading)
  }

  private companion object {
    const val A = "https://a.example"
    const val B = "https://b.example"
  }
}
