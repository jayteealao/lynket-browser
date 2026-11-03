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
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.util.events.EventBus
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
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

  private companion object {
    const val A = "https://a.example"
    const val B = "https://b.example"
    const val C = "https://c.example"
    const val SECRET = "https://secret.example"
  }
}
