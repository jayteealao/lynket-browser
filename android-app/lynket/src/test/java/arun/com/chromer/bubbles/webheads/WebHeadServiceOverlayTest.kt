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
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

/** The service draws one overlay window per head plus the trash, in the foreground. */
@HiltAndroidTest
class WebHeadServiceOverlayTest : LynketRobolectricSuite() {

  private fun intentFor(url: String) = Intent(application, WebHeadService::class.java).setData(Uri.parse(url))

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
}
