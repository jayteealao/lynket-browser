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

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants.EXTRA_KEY_BUBBLE_REFUSAL
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
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
import org.robolectric.shadows.ShadowToast
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

    // The sites load on the IO dispatcher, so a WebsiteUpdated for either head can arrive at any
    // point; only the Closed events belong to this check.
    assertThat(events.filterIsInstance<Event.WebHeadEvent.Closed>())
      .containsExactly(Event.WebHeadEvent.Closed("https://a.example"))
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

  /** Every activity started so far, after the posted work of the main thread ran. */
  private fun startedActivities(): List<Intent> {
    shadowOf(Looper.getMainLooper()).idle()
    return generateSequence { shadowOf(application).nextStartedActivity }.toList()
  }

  private fun List<Intent>.settingsPages() =
    filter { it.action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION }

  @Test
  fun with_the_permission_off_a_link_goes_to_the_refusal_path_and_the_service_stops() {
    ShadowSettings.setCanDrawOverlays(false)
    val intent = intentFor("https://a.example").putExtra(EXTRA_KEY_INCOGNITO, true)
    val controller = Robolectric.buildService(WebHeadService::class.java, intent).create()
    val service = controller.get()

    val result = service.onStartCommand(intent, 0, 1)
    val started = startedActivities()

    assertThat(result).isEqualTo(Service.START_NOT_STICKY)
    assertThat(started.settingsPages()).isEmpty()
    val tab = started.single { it.dataString == "https://a.example" }
    assertThat(tab.getStringExtra(EXTRA_KEY_BUBBLE_REFUSAL)).isEqualTo("OVERLAY_OFF")
    assertThat(tab.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
    assertThat(service.controller).isNull()
    assertThat(service.overlayWindows).isEmpty()
    assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
  }

  @Test
  fun a_revocation_after_create_refuses_the_link_and_adds_no_head() {
    ShadowSettings.setCanDrawOverlays(true)
    val controller = Robolectric.buildService(WebHeadService::class.java).create()
    val service = controller.get()
    assertThat(service.controller).isNotNull()

    ShadowSettings.setCanDrawOverlays(false)
    val result = service.onStartCommand(intentFor("https://b.example"), 0, 1)
    val started = startedActivities()

    assertThat(result).isEqualTo(Service.START_NOT_STICKY)
    assertThat(service.controller!!.size).isEqualTo(0)
    assertThat(started.settingsPages()).isEmpty()
    val tab = started.single { it.dataString == "https://b.example" }
    assertThat(tab.getStringExtra(EXTRA_KEY_BUBBLE_REFUSAL)).isEqualTo("OVERLAY_OFF")
    assertThat(shadowOf(service).isStoppedBySelf).isTrue()
  }

  @Test
  fun with_the_permission_off_a_restart_without_a_link_stops_and_opens_nothing() {
    ShadowSettings.setCanDrawOverlays(false)
    val controller = Robolectric.buildService(WebHeadService::class.java).create()
    val service = controller.get()

    val result = service.onStartCommand(null, 0, 1)

    assertThat(result).isEqualTo(Service.START_NOT_STICKY)
    assertThat(startedActivities()).isEmpty()
    assertThat(shadowOf(service).isStoppedBySelf).isTrue()
  }

  @Test
  fun with_the_permission_on_a_link_keeps_the_service_sticky() {
    ShadowSettings.setCanDrawOverlays(true)
    val intent = intentFor("https://a.example")
    val controller = Robolectric.buildService(WebHeadService::class.java, intent).create()
    val service = controller.get()

    val result = service.onStartCommand(intent, 0, 1)

    assertThat(result).isEqualTo(Service.START_STICKY)
    assertThat(service.controller!!.size).isEqualTo(1)
    assertThat(startedActivities()).isEmpty()
  }

  /** The web heads notification that is posted now, or null when none is. */
  private fun webHeadNotification(service: WebHeadService) =
    shadowOf(service.getSystemService(NotificationManager::class.java)).getNotification(1)

  @Test
  fun a_restart_with_no_head_stops_and_removes_the_notification() {
    ShadowSettings.setCanDrawOverlays(true)
    val service = Robolectric.buildService(WebHeadService::class.java).create().get()
    assertThat(webHeadNotification(service)).isNotNull()
    assertThat(service.controller!!.size).isEqualTo(0)

    // Android restarts a killed sticky service with a null intent.
    val result = service.onStartCommand(null, Service.START_FLAG_RETRY, 1)

    assertThat(result).isEqualTo(Service.START_NOT_STICKY)
    assertThat(webHeadNotification(service)).isNull()
    assertThat(shadowOf(service).isForegroundStopped).isTrue()
    assertThat(shadowOf(service).notificationShouldRemoved).isTrue()
    assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    assertThat(startedActivities()).isEmpty()
    assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
  }

  @Test
  fun a_start_without_a_link_and_no_head_stops() {
    ShadowSettings.setCanDrawOverlays(true)
    val service = Robolectric.buildService(WebHeadService::class.java).create().get()

    val result = service.onStartCommand(Intent(application, WebHeadService::class.java), 0, 1)

    assertThat(result).isEqualTo(Service.START_NOT_STICKY)
    assertThat(webHeadNotification(service)).isNull()
    assertThat(shadowOf(service).isStoppedBySelf).isTrue()
  }

  @Test
  fun a_start_without_a_link_keeps_the_heads_that_show() {
    val controller = serviceWith("https://a.example")
    val service = controller.get()

    val result = service.onStartCommand(null, 0, 2)

    assertThat(result).isEqualTo(Service.START_STICKY)
    assertThat(service.controller!!.size).isEqualTo(1)
    assertThat(webHeadNotification(service)).isNotNull()
    assertThat(shadowOf(service).isStoppedBySelf).isFalse()
  }

  @Test
  fun an_empty_link_with_no_head_stops() {
    ShadowSettings.setCanDrawOverlays(true)
    val service = Robolectric.buildService(WebHeadService::class.java).create().get()

    val result = service.onStartCommand(
      Intent(application, WebHeadService::class.java).setData(Uri.parse("")),
      0,
      1,
    )

    assertThat(result).isEqualTo(Service.START_NOT_STICKY)
    assertThat(webHeadNotification(service)).isNull()
    assertThat(shadowOf(service).isStoppedBySelf).isTrue()
  }

  @Test
  fun an_incognito_head_is_never_hinted_to_the_browser() {
    clearPreferences()
    ShadowSettings.setCanDrawOverlays(true)
    val controller = Robolectric.buildService(
      WebHeadService::class.java,
      intentFor("https://a.example").putExtra(EXTRA_KEY_INCOGNITO, true),
    ).create().startCommand(0, 1)
    controller.withIntent(intentFor("https://b.example")).startCommand(0, 2)
    shadowOf(Looper.getMainLooper()).idle()
    val service = controller.get()
    val heads = service.controller!!

    assertThat(heads["https://a.example"]!!.incognito).isTrue()
    assertThat(service.preloads(heads["https://a.example"]!!)).isFalse()
    assertThat(service.preloads(heads["https://b.example"]!!)).isTrue()

    // Aggressive loading applies in web heads mode, where the page loads in a background tab.
    preferences.browsingMode(BrowsingMode.WebHeads)
    preferences.aggressiveLoading(true)
    assertThat(service.preloads(heads["https://b.example"]!!)).isFalse()
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

  @Test
  fun the_trash_window_takes_touches_only_while_a_head_is_dragged() {
    val service = serviceWith("https://a.example").get()
    val trash = service.overlayWindows.last()
    val notTouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    assertThat(trash.params.flags and notTouchable).isEqualTo(notTouchable)

    val heads = service.controller!!
    val head = heads["https://a.example"]!!
    val downX = head.windowX + 50f
    val downY = head.windowY + 50f
    heads.onTouchDown(head, downX, downY)
    heads.onTouchMove(head, downX - 150f, downY + 150f)
    shadowOf(Looper.getMainLooper()).idle()
    heads.onTouchMove(head, downX - 300f, downY + 300f)
    shadowOf(Looper.getMainLooper()).idle()
    assertThat(trash.params.flags and notTouchable).isEqualTo(0)
    assertThat(trash.touchable).isTrue()

    heads.onTouchUp(head, 0f, 0f, downX - 300f, downY + 300f)
    shadowOf(Looper.getMainLooper()).idle()
    assertThat(trash.params.flags and notTouchable).isEqualTo(notTouchable)
  }

  @Test
  fun locking_and_leaving_the_trash_ticks_on_the_head_window() {
    val service = serviceWith("https://a.example").get()
    val headWindow = service.overlayWindows.first()
    val heads = service.controller!!
    val head = heads["https://a.example"]!!
    val downX = head.windowX + heads.headWindowPx / 2f
    val downY = head.windowY + heads.headWindowPx / 2f
    heads.onTouchDown(head, downX, downY)
    heads.onTouchMove(head, downX - 150f, downY + 150f)
    shadowOf(Looper.getMainLooper()).idle()
    heads.onTouchMove(head, heads.trashX + heads.trashWindowPx / 2f, heads.trashY + heads.trashWindowPx / 2f)
    shadowOf(Looper.getMainLooper()).idle()
    assertThat(shadowOf(headWindow.view).lastHapticFeedbackPerformed()).isEqualTo(HapticFeedbackConstants.CONFIRM)

    val middleX = heads.dispWidth / 2f
    val middleY = heads.dispHeight / 2f
    heads.onTouchMove(head, middleX, middleY)
    shadowOf(Looper.getMainLooper()).idle()
    assertThat(shadowOf(headWindow.view).lastHapticFeedbackPerformed()).isEqualTo(HapticFeedbackConstants.CLOCK_TICK)

    heads.onTouchUp(head, 0f, 0f, middleX, middleY)
    shadowOf(Looper.getMainLooper()).idle()
    assertThat(shadowOf(headWindow.view).lastHapticFeedbackPerformed()).isEqualTo(HapticFeedbackConstants.CLOCK_TICK)
  }
}
