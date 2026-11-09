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

package arun.com.chromer.screenshot

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.suggestion.items.SuggestionItem
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.home.HomeActions
import arun.com.chromer.ui.home.HomeContentState
import arun.com.chromer.ui.home.HomeScreenContent
import arun.com.chromer.ui.home.HomeSearchSamples
import arun.com.chromer.ui.home.enterTouchMode
import arun.com.chromer.ui.home.searchField
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the home search bar closed, open with suggestions, open with nothing typed, with
 * history only, offline with a copied link, and a long copied link at the largest font, on a phone
 * and a fold. Each case taps and types as a person does, then holds the clock at a fixed time so
 * the cursor is the same in every run. The whole screen is drawn, so a search layer in its own
 * window is in the picture too. Record with `./gradlew :lynket:recordRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = PHONE)
class HomeSearchParityGoldenTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val copied = { title: String ->
    SuggestionItem.CopySuggestionItem(title, composeRule.activity.getString(R.string.link_you_copied))
  }

  private fun capture(
    theme: ThemeMode = ThemeMode.Light,
    suggestions: Suggestions = Suggestions(),
    online: Boolean = true,
    tap: Boolean = true,
    type: String = "",
  ) {
    composeRule.setContent {
      var query by remember { mutableStateOf("") }
      ChromerTheme(themeMode = theme) {
        HomeScreenContent(
          state = HomeContentState(
            recents = HomeSearchSamples.recents,
            providerInfo = HomeSearchSamples.provider,
            isOnline = online,
            query = query,
            suggestions = suggestions,
            searchEngines = HomeSearchSamples.engines,
          ),
          actions = HomeActions(onQueryChange = { query = it }),
        )
      }
    }
    composeRule.activity.enterTouchMode()
    composeRule.waitForIdle()
    if (tap) {
      composeRule.searchField().performClick()
      composeRule.waitForIdle()
    }
    if (type.isNotEmpty()) {
      composeRule.searchField().performTextInput(type)
      composeRule.waitForIdle()
    }
    composeRule.mainClock.autoAdvance = false
    composeRule.mainClock.advanceTimeBy(1_000)
    captureScreenRoboImage()
  }

  @Test fun homeSearch_collapsed_light_phone() = capture(tap = false)
  @Test fun homeSearch_collapsed_dark_phone() = capture(ThemeMode.Dark, tap = false)

  @Test fun homeSearch_typed_light_phone() = capture(suggestions = HomeSearchSamples.kotlin(), type = "kotlin")
  @Test fun homeSearch_typed_dark_phone() = capture(ThemeMode.Dark, HomeSearchSamples.kotlin(), type = "kotlin")

  /** Opened with nothing typed: the engine chips only. */
  @Test fun homeSearch_openedEmpty_light_phone() = capture()

  /** Typed while the web suggestions are still on the way: one history row. */
  @Test fun homeSearch_historyOnly_light_phone() = capture(
    suggestions = Suggestions(
      query = "compose lis",
      history = listOf(HomeSearchSamples.history("Lists and grids in Compose", "https://developer.android.com/develop/ui/compose/lists")),
    ),
    type = "compose lis",
  )

  /** Offline: the copied link and a history row, no web suggestions. */
  @Test fun homeSearch_offline_light_phone() = capture(
    suggestions = Suggestions(
      query = "github",
      copy = listOf(copied("https://github.com/arunkumar9t2/lynket-browser")),
      history = listOf(HomeSearchSamples.history("GitHub: Let's build from here", "https://github.com/")),
    ),
    online = false,
    type = "github",
  )

  /** The largest font with a long copied link: the row wraps to two lines, the field cuts the text. */
  @Config(fontScale = 2f)
  @Test fun homeSearch_worst_light_phone() = capture(
    suggestions = Suggestions(
      query = LONG_LINK,
      copy = listOf(copied(LONG_LINK)),
      history = listOf(HomeSearchSamples.history("Lynket browser issue 214: links open twice", LONG_LINK)),
    ),
    type = LONG_LINK,
  )

  @Config(qualifiers = FOLD)
  @Test fun homeSearch_collapsed_light_fold() = capture(tap = false)

  @Config(qualifiers = FOLD)
  @Test fun homeSearch_typed_light_fold() = capture(suggestions = HomeSearchSamples.kotlin(), type = "kotlin")
}

private const val LONG_LINK =
  "https://github.com/arunkumar9t2/lynket-browser/issues/214?utm_source=newsletter&utm_medium=email&utm_campaign=weekly-digest"
private const val PHONE = "w411dp-h891dp-xxhdpi"
private const val FOLD = "w841dp-h701dp-xxhdpi"
