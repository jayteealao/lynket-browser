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
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import arun.com.chromer.R
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.shared.Constants.EXTRA_KEY_BUBBLE_REFUSAL
import arun.com.chromer.ui.browsing.BubbleRefusedMessage
import arun.com.chromer.ui.browsing.TOUCH_EXPLORATION_TIMEOUT_MS
import arun.com.chromer.ui.browsing.messageRes
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The message over a refused bubble's tab: its text per cause, one "Open Settings" action that
 * TalkBack can reach, the settings page each cause opens, and the browsing activity that starts it
 * once.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HiltTestApplication::class)
class BubbleRefusedMessageTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createEmptyComposeRule()

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private fun string(id: Int) = application.getString(id)

  @Before
  fun setUp() {
    hiltRule.inject()
    // Robolectric resolves no settings activity; register the two pages the message opens.
    val packageManager = shadowOf(application.packageManager)
    for (action in listOf(Settings.ACTION_APP_NOTIFICATION_SETTINGS, Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)) {
      val component = ComponentName("com.android.settings", action)
      packageManager.addActivityIfNotPresent(component)
      packageManager.addIntentFilterForActivity(component, IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }
  }

  /** The snackbar animates in; the clock is held so its timeout does not pass during the checks. */
  private fun showSnackbar() {
    composeRule.mainClock.autoAdvance = false
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.waitForIdle()
  }

  @Test
  fun eachCauseNamesItselfAndOffersOpenSettings() {
    for (refusal in BubbleRefusal.entries) {
      var opened = 0
      val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
      controller.get().setContent {
        ChromerTheme { BubbleRefusedMessage(refusal, onOpenSettings = { opened++ }, onDone = {}) }
      }
      showSnackbar()

      composeRule.onNodeWithText(string(refusal.messageRes())).assertExists()
      composeRule.onNodeWithText(string(R.string.open_settings)).assertHasClickAction().performClick()
      composeRule.mainClock.advanceTimeBy(1_000)
      composeRule.waitForIdle()
      assertThat(opened).isEqualTo(1)
      controller.pause().stop().destroy()
      composeRule.mainClock.autoAdvance = true
    }
  }

  @Test
  fun theMessageIsALiveRegionWithAPaneTitle() {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    controller.get().setContent {
      ChromerTheme { BubbleRefusedMessage(BubbleRefusal.BUBBLES_OFF, onOpenSettings = {}, onDone = {}) }
    }
    showSnackbar()

    composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertExists()
    composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).assertExists()
  }

  @Test
  fun theMessageClosesItselfAfterItsTimeout() {
    var done = false
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    controller.get().setContent {
      ChromerTheme { BubbleRefusedMessage(BubbleRefusal.BUBBLES_OFF, onOpenSettings = {}, onDone = { done = true }) }
    }
    showSnackbar()
    assertThat(done).isFalse()

    composeRule.mainClock.advanceTimeBy(15_000)
    composeRule.waitForIdle()
    assertThat(done).isTrue()
  }

  @Test
  fun withTouchExplorationTheMessageStaysPastTheLongTimeout() {
    var done = false
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    controller.get().setContent {
      ChromerTheme {
        BubbleRefusedMessage(BubbleRefusal.BUBBLES_OFF, onOpenSettings = {}, onDone = { done = true }, touchExploration = true)
      }
    }
    showSnackbar()

    composeRule.mainClock.advanceTimeBy(15_000)
    composeRule.waitForIdle()
    assertThat(done).isFalse()
    composeRule.onNodeWithText(string(R.string.open_settings)).assertExists()

    composeRule.mainClock.advanceTimeBy(TOUCH_EXPLORATION_TIMEOUT_MS)
    composeRule.waitForIdle()
    assertThat(done).isTrue()
  }

  private fun messageActivity(refusal: BubbleRefusal?) = Robolectric.buildActivity(
    BubbleRefusedActivity::class.java,
    Intent(application, BubbleRefusedActivity::class.java).apply { refusal?.let { putExtra(EXTRA_KEY_BUBBLE_REFUSAL, it.name) } },
  ).setup()

  @Test
  fun openSettingsOpensThePageOfTheCauseAndCloses() {
    val expected = mapOf(
      BubbleRefusal.NOTIFICATIONS_OFF to Settings.ACTION_APP_NOTIFICATION_SETTINGS,
      BubbleRefusal.BUBBLES_OFF to Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS,
    )
    for ((refusal, action) in expected) {
      val controller = messageActivity(refusal)
      showSnackbar()

      composeRule.onNodeWithText(string(R.string.open_settings)).performClick()
      composeRule.mainClock.advanceTimeBy(1_000)
      composeRule.waitForIdle()

      val started = shadowOf(controller.get()).nextStartedActivity
      assertThat(started.action).isEqualTo(action)
      assertThat(started.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(application.packageName)
      assertThat(controller.get().isFinishing).isTrue()
      controller.pause().stop().destroy()
      composeRule.mainClock.autoAdvance = true
    }
  }

  @Test
  fun withoutACauseTheWindowClosesAtOnce() {
    assertThat(messageActivity(null).get().isFinishing).isTrue()
  }

  @Test
  fun aBrowsingActivityStartsTheMessageOnceAfterItsPage() {
    val intent = Intent(application, WebViewActivity::class.java)
      .setData(Uri.parse("https://example.com/"))
      .putExtra(EXTRA_KEY_BUBBLE_REFUSAL, BubbleRefusal.BUBBLES_OFF.name)
    val controller = Robolectric.buildActivity(WebViewActivity::class.java, intent).setup()

    val started = shadowOf(controller.get()).nextStartedActivity
    assertThat(started.component?.className).isEqualTo(BubbleRefusedActivity::class.java.name)
    assertThat(BubbleRefusedActivity.refusalOf(started)).isEqualTo(BubbleRefusal.BUBBLES_OFF)
    assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)

    controller.recreate()
    assertThat(shadowOf(controller.get()).nextStartedActivity).isNull()
  }

  @Test
  fun aBrowsingActivityWithoutACauseStartsNoMessage() {
    val intent = Intent(application, WebViewActivity::class.java).setData(Uri.parse("https://example.com/"))
    val controller = Robolectric.buildActivity(WebViewActivity::class.java, intent).setup()

    assertThat(shadowOf(controller.get()).nextStartedActivity).isNull()
  }
}
