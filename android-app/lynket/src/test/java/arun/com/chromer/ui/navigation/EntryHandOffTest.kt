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

package arun.com.chromer.ui.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.article.ArticleActivity
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * Frames from a push to the page's start: the change is composed on the first frame, the entry on
 * the second, and its start effect runs right after it, on the third (48 ms). The slide takes
 * about 16 frames, so the page never waits for it.
 */
private const val MAX_FRAMES_TO_START = 3

private val fakes = LynketEntries(
  home = { Text("fake home") },
  history = {},
  tabs = {},
  intro = {},
  changelog = {},
)

/**
 * Pushes [key] with the main clock paused and returns the activity the screen started and the
 * number of frames it took. The slide takes about 260 ms (16 frames); the page starts right after
 * the entry's first frame, so its loading never waits for the motion.
 */
private fun startFrom(
  composeRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
  key: LynketKey,
): Pair<Intent, LynketNavigator> {
  val navigator = LynketNavigator(mutableStateListOf(LynketKey.Home))
  composeRule.setContent { ChromerTheme { LynketNavDisplay(navigator = navigator, entries = fakes) } }
  composeRule.waitForIdle()
  composeRule.mainClock.autoAdvance = false
  val shadow = shadowOf(composeRule.activity)
  while (shadow.peekNextStartedActivity() != null) shadow.nextStartedActivity

  composeRule.runOnUiThread { navigator.navigate(key) }
  var frames = 0
  var started: Intent? = null
  while (started == null && frames < 10) {
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.waitForIdle()
    frames++
    started = shadow.nextStartedActivity
  }
  assertWithMessage("frames before the page started").that(frames).isAtMost(MAX_FRAMES_TO_START)
  return started!! to navigator
}

/**
 * A link and the reader start their page on the entry's first frame, while the slide has only
 * begun, and the entry leaves the back stack at once.
 */
@HiltAndroidTest
class EntryHandOffTest : LynketRobolectricSuite() {

  @get:Rule(order = 1)
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  @Before
  fun reset() {
    clearPreferences()
  }

  @Test
  fun aLinkStartsItsPageOnTheFirstFrame() {
    val (intent, navigator) = startFrom(composeRule, LynketKey.Browser("https://example.com/a?b=1"))

    assertThat(intent.component?.className).isEqualTo(CustomTabActivity::class.java.name)
    assertThat(intent.dataString).isEqualTo("https://example.com/a?b=1")
    assertThat(navigator.backStack).containsExactly(LynketKey.Home)
  }

  @Test
  fun theReaderStartsOnTheFirstFrame() {
    val (intent, navigator) = startFrom(composeRule, LynketKey.Article("https://example.com/b"))

    assertThat(intent.component?.className).isEqualTo(ArticleActivity::class.java.name)
    assertThat(intent.dataString).isEqualTo("https://example.com/b")
    assertThat(navigator.backStack).containsExactly(LynketKey.Home)
  }
}

/** The same with system animations off: the page starts on the first frame too. */
@HiltAndroidTest
class EntryHandOffAnimationsOffTest : LynketRobolectricSuite() {

  @get:Rule(order = 1)
  val composeRule = createAndroidComposeRule<ComponentActivity>(effectContext = object : MotionDurationScale {
    override val scaleFactor = 0f
  })

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  @Before
  fun reset() {
    clearPreferences()
  }

  @Test
  fun aLinkStartsItsPageOnTheFirstFrame() {
    val (intent, _) = startFrom(composeRule, LynketKey.Browser("https://example.com/a?b=1"))
    assertThat(intent.component?.className).isEqualTo(CustomTabActivity::class.java.name)
  }

  @Test
  fun theReaderStartsOnTheFirstFrame() {
    val (intent, _) = startFrom(composeRule, LynketKey.Article("https://example.com/b"))
    assertThat(intent.component?.className).isEqualTo(ArticleActivity::class.java.name)
  }
}
