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
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.graphics.Color
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.data.website.model.Website
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
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowSettings
import java.time.Duration
import javax.inject.Inject

/** The service draws one overlay window per head plus the trash, in the foreground. */
@HiltAndroidTest
class WebHeadServiceOverlayTest : LynketRobolectricSuite() {

  @Inject
  lateinit var eventBus: EventBus

  private val collectorScope = CoroutineScope(Dispatchers.Unconfined)

  @After
  fun cancelCollectors() = collectorScope.cancel()

  private fun intentFor(url: String) = Intent(application, WebHeadService::class.java).setData(Uri.parse(url))

  /** Every web head event sent on the bus from now on. */
  private fun recordWebHeadEvents(): List<Event.WebHeadEvent> {
    val events = mutableListOf<Event.WebHeadEvent>()
    collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
      eventBus.observe<Event.WebHeadEvent>().collect { events += it }
    }
    return events
  }

  /** A started service with one head per link. */
  private fun serviceWith(vararg urls: String): ServiceController<WebHeadService> {
    ShadowSettings.setCanDrawOverlays(true)
    val controller = Robolectric.buildService(WebHeadService::class.java, intentFor(urls.first()))
      .create()
      .startCommand(0, 1)
    urls.drop(1).forEachIndexed { i, url -> controller.withIntent(intentFor(url)).startCommand(0, i + 2) }
    shadowOf(Looper.getMainLooper()).idle()
    return controller
  }

  @Test
  fun a_link_adds_a_head_window_and_the_trash_window() {
    ShadowSettings.setCanDrawOverlays(true)
    val controller = Robolectric.buildService(WebHeadService::class.java, intentFor("https://a.example"))
      .create()
      .startCommand(0, 1)
    shadowOf(Looper.getMainLooper()).idle()
    val service = controller.get()

    assertThat(service.controller!!.size).isEqualTo(1)
    assertThat(service.overlayWindows).hasSize(2)
    assertThat(service.overlayWindows.all { it.isAdded }).isTrue()
    assertThat(shadowOf(service).lastForegroundNotification).isNotNull()
  }

  @Test
  fun the_same_link_twice_keeps_one_head() {
    ShadowSettings.setCanDrawOverlays(true)
    val controller = Robolectric.buildService(WebHeadService::class.java, intentFor("https://a.example"))
      .create()
      .startCommand(0, 1)
    controller.withIntent(intentFor("https://a.example")).startCommand(0, 2)
    controller.withIntent(intentFor("https://b.example")).startCommand(0, 3)
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(controller.get().controller!!.size).isEqualTo(2)
    assertThat(controller.get().overlayWindows).hasSize(3)
  }

  @Test
  fun destroying_the_service_removes_every_window() {
    ShadowSettings.setCanDrawOverlays(true)
    val controller = Robolectric.buildService(WebHeadService::class.java, intentFor("https://a.example"))
      .create()
      .startCommand(0, 1)
    shadowOf(Looper.getMainLooper()).idle()
    val windows = controller.get().overlayWindows.toList()

    controller.destroy()

    assertThat(windows.none { it.isAdded }).isTrue()
  }

  @Test
  fun closing_a_site_by_url_removes_its_head() {
    val controller = serviceWith("https://a.example", "https://b.example")

    eventBus.tryEmit(Event.WebHeadEvent.CloseRequested("https://a.example"))
    shadowOf(Looper.getMainLooper()).idle()

    val heads = controller.get().controller!!
    assertThat(heads.size).isEqualTo(1)
    assertThat(heads["https://a.example"]).isNull()
    assertThat(heads["https://b.example"]).isNotNull()
  }

  @Test
  fun an_event_sent_right_after_create_is_handled() {
    ShadowSettings.setCanDrawOverlays(true)
    val controller = Robolectric.buildService(WebHeadService::class.java, intentFor("https://a.example"))
    val before = eventBus.subscriptionCount.value

    // onCreate is called directly: ServiceController.create() runs the main looper after onCreate
    // (shadows-framework 4.17, ServiceController.create -> ShadowLooper.idleIfPaused), which would
    // hide a collector that subscribes one looper turn late.
    controller.get().onCreate()
    val afterCreate = eventBus.subscriptionCount.value
    controller.startCommand(0, 1)
    controller.withIntent(intentFor("https://b.example")).startCommand(0, 2)
    eventBus.tryEmit(Event.WebHeadEvent.CloseRequested("https://a.example"))
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(afterCreate).isEqualTo(before + 1)
    val heads = controller.get().controller!!
    assertThat(heads["https://a.example"]).isNull()
    assertThat(heads["https://b.example"]).isNotNull()
  }

  @Test
  fun a_closed_event_closes_no_head() {
    val controller = serviceWith("https://a.example", "https://b.example")

    eventBus.tryEmit(Event.WebHeadEvent.Closed("https://a.example"))
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(controller.get().controller!!.size).isEqualTo(2)
  }

  @Test
  fun no_event_is_handled_after_destroy() {
    val controller = serviceWith("https://a.example")
    val subscribed = eventBus.subscriptionCount.value

    controller.destroy()
    shadowOf(Looper.getMainLooper()).idle()
    eventBus.tryEmit(Event.WebHeadEvent.CloseRequested("https://a.example"))
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(subscribed).isAtLeast(1)
    assertThat(eventBus.subscriptionCount.value).isEqualTo(subscribed - 1)
  }

  @Test
  fun a_colour_change_recolours_every_head() {
    val controller = serviceWith("https://a.example", "https://b.example")
    val colour = 0xFF00C853.toInt()

    eventBus.tryEmit(Event.WebHeadEvent.ColourChanged(colour))
    shadowOf(Looper.getMainLooper()).idle()

    val heads = controller.get().controller!!.all
    assertThat(heads).hasSize(2)
    assertThat(heads.map { it.colour }.distinct()).containsExactly(Color(colour))
  }

  @Test
  fun removing_a_head_sends_closed() {
    val controller = serviceWith("https://a.example", "https://b.example")
    val events = recordWebHeadEvents()
    val heads = controller.get().controller!!

    heads.destroy(heads["https://a.example"]!!, receiveCallback = true)
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(events).containsExactly(Event.WebHeadEvent.Closed("https://a.example"))
  }

  @Test
  fun a_loaded_site_sends_website_updated() {
    val controller = serviceWith("https://a.example")
    val events = recordWebHeadEvents()
    val site = Website("https://a.example").apply { title = "A" }

    controller.get().onSiteLoaded("https://a.example", site)

    assertThat(events).containsExactly(Event.WebHeadEvent.WebsiteUpdated(site))
    assertThat(controller.get().controller!!["https://a.example"]!!.website.title).isEqualTo("A")
  }

  @Test
  fun a_pending_preload_stops_with_the_service() {
    val controller = Robolectric.buildService(WebHeadService::class.java).create()
    shadowOf(Looper.getMainLooper()).idle()
    val job = controller.get().deferPreload("https://a.example")

    controller.destroy()

    assertThat(job.isCancelled).isTrue()
  }

  @Test
  fun a_pending_preload_runs_after_the_delay() {
    val controller = Robolectric.buildService(WebHeadService::class.java).create()
    shadowOf(Looper.getMainLooper()).idle()
    val job = controller.get().deferPreload("https://a.example")

    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))

    assertThat(job.isCompleted).isTrue()
    assertThat(job.isCancelled).isFalse()
  }
}
