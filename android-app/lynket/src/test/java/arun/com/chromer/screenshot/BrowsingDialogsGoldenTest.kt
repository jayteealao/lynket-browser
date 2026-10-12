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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.browsing.amp.AmpState
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.suggestion.items.SuggestionItem
import arun.com.chromer.ui.browsing.AmpResolverDialog
import arun.com.chromer.ui.browsing.NewTabCard
import arun.com.chromer.ui.browsing.OptionsSheetContent
import arun.com.chromer.ui.browsing.optionItems
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the browsing dialogs: the Lynket options sheet (light, dark, black, fold), New tab
 * with suggestions and the AMP lookup (loading, offline error). Record with
 * `./gradlew :lynket:recordRoborazziDebug`. The shortcut dialog has no golden: its AlertDialog with
 * a text field never went idle in a native-graphics window (repro: AppNotIdleException after 60 s,
 * 2026-10-12); BrowsingDialogsTest covers its controls and the device check captures it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = BD_PHONE)
class BrowsingDialogsGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun capture(theme: ThemeMode = ThemeMode.Light, dialog: Boolean = false, content: @Composable () -> Unit) {
    // The AMP dialog's progress bar never stops, so a dialog's frames are advanced by hand.
    if (dialog) composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      ChromerTheme(themeMode = theme) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { content() }
      }
    }
    if (dialog) {
      composeRule.mainClock.advanceTimeBy(1_000)
    } else {
      composeRule.waitForIdle()
    }
    if (dialog) composeRule.onNode(isDialog()).captureRoboImage() else composeRule.onRoot().captureRoboImage()
  }

  /** The options content on the sheet's surface and shape, at the bottom of the screen. */
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun SheetFrame() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
      Surface(
        shape = BottomSheetDefaults.ExpandedShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.widthIn(max = BottomSheetDefaults.SheetMaxWidth).fillMaxWidth(),
      ) {
        androidx.compose.foundation.layout.Column {
          BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
          OptionsSheetContent(optionItems(fromArticle = false), urlKnown = true, onPick = {})
        }
      }
    }
  }

  @Test
  fun optionsSheet_light() = capture { SheetFrame() }

  @Test
  fun optionsSheet_dark() = capture(ThemeMode.Dark) { SheetFrame() }

  @Test
  fun optionsSheet_black() = capture(ThemeMode.Black) { SheetFrame() }

  @Test
  @Config(qualifiers = BD_FOLD)
  fun optionsSheet_fold() = capture { SheetFrame() }

  @Test
  fun newTab_suggestions() = capture {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
      NewTabCard(
        query = "navigation 3",
        suggestions = Suggestions(
          query = "navigation 3",
          google = listOf(
            SuggestionItem.GoogleSuggestionItem("navigation 3 compose"),
            SuggestionItem.GoogleSuggestionItem("navigation 3 compose deep links"),
            SuggestionItem.GoogleSuggestionItem("navigation 3 compose migration"),
          ),
        ),
        onQueryChange = {},
        onSubmit = {},
        onVoiceUnavailable = {},
        onDismiss = {},
      )
    }
  }

  @Test
  fun amp_loading() = capture(dialog = true) {
    AmpResolverDialog(AmpState.Loading("https://www.theverge.com/2026/10/android-17"), {}, {}, {}, {})
  }

  @Test
  fun amp_error() = capture(dialog = true) {
    AmpResolverDialog(AmpState.Error("https://www.theverge.com/2026/10/android-17"), {}, {}, {}, {})
  }
}

private const val BD_PHONE = "w411dp-h891dp-xxhdpi"
private const val BD_FOLD = "w841dp-h701dp-xxhdpi"
