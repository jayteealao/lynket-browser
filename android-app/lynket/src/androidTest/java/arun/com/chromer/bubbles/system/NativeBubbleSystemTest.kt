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

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.bubbles.BubbleLoadData
import arun.com.chromer.bubbles.bubbleConversationId
import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants.EXTRA_KEY_BUBBLE_REFUSAL
import arun.com.chromer.tabs.TabsManager
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Native bubbles against the emulator's own notification service: Android flags a posted bubble,
 * and a bubble Android refuses opens its link in a tab. Only a real system server sets the bubble
 * flag, so these checks cannot run on the JVM.
 *
 * The settings stores are fresh for each test (`InstrumentedDataStoreModule`); the bubble setting,
 * the device switch and the notification permission are the device's own, set through the shell.
 *
 * Run on a headless emulator:
 * `ANDROID_SERIAL=<emulator> ./gradlew :lynket:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=arun.com.chromer.bubbles.system.NativeBubbleSystemTest`.
 * Add `-Pandroid.testInstrumentationRunnerArguments.bubbleHoldMs=<ms>` to keep the posted bubble on
 * screen for a screenshot.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@RequiresApi(Build.VERSION_CODES.R)
class NativeBubbleSystemTest {

  @get:Rule
  val hilt = HiltAndroidRule(this)

  @Inject lateinit var bubbleNotificationManager: BubbleNotificationManager
  @Inject lateinit var bubbleGate: BubbleGate
  @Inject lateinit var tabsManager: TabsManager
  @Inject lateinit var preferences: Preferences

  private var deviceSwitch = ""

  @Before
  fun setUp() {
    hilt.inject()
    grantNotifications()
    deviceSwitch = deviceBubbles()
    setDeviceBubbles("1")
    cancelAppNotifications()
  }

  @After
  fun tearDown() {
    cancelAppNotifications()
    // A switch that was never stored reads "null"; Android treats it as on.
    setDeviceBubbles(if (deviceSwitch == "0") "0" else "1")
    setAppBubbles(1)
  }

  @Test
  fun postedBubbleCarriesTheBubbleFlag() {
    setAppBubbles(1)
    assertThat(bubbleGate.preference()).isEqualTo(BubbleSetting.ALL)
    assertThat(bubbleGate.refusal(bubbleConversationId(TEST_URL))).isNull()

    runBlocking {
      bubbleNotificationManager.showBubbles(
        BubbleLoadData(
          website = Website(TEST_URL),
          fromMinimize = false,
          fromAmp = false,
          incognito = false,
          desiredHeightDp = 600,
        ),
      )
    }

    assertWithMessage("bubble flag on notification ${bubbleNotificationId(TEST_URL)} within 10 s")
      .that(awaitBubbleFlag(TEST_URL))
      .isTrue()
    assertThat(bubbleNotificationManager.isShownAsBubble(TEST_URL, false)).isTrue()

    val holdMs = InstrumentationRegistry.getArguments().getString("bubbleHoldMs")?.toLongOrNull() ?: 0L
    if (holdMs > 0) Thread.sleep(holdMs)
  }

  @Test
  fun refusedBubbleOpensTheLinkInATab() {
    preferences.browsingMode(BrowsingMode.NativeBubbles)
    setAppBubbles(0)
    assertThat(bubbleGate.preference()).isEqualTo(BubbleSetting.NONE)
    assertThat(bubbleGate.refusal(bubbleConversationId(TEST_URL))).isEqualTo(BubbleRefusal.BUBBLES_OFF)

    // Records each activity start and blocks it, so no tab or browser opens during the test.
    val starts = mutableListOf<Intent>()
    val monitor = object : Instrumentation.ActivityMonitor() {
      override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
        synchronized(starts) { starts += Intent(intent) }
        return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
      }
    }
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.addMonitor(monitor)
    try {
      // A link reaches openWebHeads on the main thread.
      instrumentation.runOnMainSync { tabsManager.openWebHeads(appContext, Website(TEST_URL)) }
    } finally {
      instrumentation.removeMonitor(monitor)
    }

    val started = synchronized(starts) { starts.toList() }
    assertWithMessage("activity starts: $started").that(started).hasSize(1)
    val tab = started.single()
    assertThat(tab.component?.className)
      .isAnyOf(CustomTabActivity::class.java.name, WebViewActivity::class.java.name)
    assertThat(tab.data).isEqualTo(Uri.parse(TEST_URL))
    assertThat(tab.getStringExtra(EXTRA_KEY_BUBBLE_REFUSAL)).isEqualTo(BubbleRefusal.BUBBLES_OFF.name)

    // No bubble of the link shows: nothing posted, or posted without the bubble flag.
    val end = SystemClock.uptimeMillis() + 2_000
    while (SystemClock.uptimeMillis() < end) {
      assertWithMessage("bubble flag on the refused link").that(bubbleFlagOf(TEST_URL)).isNotEqualTo(true)
      SystemClock.sleep(250)
    }
  }

  private companion object {
    const val TEST_URL = "https://example.com/lynket-bubble-test"
  }
}
