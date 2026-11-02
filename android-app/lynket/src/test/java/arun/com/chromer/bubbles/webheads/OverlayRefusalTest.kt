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
import android.os.Looper
import android.provider.Settings
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.bubbles.system.BubbleRefusal
import arun.com.chromer.bubbles.system.BubbleRefusedActivity
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants.EXTRA_KEY_BUBBLE_REFUSAL
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.tabs.TabsManager
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings
import javax.inject.Inject

/**
 * With the overlay permission off, a web heads link opens in a tab, keeps its incognito flag, and
 * the "Display over other apps" settings page never opens on its own.
 */
@HiltAndroidTest
class OverlayRefusalTest : LynketRobolectricSuite() {
  @Inject
  lateinit var tabs: TabsManager

  @Inject
  lateinit var overlayRefusal: OverlayRefusal

  private val url = "https://example.com/refused"

  private fun overlayOff() {
    ShadowSettings.setCanDrawOverlays(false)
    preferences.webHeads(true)
    preferences.nativeBubbles(false)
    preferences.fullIncognitoMode(false)
    preferences.aggressiveLoading(false)
  }

  /** Every activity started so far, after the posted work of the main thread ran. */
  private fun startedActivities(): List<Intent> {
    shadowOf(Looper.getMainLooper()).idle()
    return generateSequence { shadowOf(application).nextStartedActivity }.toList()
  }

  private fun List<Intent>.settingsPages() =
    filter { it.action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION }

  @Test
  fun web_heads_without_the_permission_open_a_tab_and_no_settings_page() {
    overlayOff()

    tabs.openWebHeads(application, Website(url))

    val started = startedActivities()
    assertThat(started.settingsPages()).isEmpty()
    assertThat(shadowOf(application).nextStartedService).isNull()
    assertThat(started.map { it.dataString }).containsExactly(url)
  }

  @Test
  fun web_heads_with_aggressive_loading_open_exactly_one_tab() {
    overlayOff()
    preferences.aggressiveLoading(true)

    tabs.openWebHeads(application, Website(url))

    val started = startedActivities()
    assertThat(started.settingsPages()).isEmpty()
    assertThat(started.map { it.dataString }).containsExactly(url)
    assertThat(shadowOf(application).nextStartedService).isNull()
  }

  @Test
  fun refusal_keeps_the_incognito_flag() {
    overlayOff()

    overlayRefusal.refuse(application, Website(url), incognito = true)

    val started = startedActivities()
    assertThat(started).hasSize(1)
    assertThat(started.single().dataString).isEqualTo(url)
    assertThat(started.single().getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
    assertThat(started.single().getStringExtra(EXTRA_KEY_BUBBLE_REFUSAL)).isEqualTo("OVERLAY_OFF")
  }

  @Test
  fun refusal_of_a_normal_link_opens_a_normal_tab() {
    overlayOff()

    overlayRefusal.refuse(application, Website(url), incognito = false)

    val started = startedActivities()
    assertThat(started).hasSize(1)
    assertThat(started.single().dataString).isEqualTo(url)
    assertThat(started.single().hasExtra(EXTRA_KEY_INCOGNITO)).isFalse()
    assertThat(started.single().getStringExtra(EXTRA_KEY_BUBBLE_REFUSAL)).isEqualTo("OVERLAY_OFF")
  }

  @Test
  fun refused_tab_shows_the_overlay_message_and_no_settings_page_before_a_tap() {
    overlayOff()
    // Web View mode, so the tab is a Lynket activity that Robolectric can start.
    preferences.useWebView(true)

    overlayRefusal.refuse(application, Website(url), incognito = false)
    val tab = startedActivities().single()
    val controller = Robolectric.buildActivity(WebViewActivity::class.java, Intent(tab).setClass(application, WebViewActivity::class.java)).setup()
    val fromTab = generateSequence { shadowOf(controller.get()).nextStartedActivity }.toList() + startedActivities()

    val message = fromTab.single { it.component?.className == BubbleRefusedActivity::class.java.name }
    assertThat(BubbleRefusedActivity.refusalOf(message)).isEqualTo(BubbleRefusal.OVERLAY_OFF)
    assertThat((listOf(tab) + fromTab).settingsPages()).isEmpty()
  }

  @Test
  fun web_heads_keep_the_incognito_flag_of_a_refused_link() {
    overlayOff()

    tabs.openWebHeads(application, Website(url), incognito = true)

    val started = startedActivities()
    assertThat(started.settingsPages()).isEmpty()
    assertThat(started).hasSize(1)
    assertThat(started.single().getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
  }
}
