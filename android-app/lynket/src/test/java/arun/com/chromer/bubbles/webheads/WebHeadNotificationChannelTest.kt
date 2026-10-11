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
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import arun.com.chromer.LynketRobolectricSuite
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers

/**
 * Notification channels exist from Android 8.0. On Android 7 (the minimum the app supports) the
 * web head notification must be built without touching the channel API, which is missing there.
 */
@HiltAndroidTest
class WebHeadNotificationChannelTest : LynketRobolectricSuite() {

  @Test
  fun below_android_8_the_notification_is_built_without_a_channel() {
    ShadowSettings.setCanDrawOverlays(true)
    val intent = Intent(application, WebHeadService::class.java).setData(Uri.parse("https://a.example"))
    val service = Robolectric.buildService(WebHeadService::class.java, intent).create().startCommand(0, 1).get()
    shadowOf(Looper.getMainLooper()).idle()
    val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notificationChannels.forEach { notificationManager.deleteNotificationChannel(it.id) }

    val sdk = Build.VERSION.SDK_INT
    ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.N_MR1)
    try {
      service.getNotification()
    } finally {
      ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
    }

    assertThat(shadowOf(notificationManager).notificationChannels).isEmpty()
  }
}
