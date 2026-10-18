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

package arun.com.chromer.bubbles.webheads.ui.context

import android.content.Intent
import android.os.Looper
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants.EXTRA_KEY_WEBSITE
import arun.com.chromer.util.events.Event
import arun.com.chromer.util.events.EventBus
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import javax.inject.Inject

/**
 * The web head list sends a close request for each site it closes, and follows the head removals
 * and site updates the web head service sends. The activity is only created, not shown: an open
 * ModalBottomSheet never goes idle in Robolectric (see DialogActivitiesRecreateTest), and the
 * events do not need the sheet on screen.
 */
@HiltAndroidTest
class WebHeadContextActivityEventsTest : LynketRobolectricSuite() {

  @Inject
  lateinit var eventBus: EventBus

  private val collectorScope = CoroutineScope(Dispatchers.Unconfined)

  private val siteA = Website("https://a.example")
  private val siteB = Website("https://b.example")

  @After
  fun cancelCollectors() = collectorScope.cancel()

  private fun createSheet(): WebHeadContextActivity {
    val intent = Intent(application, WebHeadContextActivity::class.java)
      .putParcelableArrayListExtra(EXTRA_KEY_WEBSITE, arrayListOf(siteA, siteB))
    return Robolectric.buildActivity(WebHeadContextActivity::class.java, intent).create().get()
  }

  private fun closeRequests(): List<Event.WebHeadEvent.CloseRequested> {
    val events = mutableListOf<Event.WebHeadEvent.CloseRequested>()
    collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
      eventBus.observe<Event.WebHeadEvent.CloseRequested>().collect { events += it }
    }
    return events
  }

  @Test
  fun closingASiteSendsCloseRequested() {
    val sheet = createSheet()
    val requests = closeRequests()

    sheet.close(siteA)

    assertThat(requests).containsExactly(Event.WebHeadEvent.CloseRequested(siteA.url))
    assertThat(sheet.websites.map { it.url }).containsExactly(siteB.url)
  }

  @Test
  fun openingASiteWithCloseOnOpenSendsCloseRequested() {
    preferences.webHeadsCloseOnOpen(true)
    val sheet = createSheet()
    val requests = closeRequests()

    sheet.open(siteA)

    assertThat(requests).containsExactly(Event.WebHeadEvent.CloseRequested(siteA.url))

    preferences.webHeadsCloseOnOpen(false)
    val second = createSheet()
    second.open(siteB)

    assertThat(requests).hasSize(1)
  }

  @Test
  fun aClosedEventRemovesTheRow() {
    val sheet = createSheet()

    // Sent with no main-looper turn after create: the collector is already subscribed.
    eventBus.tryEmit(Event.WebHeadEvent.Closed(siteA.url))
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(sheet.websites.map { it.url }).containsExactly(siteB.url)
    assertThat(sheet.isFinishing).isFalse()
  }

  @Test
  fun theLastClosedEventFinishesTheSheet() {
    val sheet = createSheet()

    eventBus.tryEmit(Event.WebHeadEvent.Closed(siteA.url))
    eventBus.tryEmit(Event.WebHeadEvent.Closed(siteB.url))
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(sheet.websites).isEmpty()
    assertThat(sheet.isFinishing).isTrue()
  }

  @Test
  fun aWebsiteUpdatedEventReplacesTheRow() {
    val sheet = createSheet()
    val loaded = Website(siteB.url).apply { title = "Site B" }

    eventBus.tryEmit(Event.WebHeadEvent.WebsiteUpdated(loaded))
    eventBus.tryEmit(Event.WebHeadEvent.WebsiteUpdated(Website("https://unknown.example").apply { title = "X" }))
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(sheet.websites.map { it.url }).containsExactly(siteA.url, siteB.url).inOrder()
    assertThat(sheet.websites[1].title).isEqualTo("Site B")
    assertThat(sheet.websites[0].title).isNull()
  }
}
