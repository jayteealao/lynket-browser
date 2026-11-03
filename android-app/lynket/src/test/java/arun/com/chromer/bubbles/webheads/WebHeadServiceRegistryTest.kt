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
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.data.preferences.BrowsingMode
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
import javax.inject.Inject

/** The service keeps its open heads in the one list of open bubbles, and each test reads that list. */
@HiltAndroidTest
class WebHeadServiceRegistryTest : LynketRobolectricSuite() {

  @Inject
  lateinit var registry: BubbleRegistry

  @Inject
  lateinit var eventBus: EventBus

  private val collectorScope = CoroutineScope(Dispatchers.Unconfined)

  @After
  fun cancelCollectors() = collectorScope.cancel()

  private fun intentFor(url: String) = Intent(application, WebHeadService::class.java).setData(Uri.parse(url))

  private fun incognitoIntentFor(url: String) = intentFor(url).putExtra(EXTRA_KEY_INCOGNITO, true)

  /** A started service with one head per link. */
  private fun serviceWith(vararg urls: String): ServiceController<WebHeadService> {
    ShadowSettings.setCanDrawOverlays(true)
    val controller = Robolectric.buildService(WebHeadService::class.java, intentFor(urls.first()))
      .create()
      .startCommand(0, 1)
    urls.drop(1).forEachIndexed { i, url -> controller.withIntent(intentFor(url)).startCommand(0, i + 2) }
    idle()
    return controller
  }

  private fun idle() = shadowOf(Looper.getMainLooper()).idle()

  private fun listedUrls() = registry.list(BrowsingMode.WebHeads).map { it.url }

  private fun webHeadKey(url: String, incognito: Boolean = false) =
    BubbleKey.of(BrowsingMode.WebHeads, url, incognito)

  private fun assertClosedOnPurpose(url: String) {
    assertThat(listedUrls()).doesNotContain(url)
    assertThat(registry.isClosedOnPurpose(webHeadKey(url))).isTrue()
  }

  private fun closeFromTheList(url: String) {
    eventBus.tryEmit(Event.WebHeadEvent.CloseRequested(url))
    idle()
  }

  @Test
  fun three_links_opened_in_turn_are_listed_in_open_order_as_web_heads_only() {
    serviceWith(A, B, C)

    assertThat(listedUrls()).containsExactly(A, B, C).inOrder()
    assertThat(registry.list(BrowsingMode.NativeBubbles)).isEmpty()
  }

  @Test
  fun the_same_link_twice_keeps_one_entry_and_its_place() {
    serviceWith(A, B, A)

    assertThat(listedUrls()).containsExactly(A, B).inOrder()
  }

  @Test
  fun an_incognito_link_is_listed_as_incognito_and_prints_no_address() {
    val controller = serviceWith(A)

    controller.withIntent(incognitoIntentFor(SECRET)).startCommand(0, 2)
    idle()

    val entry = registry.find(BrowsingMode.WebHeads, SECRET, true)
    assertThat(entry).isNotNull()
    assertThat(entry!!.incognito).isTrue()
    assertThat(entry.toString()).doesNotContain("secret.example")
  }

  @Test
  fun a_link_sent_normally_and_then_in_incognito_stays_one_entry_as_it_stays_one_head() {
    val controller = serviceWith(A)

    controller.withIntent(incognitoIntentFor(A)).startCommand(0, 2)
    idle()

    assertThat(controller.get().controller!!.size).isEqualTo(1)
    assertThat(registry.list(BrowsingMode.WebHeads).map { it.url to it.incognito })
      .containsExactly(A to false)
  }

  @Test
  fun a_start_with_the_overlay_permission_off_lists_nothing() {
    ShadowSettings.setCanDrawOverlays(false)
    val intent = intentFor(A)
    val service = Robolectric.buildService(WebHeadService::class.java, intent).create().get()

    service.onStartCommand(intent, 0, 1)
    idle()

    assertThat(listedUrls()).isEmpty()
  }

  @Test
  fun a_start_with_no_link_lists_nothing() {
    ShadowSettings.setCanDrawOverlays(true)
    val service = Robolectric.buildService(WebHeadService::class.java).create().get()

    service.onStartCommand(null, 0, 1)
    idle()

    assertThat(listedUrls()).isEmpty()
  }

  @Test
  fun a_close_from_the_list_takes_the_link_off_and_marks_it() {
    serviceWith(A, B)

    closeFromTheList(A)

    assertClosedOnPurpose(A)
  }

  @Test
  fun the_spoken_close_takes_the_link_off_and_marks_it() {
    val service = serviceWith(A, B).get()

    service.onClose(service.controller!![A]!!)
    idle()

    assertClosedOnPurpose(A)
  }

  @Test
  fun a_tap_with_close_on_open_takes_the_link_off_and_marks_it() {
    val service = serviceWith(A, B).get()
    preferences.webHeadsCloseOnOpen(true)

    service.onHeadClick(service.controller!![A]!!)
    idle()

    assertClosedOnPurpose(A)
    preferences.webHeadsCloseOnOpen(false)
  }

  @Test
  fun a_close_keeps_the_other_heads_in_their_order() {
    serviceWith(A, B, C)

    closeFromTheList(B)

    assertThat(listedUrls()).containsExactly(A, C).inOrder()
    assertThat(registry.isClosedOnPurpose(webHeadKey(A))).isFalse()
    assertThat(registry.isClosedOnPurpose(webHeadKey(C))).isFalse()
  }

  @Test
  fun the_last_head_closed_leaves_the_list_empty_and_its_marker_set() {
    serviceWith(A)

    closeFromTheList(A)

    assertThat(listedUrls()).isEmpty()
    assertThat(registry.isClosedOnPurpose(webHeadKey(A))).isTrue()
  }

  @Test
  fun a_closed_incognito_head_reaches_its_own_entry() {
    val controller = serviceWith(A)
    controller.withIntent(incognitoIntentFor(SECRET)).startCommand(0, 2)
    idle()

    closeFromTheList(SECRET)

    assertThat(registry.find(BrowsingMode.WebHeads, SECRET, true)).isNull()
    assertThat(registry.isClosedOnPurpose(webHeadKey(SECRET, incognito = true))).isTrue()
    assertThat(registry.isClosedOnPurpose(webHeadKey(SECRET))).isFalse()
  }

  @Test
  fun a_link_opened_again_after_its_close_goes_last_and_is_not_marked() {
    val controller = serviceWith(A, B)
    closeFromTheList(A)

    controller.withIntent(intentFor(A)).startCommand(0, 3)
    idle()

    assertThat(listedUrls()).containsExactly(B, A).inOrder()
    assertThat(registry.isClosedOnPurpose(webHeadKey(A))).isFalse()
  }

  @Test
  fun closing_a_head_leaves_a_native_bubble_of_the_same_link_alone() {
    serviceWith(A, B)
    val native = registry.add(BrowsingMode.NativeBubbles, A, false)

    closeFromTheList(A)

    assertThat(registry.list(BrowsingMode.NativeBubbles)).containsExactly(native)
    assertThat(registry.isClosedOnPurpose(native.key)).isFalse()
  }

  @Test
  fun the_entry_is_gone_by_the_time_closed_is_sent() {
    serviceWith(A, B)
    val listedAtClosed = mutableListOf<List<String>>()
    collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
      eventBus.observe<Event.WebHeadEvent>().collect { event ->
        if (event is Event.WebHeadEvent.Closed) listedAtClosed += listedUrls()
      }
    }

    closeFromTheList(A)

    assertThat(listedAtClosed).containsExactly(listOf(B))
  }

  @Test
  fun a_stopped_service_leaves_no_web_head_entry() {
    serviceWith(A, B, C).destroy()

    assertThat(listedUrls()).isEmpty()
  }

  @Test
  fun a_stop_marks_none_of_the_heads_as_closed_on_purpose() {
    serviceWith(A, B, C).destroy()

    listOf(A, B, C).forEach { assertThat(registry.isClosedOnPurpose(webHeadKey(it))).isFalse() }
  }

  @Test
  fun a_stop_leaves_the_native_bubbles_listed() {
    val controller = serviceWith(A)
    val native = registry.add(BrowsingMode.NativeBubbles, A, false)

    controller.destroy()

    assertThat(registry.list(BrowsingMode.NativeBubbles)).containsExactly(native)
  }

  @Test
  fun a_head_closed_before_the_stop_keeps_its_marker() {
    val controller = serviceWith(A, B)
    closeFromTheList(A)

    controller.destroy()

    assertThat(registry.isClosedOnPurpose(webHeadKey(A))).isTrue()
  }

  @Test
  fun a_link_sent_to_a_new_service_after_the_stop_is_listed_again() {
    serviceWith(A, B).destroy()

    serviceWith(A)

    assertThat(listedUrls()).containsExactly(A)
  }

  @Test
  fun a_permission_lost_with_heads_open_leaves_the_list_empty_after_the_refused_start_and_the_stop() {
    val controller = serviceWith(A, B)

    ShadowSettings.setCanDrawOverlays(false)
    controller.get().onStartCommand(intentFor(C), 0, 3)
    idle()
    controller.destroy()

    assertThat(listedUrls()).isEmpty()
  }

  private companion object {
    const val A = "https://a.example"
    const val B = "https://b.example"
    const val C = "https://c.example"
    const val SECRET = "https://secret.example"
  }
}
