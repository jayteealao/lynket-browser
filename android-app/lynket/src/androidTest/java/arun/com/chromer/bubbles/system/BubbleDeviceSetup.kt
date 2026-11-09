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

package arun.com.chromer.bubbles.system

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import arun.com.chromer.bubbles.bubbleNotificationId

// Device set-up for the emulator bubble tests: the app's bubble setting, the device switch and the
// notification permission are set through the shell, so a run needs no hand set-up.

private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

/** The app under test, with its build type's package suffix. */
internal val appContext: Context get() = instrumentation.targetContext

private val pkg: String get() = appContext.packageName

private val notificationManager: NotificationManager
  get() = appContext.getSystemService(NotificationManager::class.java)

/** Runs [command] as the shell user and returns its output once the command has finished. */
internal fun shell(command: String): String {
  val output = instrumentation.uiAutomation.executeShellCommand(command)
  return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
}

/** Allows the app's notifications; before Android 13 they need no runtime permission. */
internal fun grantNotifications() {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.POST_NOTIFICATIONS)
  }
}

/** The app's bubble setting: 0 nothing can bubble, 1 all conversations, 2 selected ones. */
internal fun setAppBubbles(preference: Int) {
  shell("cmd notification set_bubbles $pkg $preference")
}

/** The device's bubble switch as stored, "1" on and "0" off ("null" when never set). */
internal fun deviceBubbles(): String = shell("settings get secure notification_bubbles").trim()

internal fun setDeviceBubbles(value: String) {
  shell("settings put secure notification_bubbles $value")
}

/** Whether the bubble notification of [url] carries the bubble flag, or null when none is posted. */
internal fun bubbleFlagOf(url: String): Boolean? =
  notificationManager.activeNotifications
    .firstOrNull { it.id == bubbleNotificationId(url) }
    ?.let { it.notification.flags and Notification.FLAG_BUBBLE != 0 }

/**
 * Reads the active notifications every [everyMs] until the bubble of [url] carries the bubble flag
 * or [limitMs] passes. Android sets the flag after the post returns, so a single read can be early.
 */
internal fun awaitBubbleFlag(url: String, limitMs: Long = 10_000, everyMs: Long = 250): Boolean {
  val end = SystemClock.uptimeMillis() + limitMs
  while (true) {
    if (bubbleFlagOf(url) == true) return true
    if (SystemClock.uptimeMillis() >= end) return false
    SystemClock.sleep(everyMs)
  }
}

internal fun cancelAppNotifications() {
  notificationManager.cancelAll()
}
