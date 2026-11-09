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

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import arun.com.chromer.bubbles.BubbleLoadData
import arun.com.chromer.bubbles.bubbleConversationId
import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
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

  private companion object {
    const val TEST_URL = "https://example.com/lynket-bubble-test"
  }
}
