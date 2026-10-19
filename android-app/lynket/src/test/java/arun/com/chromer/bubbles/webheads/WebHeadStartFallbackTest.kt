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

import android.os.Looper
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast
import arun.com.chromer.debug.FileDebugHooks
import java.io.File
import javax.inject.Inject

/** When web heads cannot start, the link opens in a tab instead of being lost. */
@HiltAndroidTest
class WebHeadStartFallbackTest : LynketRobolectricSuite() {
  @Inject
  lateinit var tabs: TabsManager

  private val faultFile: File
    get() = FileDebugHooks.switchFile(application, FileDebugHooks.START_FAIL_FILE)

  @After
  fun removeFault() {
    faultFile.delete()
  }

  private fun webHeadsOn() {
    ShadowSettings.setCanDrawOverlays(true)
    preferences.webHeads(true)
    preferences.aggressiveLoading(false)
  }

  @Test
  fun web_heads_start_the_service_normally() {
    webHeadsOn()
    tabs.openWebHeads(application, Website("https://a.example"))

    val started = shadowOf(application).nextStartedService
    assertThat(started?.component?.className).isEqualTo(WebHeadService::class.java.name)
    assertThat(shadowOf(application).nextStartedActivity).isNull()
  }

  @Test
  fun a_refused_start_opens_a_tab_with_a_message() {
    webHeadsOn()
    faultFile.parentFile!!.mkdirs()
    faultFile.createNewFile()

    tabs.openWebHeads(application, Website("https://a.example"))
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(shadowOf(application).nextStartedService).isNull()
    val tab = shadowOf(application).nextStartedActivity
    assertThat(tab).isNotNull()
    assertThat(tab.dataString).isEqualTo("https://a.example")
    assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(application.getString(R.string.web_heads_start_failed))
  }

  @Test
  fun the_fault_switch_is_off_without_the_marker_file() {
    assertThat(FileDebugHooks().webHeadStartRefused(application)).isFalse()
  }
}
