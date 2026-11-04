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

package arun.com.chromer.debug

import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.bubbles.system.isBubbleEntry
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * Debug build only. Lets the overflow device check read and post the app's bubble notifications:
 * the shell cannot post as the app, and `cmd notification post` would post as the shell.
 *
 * `adb shell am broadcast -n arun.com.chromer.debug/arun.com.chromer.debug.BubbleProbeReceiver
 * --es action <action>`, with these actions:
 * - `state`: one line under the tag `BubbleProbe` for each bubble notification, then a count;
 * - `repost`, with `--es url <url>` or `--ei id <id>`, and `--es variant <same|text|fresh>`: posts
 *   the active notification of that link or id again, silently. Only the id reaches an incognito
 *   bubble, whose id is not the one of its link. `same` changes nothing else, `text` adds an
 *   invisible character to its text, and `fresh` cancels it first;
 * - `clear`: cancels every notification of the app.
 */
class BubbleProbeReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val manager = context.getSystemService(NotificationManager::class.java)
    when (intent.getStringExtra(EXTRA_ACTION)) {
      "state" -> logState(manager)
      "repost" -> repost(manager, repostId(intent), intent.getStringExtra(EXTRA_VARIANT))
      "clear" -> {
        manager.cancelAll()
        Timber.tag(TAG).i("clear done")
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun logState(manager: NotificationManager) {
    val bubbles = manager.activeNotifications.filter(::isBubbleEntry)
    bubbles.forEach {
      val flags = it.notification.flags
      Timber.tag(TAG).i(
        "state id=%d bubble=%b flags=%x",
        it.id,
        flags and Notification.FLAG_BUBBLE != 0,
        flags,
      )
    }
    Timber.tag(TAG).i("state done count=%d", bubbles.size)
  }

  /** A bubble by `--ei id <id>`, which reaches an incognito one, or by its `--es url <url>`. */
  private fun repostId(intent: Intent): Int =
    if (intent.hasExtra(EXTRA_ID)) intent.getIntExtra(EXTRA_ID, 0) else bubbleNotificationId(intent.getStringExtra(EXTRA_URL).orEmpty())

  private fun repost(manager: NotificationManager, id: Int, variant: String?) {
    val active = manager.activeNotifications.firstOrNull { it.id == id }
    Timber.tag(TAG).i("repost id=%d variant=%s found=%b", id, variant, active != null)
    if (active == null) return
    val copy = active.notification.clone().apply { flags = flags or Notification.FLAG_ONLY_ALERT_ONCE }
    when (variant) {
      "text" -> copy.extras.putCharSequence(
        Notification.EXTRA_TEXT,
        "${copy.extras.getCharSequence(Notification.EXTRA_TEXT) ?: ""}$ZERO_WIDTH_SPACE",
      )
      "fresh" -> manager.cancel(id)
    }
    manager.notify(id, copy)
  }

  /** A notification of the bubble group, without the summary. */
  private fun isBubbleEntry(active: StatusBarNotification) = active.notification.isBubbleEntry()

  private companion object {
    const val TAG = "BubbleProbe"
    const val EXTRA_ACTION = "action"
    const val EXTRA_URL = "url"
    const val EXTRA_ID = "id"
    const val EXTRA_VARIANT = "variant"

    const val ZERO_WIDTH_SPACE = "\u200B"
  }
}
