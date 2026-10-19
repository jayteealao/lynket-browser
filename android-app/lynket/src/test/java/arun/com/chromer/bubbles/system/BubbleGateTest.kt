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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.BUBBLE_PREFERENCE_ALL
import android.app.NotificationManager.BUBBLE_PREFERENCE_NONE
import android.app.NotificationManager.BUBBLE_PREFERENCE_SELECTED
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Which cause the gate names for each bubble setting, and which settings page it opens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BubbleGateTest {

  private fun state(
    sdk: Int = 36,
    notifications: Boolean = true,
    blocked: Boolean = false,
    preference: Int? = BUBBLE_PREFERENCE_ALL,
    allowed: Boolean? = null,
    device: Boolean? = true,
    canBubble: Boolean? = null,
  ) = BubbleState(sdk, notifications, blocked, preference, allowed, device, canBubble)

  @Test
  fun notificationsOffOrABlockedChannelAreNamedFirst() {
    assertThat(BubbleGate.refusal(state(notifications = false))).isEqualTo(BubbleRefusal.NOTIFICATIONS_OFF)
    assertThat(BubbleGate.refusal(state(blocked = true))).isEqualTo(BubbleRefusal.NOTIFICATIONS_OFF)
    assertThat(BubbleGate.refusal(state(sdk = 29, notifications = false, preference = null, allowed = true)))
      .isEqualTo(BubbleRefusal.NOTIFICATIONS_OFF)
  }

  @Test
  fun androidTenAndElevenUseTheAppSwitch() {
    for (sdk in listOf(29, 30)) {
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = null, device = null, allowed = false)))
        .isEqualTo(BubbleRefusal.BUBBLES_OFF)
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = null, device = null, allowed = true))).isNull()
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = null, device = null, allowed = null))).isNull()
    }
  }

  @Test
  fun androidTwelveAndLaterReadThePreferenceAndTheDeviceSwitch() {
    for (sdk in listOf(31, 36)) {
      assertThat(BubbleGate.refusal(state(sdk = sdk, device = false))).isEqualTo(BubbleRefusal.BUBBLES_OFF)
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = BUBBLE_PREFERENCE_NONE))).isEqualTo(BubbleRefusal.BUBBLES_OFF)
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = BUBBLE_PREFERENCE_SELECTED, canBubble = null)))
        .isEqualTo(BubbleRefusal.BUBBLES_OFF)
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = BUBBLE_PREFERENCE_SELECTED, canBubble = false)))
        .isEqualTo(BubbleRefusal.BUBBLES_OFF)
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = BUBBLE_PREFERENCE_SELECTED, canBubble = true))).isNull()
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = BUBBLE_PREFERENCE_ALL))).isNull()
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = BUBBLE_PREFERENCE_ALL, canBubble = false))).isNull()
      // A value that could not be read never refuses; the check after posting still runs.
      assertThat(BubbleGate.refusal(state(sdk = sdk, preference = null, device = null))).isNull()
    }
  }

  @Test
  fun theScreenSettingFollowsEachPreference() {
    assertThat(BubbleGate.setting(state(preference = BUBBLE_PREFERENCE_ALL))).isEqualTo(BubbleSetting.ALL)
    assertThat(BubbleGate.setting(state(preference = BUBBLE_PREFERENCE_SELECTED))).isEqualTo(BubbleSetting.SELECTED)
    assertThat(BubbleGate.setting(state(preference = BUBBLE_PREFERENCE_NONE))).isEqualTo(BubbleSetting.NONE)
    assertThat(BubbleGate.setting(state(device = false))).isEqualTo(BubbleSetting.DEVICE_OFF)
    assertThat(BubbleGate.setting(state(notifications = false))).isEqualTo(BubbleSetting.NOTIFICATIONS_OFF)
    assertThat(BubbleGate.setting(state(preference = null))).isEqualTo(BubbleSetting.UNSUPPORTED)
    assertThat(BubbleGate.setting(state(sdk = 28))).isEqualTo(BubbleSetting.UNSUPPORTED)
    assertThat(BubbleGate.setting(state(sdk = 30, preference = null, device = null, allowed = true))).isEqualTo(BubbleSetting.ALL)
    assertThat(BubbleGate.setting(state(sdk = 30, preference = null, device = null, allowed = false))).isEqualTo(BubbleSetting.NONE)
    // The preference alone ignores notifications, so setup can ask for both.
    assertThat(BubbleGate.preference(state(notifications = false, preference = BUBBLE_PREFERENCE_SELECTED)))
      .isEqualTo(BubbleSetting.SELECTED)
  }

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val notificationManager: NotificationManager
    get() = application.getSystemService(NotificationManager::class.java)

  @Test
  fun readSeesNotificationsOff() {
    shadowOf(notificationManager).setNotificationsEnabled(false)

    val state = BubbleGate(application).read(bubbleConversationId("https://a.example"))

    assertThat(state.notificationsEnabled).isFalse()
    assertThat(BubbleGate.refusal(state)).isEqualTo(BubbleRefusal.NOTIFICATIONS_OFF)
  }

  @Test
  fun readFindsTheConversationChannelAndWhetherItCanBubble() {
    val conversation = bubbleConversationId("https://a.example")
    notificationManager.createNotificationChannel(
      NotificationChannel(BUBBLE_NOTIFICATION_CHANNEL_ID, "Bubbles", NotificationManager.IMPORTANCE_HIGH),
    )
    notificationManager.createNotificationChannel(
      NotificationChannel("conversation", "a.example", NotificationManager.IMPORTANCE_HIGH).apply {
        setConversationId(BUBBLE_NOTIFICATION_CHANNEL_ID, conversation)
        setAllowBubbles(true)
      },
    )

    val state = BubbleGate(application).read(conversation)

    assertThat(state.notificationsEnabled).isTrue()
    assertThat(state.channelBlocked).isFalse()
    assertThat(state.conversationCanBubble).isTrue()
  }

  @Test
  fun aBlockedBubbleChannelReadsAsBlocked() {
    notificationManager.createNotificationChannel(
      NotificationChannel(BUBBLE_NOTIFICATION_CHANNEL_ID, "Bubbles", NotificationManager.IMPORTANCE_NONE),
    )

    assertThat(BubbleGate(application).read(null).channelBlocked).isTrue()
  }

  @Test
  fun eachCauseOpensItsSettingsPageForThisApp() {
    // Robolectric resolves no settings activity, so register the two pages.
    val packageManager = shadowOf(application.packageManager)
    for (action in listOf(Settings.ACTION_APP_NOTIFICATION_SETTINGS, Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)) {
      val component = ComponentName("com.android.settings", action)
      packageManager.addActivityIfNotPresent(component)
      packageManager.addIntentFilterForActivity(component, IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }
    val gate = BubbleGate(application)

    val notifications = gate.settingsIntent(BubbleRefusal.NOTIFICATIONS_OFF)
    val bubbles = gate.settingsIntent(BubbleRefusal.BUBBLES_OFF)
    val notShown = gate.settingsIntent(BubbleRefusal.NOT_SHOWN)

    assertThat(notifications.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    assertThat(bubbles.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
    assertThat(notShown.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
    for (intent in listOf(notifications, bubbles, notShown)) {
      assertThat(intent.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(application.packageName)
    }
    assertThat(gate.settingsIntent(BubbleSetting.NOTIFICATIONS_OFF).action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    assertThat(gate.settingsIntent(BubbleSetting.SELECTED).action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
  }

  @Test
  fun withoutASettingsPageTheAppDetailsPageOpens() {
    val intent = BubbleGate(application).settingsIntent(BubbleRefusal.BUBBLES_OFF)

    assertThat(intent.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    assertThat(intent.dataString).isEqualTo("package:${application.packageName}")
  }
}
