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

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Why Android will not show a link as a bubble. */
enum class BubbleRefusal {
  /** Notifications are off for the app, or its bubble channel is blocked. */
  NOTIFICATIONS_OFF,

  /** Bubbles are off for the app or the device, or this conversation may not bubble. */
  BUBBLES_OFF,

  /** The bubble notification posted, but Android showed it as a plain notification. */
  NOT_SHOWN,
}

/** The app's bubble setting, as the browsing mode screen shows it. */
enum class BubbleSetting { ALL, SELECTED, NONE, DEVICE_OFF, NOTIFICATIONS_OFF, UNSUPPORTED }

/**
 * What the platform reports about bubbles. A value is null when the API level has no such query or
 * the query failed.
 */
data class BubbleState(
  val sdk: Int,
  val notificationsEnabled: Boolean,
  val channelBlocked: Boolean,
  /** `NotificationManager.getBubblePreference()`, API 31 and later. */
  val appPreference: Int?,
  /** `NotificationManager.areBubblesAllowed()`, API 29 and 30. */
  val appBubblesAllowed: Boolean?,
  /** `NotificationManager.areBubblesEnabled()`, the device switch, API 31 and later. */
  val deviceBubblesEnabled: Boolean?,
  /** Whether the channel a bubble of this conversation posts to (its own or the parent) can bubble. */
  val conversationCanBubble: Boolean?,
)

/** The id of the conversation (and its long-lived shortcut) a bubble of [url] belongs to. */
fun bubbleConversationId(url: String): String = "bubble_${url.hashCode()}"

/**
 * Reads the notification and bubble settings before a bubble posts, names the cause when Android
 * will not show it, and builds the intent of the settings page where the person can change it.
 * Android does not let an app change these settings.
 */
@Singleton
class BubbleGate @Inject constructor(private val application: Application) {

  private val notificationManager: NotificationManager?
    get() = application.getSystemService(NotificationManager::class.java)

  /** The cause when a bubble of [conversationId] would not show, or null when it may. */
  fun refusal(conversationId: String): BubbleRefusal? = refusal(read(conversationId))

  /** The app's bubble setting, with notifications off first. */
  fun setting(): BubbleSetting = setting(read(null))

  /** The app's bubble setting only, whether or not notifications are allowed yet. */
  fun preference(): BubbleSetting = preference(read(null))

  fun read(conversationId: String?): BubbleState {
    val manager = notificationManager
    val notificationsEnabled = manager?.let { query("notifications") { it.areNotificationsEnabled() } } ?: false
    if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return BubbleState(Build.VERSION.SDK_INT, notificationsEnabled, false, null, null, null, null)
    }
    return readBubbles(manager, notificationsEnabled, conversationId)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun readBubbles(manager: NotificationManager, notificationsEnabled: Boolean, conversationId: String?): BubbleState {
    val parent = query("bubble channel") { manager.getNotificationChannel(BUBBLE_NOTIFICATION_CHANNEL_ID) }
    val channelBlocked = parent?.importance == NotificationManager.IMPORTANCE_NONE
    val recordChannel = if (conversationId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      query("conversation channel") { manager.getNotificationChannel(BUBBLE_NOTIFICATION_CHANNEL_ID, conversationId) }
    } else {
      parent
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      BubbleState(
        sdk = Build.VERSION.SDK_INT,
        notificationsEnabled = notificationsEnabled,
        channelBlocked = channelBlocked,
        appPreference = query("bubble preference") { manager.bubblePreference },
        appBubblesAllowed = null,
        deviceBubblesEnabled = query("device bubbles") { manager.areBubblesEnabled() },
        conversationCanBubble = recordChannel?.canBubble(),
      )
    } else {
      @Suppress("DEPRECATION") // sdlc-debt: areBubblesAllowed is the only app-level query on API 29 and 30; drop when minSdk reaches 31
      BubbleState(
        sdk = Build.VERSION.SDK_INT,
        notificationsEnabled = notificationsEnabled,
        channelBlocked = channelBlocked,
        appPreference = null,
        appBubblesAllowed = query("bubbles allowed") { manager.areBubblesAllowed() },
        deviceBubblesEnabled = null,
        conversationCanBubble = recordChannel?.canBubble(),
      )
    }
  }

  /** The settings page for [refusal]: notification settings, or the app's bubble settings. */
  fun settingsIntent(refusal: BubbleRefusal): Intent = when (refusal) {
    BubbleRefusal.NOTIFICATIONS_OFF -> notificationSettingsIntent()
    BubbleRefusal.BUBBLES_OFF, BubbleRefusal.NOT_SHOWN -> bubbleSettingsIntent()
  }

  /** The settings page the browsing mode screen opens for [setting]. */
  fun settingsIntent(setting: BubbleSetting): Intent = when (setting) {
    BubbleSetting.NOTIFICATIONS_OFF -> notificationSettingsIntent()
    else -> bubbleSettingsIntent()
  }

  /** The app's own bubble settings page, where "All conversations can bubble" is chosen. */
  fun bubbleSettingsIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    pageIntent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
  } else {
    notificationSettingsIntent()
  }

  /** The app's notification settings page (Android 8 and later), else the app details page. */
  private fun notificationSettingsIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    pageIntent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
  } else {
    appDetailsIntent()
  }

  /** The app details page, for a device that has no notification or bubble page. */
  fun appDetailsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${application.packageName}".toUri())

  @RequiresApi(Build.VERSION_CODES.O)
  private fun pageIntent(action: String): Intent {
    val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
    return if (intent.resolveActivity(application.packageManager) != null) intent else appDetailsIntent()
  }

  private inline fun <T> query(what: String, block: () -> T): T? = try {
    block()
  } catch (e: RuntimeException) {
    Timber.w(e, "Could not read the %s", what)
    null
  }

  companion object {
    /**
     * The cause for [state], or null. Unknown values never refuse: the check after posting
     * ([Notification.FLAG_BUBBLE]) still catches a bubble Android did not show.
     */
    fun refusal(state: BubbleState): BubbleRefusal? {
      if (!state.notificationsEnabled || state.channelBlocked) return BubbleRefusal.NOTIFICATIONS_OFF
      if (state.sdk < Build.VERSION_CODES.Q) return null
      if (state.sdk < Build.VERSION_CODES.S) {
        return if (state.appBubblesAllowed == false) BubbleRefusal.BUBBLES_OFF else null
      }
      if (state.deviceBubblesEnabled == false) return BubbleRefusal.BUBBLES_OFF
      return when (state.appPreference) {
        NotificationManager.BUBBLE_PREFERENCE_NONE -> BubbleRefusal.BUBBLES_OFF
        // Only a conversation the person selected bubbles; its channel then allows bubbles.
        NotificationManager.BUBBLE_PREFERENCE_SELECTED ->
          if (state.conversationCanBubble == true) null else BubbleRefusal.BUBBLES_OFF
        else -> null
      }
    }

    /** The setting the screen shows: notifications off first, then the bubble setting. */
    fun setting(state: BubbleState): BubbleSetting = when {
      state.sdk < Build.VERSION_CODES.Q -> BubbleSetting.UNSUPPORTED
      !state.notificationsEnabled || state.channelBlocked -> BubbleSetting.NOTIFICATIONS_OFF
      else -> preference(state)
    }

    /** The bubble setting alone. */
    fun preference(state: BubbleState): BubbleSetting = when {
      state.sdk < Build.VERSION_CODES.Q -> BubbleSetting.UNSUPPORTED
      state.sdk < Build.VERSION_CODES.S -> when (state.appBubblesAllowed) {
        true -> BubbleSetting.ALL
        false -> BubbleSetting.NONE
        null -> BubbleSetting.UNSUPPORTED
      }
      state.deviceBubblesEnabled == false -> BubbleSetting.DEVICE_OFF
      else -> when (state.appPreference) {
        NotificationManager.BUBBLE_PREFERENCE_ALL -> BubbleSetting.ALL
        NotificationManager.BUBBLE_PREFERENCE_SELECTED -> BubbleSetting.SELECTED
        NotificationManager.BUBBLE_PREFERENCE_NONE -> BubbleSetting.NONE
        else -> BubbleSetting.UNSUPPORTED
      }
    }
  }
}
