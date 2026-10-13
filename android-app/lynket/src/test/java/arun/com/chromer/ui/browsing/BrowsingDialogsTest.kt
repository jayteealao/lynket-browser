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

package arun.com.chromer.ui.browsing

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.browsing.amp.AmpState
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.suggestion.items.SuggestionItem
import arun.com.chromer.shortcuts.ShortcutState
import arun.com.chromer.ui.settings.components.AppPickerSheet
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The browsing dialogs: controls, order, enablement and callbacks. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class BrowsingDialogsTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val url = "https://developer.android.com/guide/navigation/navigation-3"

  private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
    composeRule.setContent { ChromerTheme(themeMode = ThemeMode.Light) { content() } }
  }

  // Options sheet

  @Test
  fun optionsKeepTheOldOrder() {
    assertThat(optionItems(fromArticle = false).map { it.name }).containsExactly(
      "Settings", "Tabs", "NewTab", "History", "AddToHomeScreen", "OpenWith", "ArticleView",
    ).inOrder()
    assertThat(optionItems(fromArticle = true)).doesNotContain(OptionItem.ArticleView)
  }

  @Test
  fun eachOptionCallsBackWithItself() {
    val picked = mutableListOf<OptionItem>()
    show { OptionsSheetContent(optionItems(false), urlKnown = true, onPick = { picked += it }) }
    composeRule.onNodeWithText("Lynket", substring = true).assertIsDisplayed()
    OptionItem.entries.forEach { composeRule.onNodeWithTag(it.tag).performClick() }
    assertThat(picked).containsExactlyElementsIn(OptionItem.entries).inOrder()
  }

  @Test
  fun addressItemsAreDisabledWithoutAnAddress() {
    val picked = mutableListOf<OptionItem>()
    show { OptionsSheetContent(optionItems(false), urlKnown = false, onPick = { picked += it }) }
    listOf(OptionItem.AddToHomeScreen, OptionItem.OpenWith, OptionItem.ArticleView).forEach {
      composeRule.onNodeWithTag(it.tag).performClick()
    }
    composeRule.onNodeWithTag(OptionItem.Settings.tag).performClick()
    assertThat(picked).containsExactly(OptionItem.Settings)
  }

  // Open with

  @Test
  fun openWithShowsTheAddressUnderTheTitle() {
    show {
      AppPickerSheet(
        title = "Open with…",
        subtitle = url,
        intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)),
        onPick = {},
        onDismiss = {},
      )
    }
    composeRule.onNodeWithText("Open with…").assertIsDisplayed()
    composeRule.onNodeWithTag("app-picker-subtitle").assertIsDisplayed()
    composeRule.onNodeWithText(url).assertIsDisplayed()
  }

  // New tab

  @Test
  fun newTabOpensOnlyTypedText() {
    val submitted = mutableListOf<String>()
    var dismissed = false
    var query by mutableStateOf("")
    show {
      NewTabCard(
        query = query,
        suggestions = Suggestions(),
        onQueryChange = { query = it },
        onSubmit = { submitted += it },
        onVoiceUnavailable = {},
        onDismiss = { dismissed = true },
      )
    }
    composeRule.onNodeWithText("New tab").assertIsDisplayed()
    composeRule.onNodeWithTag("new-tab-open").assertIsNotEnabled()
    composeRule.onNodeWithTag("new-tab-field").performTextReplacement("example.org")
    composeRule.onNodeWithTag("new-tab-open").assertIsEnabled().performClick()
    composeRule.onNodeWithTag("new-tab-cancel").performClick()
    assertThat(submitted).containsExactly("example.org")
    assertThat(dismissed).isTrue()
  }

  @Test
  fun newTabSuggestionsOpenTheirText() {
    val submitted = mutableListOf<String>()
    val history = Website("Example", "https://example.com/", null, null, null, null, false, 0, 1)
    show {
      NewTabCard(
        query = "exa",
        suggestions = Suggestions(
          query = "exa",
          google = listOf(SuggestionItem.GoogleSuggestionItem("example domain")),
          history = listOf(SuggestionItem.HistorySuggestionItem(history, "Example", "https://example.com/")),
        ),
        onQueryChange = {},
        onSubmit = { submitted += it },
        onVoiceUnavailable = {},
        onDismiss = {},
      )
    }
    composeRule.onNodeWithText("example domain").performClick()
    composeRule.onNodeWithText("Example").performClick()
    assertThat(submitted).containsExactly("example domain", "https://example.com/").inOrder()
  }

  // AMP

  @Test
  fun ampLoadingShowsTheAddressProgressAndSkip() {
    var skipped = false
    show { AmpResolverDialog(AmpState.Loading(url), onSkip = { skipped = true }, onRetry = {}, onOpenNormal = {}, onDismiss = {}) }
    composeRule.onNodeWithText("Grabbing AMP link").assertIsDisplayed()
    composeRule.onNodeWithText("developer.android.com/guide/navigation/navigation-3").assertIsDisplayed()
    composeRule.onNodeWithTag("amp-progress").assertIsDisplayed()
    composeRule.onNodeWithText("Loading…").assertIsDisplayed()
    composeRule.onNodeWithText("Skip").performClick()
    assertThat(skipped).isTrue()
  }

  @Test
  fun ampResultLinesAreTheOldOnes() {
    var state by mutableStateOf<AmpState>(AmpState.Found(Website(url)))
    show { AmpResolverDialog(state, onSkip = {}, onRetry = {}, onOpenNormal = {}, onDismiss = {}) }
    composeRule.onNodeWithText("Link found!").assertIsDisplayed()
    state = AmpState.NotFound(Website(url))
    composeRule.onNodeWithText("AMP link not found").assertIsDisplayed()
    assertThat(composeRule.onAllNodesWithTag("amp-progress").fetchSemanticsNodes()).isEmpty()
  }

  @Test
  fun ampErrorOffersTryAgainAndTheNormalPage() {
    val calls = mutableListOf<String>()
    show {
      AmpResolverDialog(
        AmpState.Error(url),
        onSkip = { calls += "skip" },
        onRetry = { calls += "retry" },
        onOpenNormal = { calls += "normal" },
        onDismiss = {},
      )
    }
    composeRule.onNodeWithText("No AMP version found").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Open normal page").performClick()
    assertThat(calls).containsExactly("retry", "normal").inOrder()
  }

  // Shortcut creator

  @Test
  fun shortcutDialogWaitsForTheIconAndANameBeforeCreate() {
    val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    var state by mutableStateOf<ShortcutState>(ShortcutState.Loading)
    var name by mutableStateOf("")
    var created = false
    show {
      ShortcutCreatorDialog(
        state = state,
        name = name,
        canCreate = state is ShortcutState.Ready && name.isNotBlank(),
        onNameChange = { name = it },
        onCreate = { created = true },
        onDismiss = {},
      )
    }
    composeRule.onNodeWithText("Create shortcut").assertIsDisplayed()
    composeRule.onNodeWithTag("shortcut-create").assertIsNotEnabled()
    state = ShortcutState.Ready(Website("Navigation 3", url, null, null, null, null, false, 0, 1), icon)
    composeRule.onNodeWithText("Name cannot be empty").assertIsDisplayed()
    composeRule.onNodeWithTag("shortcut-name").performTextReplacement("Navigation 3")
    composeRule.onNodeWithTag("shortcut-url").assertIsDisplayed()
    composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    composeRule.onNodeWithTag("shortcut-create").assertIsEnabled().performClick()
    assertThat(created).isTrue()
  }
}
