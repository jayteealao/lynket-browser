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

package arun.com.chromer.browsing.customtabs

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.R
import arun.com.chromer.browsing.customtabs.bottombar.BottomBarManager
import arun.com.chromer.browsing.customtabs.bottombar.BottomBarReceiver
import arun.com.chromer.browsing.customtabs.callbacks.CopyToClipboardReceiver
import arun.com.chromer.shared.Constants.EXTRA_KEY_ORIGINAL_URL
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * The Custom Tab callbacks are mutable and explicit, so the provider's fill-in (the page address
 * and the tapped bottom-bar view) reaches the receiver.
 */
@HiltAndroidTest
class CallbackPendingIntentsTest : LynketRobolectricSuite() {

  private val url = "https://example.com/?bd=1"
  private val current = "https://example.com/page-2?q=%C3%A4#top"

  @Test
  fun bottomBarCallbackIsMutableAndNamesItsReceiver() {
    val pending = BottomBarManager.getOnClickPendingIntent(application, url)

    assertThat(pending.isImmutable).isFalse()
    val saved = shadowOf(pending).savedIntent
    assertThat(saved.component?.className).isEqualTo(BottomBarReceiver::class.java.name)
    assertThat(saved.getStringExtra(EXTRA_KEY_ORIGINAL_URL)).isEqualTo(url)
  }

  @Test
  fun theFillInReachesTheReceiver() {
    val pending = BottomBarManager.getOnClickPendingIntent(application, url)
    val fillIn = Intent()
      .setData(Uri.parse(current))
      .putExtra(CustomTabsIntent.EXTRA_REMOTEVIEWS_CLICKED_ID, R.id.bottom_bar_share)

    pending.send(application, 0, fillIn)

    val sent = shadowOf(application).broadcastIntents.last()
    assertThat(sent.component?.className).isEqualTo(BottomBarReceiver::class.java.name)
    assertThat(sent.dataString).isEqualTo(current)
    assertThat(sent.getIntExtra(CustomTabsIntent.EXTRA_REMOTEVIEWS_CLICKED_ID, -1)).isEqualTo(R.id.bottom_bar_share)
    assertThat(sent.browsingUrl()).isEqualTo(current)
  }

  @Test
  fun menuCallbacksAreMutableAndExplicit() {
    val pending = callbackBroadcast(application, 0, Intent(application, CopyToClipboardReceiver::class.java))

    assertThat(pending.isImmutable).isFalse()
    assertThat(shadowOf(pending).isBroadcast).isTrue()
    assertThat(shadowOf(pending).flags and PendingIntent.FLAG_MUTABLE).isNotEqualTo(0)
  }

  @Test
  fun anImplicitCallbackIsRefused() {
    assertThrows(IllegalArgumentException::class.java) {
      callbackBroadcast(application, 0, Intent("arun.com.chromer.SOMETHING"))
    }
  }

  @Test
  fun theAddressFallsBackToTheOneTheTabOpenedWith() {
    assertThat(Intent().putExtra(EXTRA_KEY_ORIGINAL_URL, url).browsingUrl()).isEqualTo(url)
    assertThat(Intent().setData(Uri.parse(current)).putExtra(EXTRA_KEY_ORIGINAL_URL, url).browsingUrl()).isEqualTo(current)
    assertThat(Intent().browsingUrl()).isNull()
  }
}
