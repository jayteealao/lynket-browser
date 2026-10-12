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

package arun.com.chromer.ui.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.browsing.providerselection.ModernProviderSelectionViewModel.ProviderSelectionUiState
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.ui.navigation.LynketKey
import arun.com.chromer.ui.providerselection.ProviderSelectionContent
import arun.com.chromer.ui.settings.SettingsKeyTable.ANIMATION_SPEED
import arun.com.chromer.ui.settings.SettingsKeyTable.ANIMATION_TYPE
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR_APP
import arun.com.chromer.ui.settings.SettingsKeyTable.PREFERRED_ACTION
import arun.com.chromer.ui.settings.SettingsKeyTable.THEME_MODE
import arun.com.chromer.ui.settings.SettingsKeyTable.TOOLBAR_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.TOOLBAR_COLOR_PREF
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_ENABLED
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Each settings screen: its rows, the rows that follow another, and where each row leads. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SettingsScreensUiTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val context: Application get() = ApplicationProvider.getApplicationContext()
  private val defaults: SettingsValues get() = SettingsValues.from(emptyPreferences(), context)
  private val writes = mutableListOf<Pair<String, Any?>>()
  private val opened = mutableListOf<LynketKey>()

  private fun show(content: @Composable () -> Unit) {
    composeRule.setContent { ChromerTheme(themeMode = ThemeMode.Light) { content() } }
    composeRule.waitForIdle()
  }

  private fun row(tag: String): SemanticsNodeInteraction {
    composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
    return composeRule.onNodeWithTag(tag)
  }

  private fun string(id: Int) = context.getString(id)

  // Settings root

  @Test
  fun theRootShowsTheDefaultBrowserCardOnlyWhenNeeded() {
    show { SettingsRootContent(showDefaultBrowserCard = true, version = "1.0", onBack = {}, onSetDefault = {}, onOpen = { opened += it }) }
    composeRule.onNodeWithTag("default-browser-card").assertIsDisplayed()
  }

  @Test
  fun theRootGroupsOpenTheirScreens() {
    show { SettingsRootContent(showDefaultBrowserCard = false, version = "1.0", onBack = {}, onSetDefault = {}, onOpen = { opened += it }) }
    composeRule.onNodeWithTag("default-browser-card").assertDoesNotExist()
    row("row-browsing-mode").performClick()
    row("row-look-and-feel").performClick()
    row("row-browsing-options").performClick()
    row("row-about").performClick()
    assertThat(opened).containsExactly(
      LynketKey.BrowsingMode, LynketKey.LookAndFeel, LynketKey.BrowsingOptions, LynketKey.About,
    ).inOrder()
  }

  // Browsing mode

  @Test
  fun browsingModeSelectsTheStoredMode() {
    val modes = mutableListOf<BrowsingMode>()
    show {
      BrowsingModeContent(defaults.with(WEB_HEAD_ENABLED to true), showNativeBubbles = true, onBack = {}, onMode = { modes += it }, onSet = { _, _ -> })
    }
    row("mode-web-heads").assertIsSelected()
    row("mode-native-bubbles").performClick()
    row("mode-slide-over").performClick()
    assertThat(modes).containsExactly(BrowsingMode.NativeBubbles, BrowsingMode.SlideOver).inOrder()
  }

  @Test
  fun nativeBubblesAreHiddenBeforeAndroid10() {
    show { BrowsingModeContent(defaults, showNativeBubbles = false, onBack = {}, onMode = {}, onSet = { _, _ -> }) }
    composeRule.onNodeWithTag("mode-native-bubbles").assertDoesNotExist()
    row("mode-slide-over").assertIsSelected()
  }

  @Test
  fun loadFasterSwitchesWriteTheirKeys() {
    show { BrowsingModeContent(defaults, showNativeBubbles = true, onBack = {}, onMode = {}, onSet = { k, v -> writes += k to v }) }
    row("row-${SettingsKeyTable.AMP_MODE}").assertIsOff().performClick()
    row("row-${SettingsKeyTable.ARTICLE_MODE}").performClick()
    assertThat(writes).containsExactly(SettingsKeyTable.AMP_MODE to true, SettingsKeyTable.ARTICLE_MODE to true).inOrder()
  }

  // Look and feel

  private fun lookAndFeel(values: SettingsValues, wallpaper: Boolean = true, openList: String? = null, openColour: String? = null) = show {
    LookAndFeelContent(values, wallpaper, onBack = {}, onSet = { k, v -> writes += k to v }, openList = openList, openColour = openColour)
  }

  @Test
  fun themeChipsWriteTheMode() {
    lookAndFeel(defaults)
    row("theme-dark").performClick()
    row("theme-black").performClick()
    assertThat(writes).containsExactly(THEME_MODE to ThemeMode.Dark.ordinal, THEME_MODE to ThemeMode.Black.ordinal).inOrder()
  }

  @Test
  fun wallpaperColoursShowOnlyWhereAvailable() {
    lookAndFeel(defaults, wallpaper = false)
    composeRule.onNodeWithTag("row-${SettingsKeyTable.USE_WALLPAPER_COLOURS}").assertDoesNotExist()
  }

  @Test
  fun colouredToolbarOffDisablesItsRows() {
    lookAndFeel(defaults.with(TOOLBAR_COLOR_PREF to false))
    row("row-$TOOLBAR_COLOR").assertIsNotEnabled()
    row("row-$DYNAMIC_COLOR").assertIsNotEnabled()
  }

  @Test
  fun colouredToolbarOnEnablesItsRows() {
    lookAndFeel(defaults.with(TOOLBAR_COLOR_PREF to true))
    row("row-$TOOLBAR_COLOR").assertIsEnabled()
    row("row-$DYNAMIC_COLOR").assertIsEnabled()
  }

  @Test
  fun dynamicColourBoxesShowOnlyWhileItIsOn() {
    lookAndFeel(defaults.with(TOOLBAR_COLOR_PREF to true, DYNAMIC_COLOR to false))
    composeRule.onNodeWithTag("row-$DYNAMIC_COLOR_APP").assertDoesNotExist()
  }

  @Test
  fun dynamicColourBoxesWriteTheirKeys() {
    lookAndFeel(defaults.with(TOOLBAR_COLOR_PREF to true, DYNAMIC_COLOR to true))
    row("row-$DYNAMIC_COLOR_APP").assertIsOff().performClick()
    assertThat(writes).containsExactly(DYNAMIC_COLOR_APP to true)
  }

  @Test
  fun noAnimationDisablesTheSpeed() {
    lookAndFeel(defaults.with(ANIMATION_TYPE to 0))
    row("row-$ANIMATION_SPEED").assertIsNotEnabled()
  }

  @Test
  fun webHeadRowsCarryTheSlideOverNoticeAndAreDisabled() {
    lookAndFeel(defaults.with(WEB_HEAD_ENABLED to false))
    row("row-${SettingsKeyTable.WEB_HEAD_SIZE}").assertIsNotEnabled()
    composeRule.onAllNodesWithTag("slide-over-notice")[0].assertExists()
  }

  @Test
  fun aListRowOpensItsChoicesAndWritesTheStoredValue() {
    lookAndFeel(defaults, openList = PREFERRED_ACTION)
    composeRule.onNodeWithTag("list-dialog").assertIsDisplayed()
    composeRule.onNodeWithText(context.resources.getStringArray(R.array.actionValues)[1]).performClick()
    assertThat(writes).containsExactly(PREFERRED_ACTION to context.resources.getStringArray(R.array.actionKeys)[1].toInt())
  }

  @Test
  fun theColourSheetRejectsABadHexAndWritesAGoodOne() {
    lookAndFeel(defaults.with(TOOLBAR_COLOR_PREF to true), openColour = TOOLBAR_COLOR)
    composeRule.onNodeWithTag("colour-hex").performTextReplacement("#12")
    composeRule.onNodeWithTag("colour-done").assertIsNotEnabled()
    composeRule.onNodeWithTag("colour-hex").performTextReplacement("#3F51B5")
    // The sheet may still be settling, so the button is pressed through its click action.
    composeRule.onNodeWithTag("colour-done").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
    composeRule.waitForIdle()
    assertThat(writes).containsExactly(TOOLBAR_COLOR to 0xFF3F51B5.toInt())
  }

  // Browsing options

  private fun browsingOptions(values: SettingsValues, labels: AppLabels = AppLabels(providerPackage = "com.android.chrome", providerName = "Chrome")) = show {
    BrowsingOptionsContent(values, labels, onBack = {}, onSet = { k, v -> writes += k to v }, onOpen = { opened += it }, onPickApp = { writes += "pick" to it })
  }

  @Test
  fun browsingOptionsLinkToTheProviderAndPerAppSettings() {
    browsingOptions(defaults)
    row("row-${SettingsKeyTable.PREFERRED_PACKAGE}").performClick()
    row("row-${SettingsKeyTable.PER_APP_LINK}").performClick()
    assertThat(opened).containsExactly(LynketKey.ProviderSelection, LynketKey.PerApp).inOrder()
  }

  @Test
  fun appRowsOpenThePicker() {
    browsingOptions(defaults)
    row("row-${SettingsKeyTable.SECONDARY_BROWSER}").performClick()
    row("row-${SettingsKeyTable.FAV_SHARE}").performClick()
    assertThat(writes).containsExactly("pick" to SettingsKeyTable.SECONDARY_BROWSER, "pick" to SettingsKeyTable.FAV_SHARE).inOrder()
  }

  @Test
  fun aMissingProviderReadsNotFound() {
    browsingOptions(defaults, labels = AppLabels())
    row("row-${SettingsKeyTable.PREFERRED_PACKAGE}")
    composeRule.onNodeWithText(string(R.string.not_found)).assertExists()
  }

  @Test
  fun webHeadOptionsNeedWebHeads() {
    browsingOptions(defaults.with(WEB_HEAD_ENABLED to false))
    row("row-${SettingsKeyTable.AGGRESSIVE_LOADING}").assertIsNotEnabled()
    row("row-${SettingsKeyTable.WEB_HEAD_CLOSE_ON_OPEN}").assertIsNotEnabled()
  }

  @Test
  fun webHeadOptionsWriteWithWebHeadsOn() {
    browsingOptions(defaults.with(WEB_HEAD_ENABLED to true))
    row("row-${SettingsKeyTable.AGGRESSIVE_LOADING}").assertIsEnabled().performClick()
    assertThat(writes.single().first).isEqualTo(SettingsKeyTable.AGGRESSIVE_LOADING)
  }

  @Test
  fun theBottomBarSwitchFollowsTheStore() {
    browsingOptions(defaults.with(SettingsKeyTable.BOTTOM_BAR to true))
    row("row-${SettingsKeyTable.BOTTOM_BAR}").assertIsOn()
  }

  // Provider selection

  private val icon: Uri = Uri.parse("android.resource://com.android.chrome/0")
  private val providers = listOf(
    Provider("com.android.chrome", "Chrome", icon, installed = true),
    Provider("org.mozilla.firefox", "Firefox", icon, installed = false),
  )

  private fun providerScreen(state: ProviderSelectionUiState, log: MutableList<String>) = show {
    ProviderSelectionContent(
      state = state,
      onBack = {},
      onUseProvider = { log += "use:${it.packageName}" },
      onInstall = { log += "install:${it.packageName}" },
      onUseWebView = { log += "webview" },
      onGetChrome = { log += "chrome" },
      onRetry = { log += "retry" },
    )
  }

  @Test
  fun providersKeepTheOldOrderWithWebViewFirst() {
    val log = mutableListOf<String>()
    providerScreen(ProviderSelectionUiState.Success(providers, "com.android.chrome", usingWebView = false), log)
    composeRule.onNodeWithText(string(R.string.tab_providers)).assertExists()
    val webView = composeRule.onNodeWithTag("provider-webview").fetchSemanticsNode().boundsInRoot.top
    val chrome = composeRule.onNodeWithTag("provider-com.android.chrome").fetchSemanticsNode().boundsInRoot.top
    val firefox = composeRule.onNodeWithTag("provider-org.mozilla.firefox").fetchSemanticsNode().boundsInRoot.top
    assertThat(webView).isLessThan(chrome)
    assertThat(chrome).isLessThan(firefox)
    composeRule.onNodeWithTag("provider-com.android.chrome").assertIsSelected()
  }

  @Test
  fun aProviderOpensItsDetailsWithUseOrInstall() {
    val log = mutableListOf<String>()
    providerScreen(ProviderSelectionUiState.Success(providers, null, usingWebView = true), log)
    composeRule.onNodeWithTag("provider-com.android.chrome").performClick()
    composeRule.onNodeWithTag("provider-dialog").assertIsDisplayed()
    composeRule.onNodeWithText(string(R.string.use)).performClick()
    composeRule.onNodeWithTag("provider-org.mozilla.firefox").performClick()
    composeRule.onNodeWithTag("provider-confirm").performClick()
    assertThat(log).containsExactly("use:com.android.chrome", "install:org.mozilla.firefox").inOrder()
  }

  @Test
  fun webViewAsksFirst() {
    val log = mutableListOf<String>()
    providerScreen(ProviderSelectionUiState.Success(providers, "com.android.chrome", usingWebView = false), log)
    composeRule.onNodeWithTag("provider-webview").performClick()
    composeRule.onNodeWithText(string(R.string.webview_disadvantages)).assertExists()
    assertThat(log).isEmpty()
    composeRule.onNodeWithText(string(android.R.string.yes)).performClick()
    assertThat(log).containsExactly("webview")
  }

  @Test
  fun withNoProviderTheErrorOffersWebViewOrChrome() {
    val log = mutableListOf<String>()
    providerScreen(ProviderSelectionUiState.NoProvider(emptyList(), usingWebView = false), log)
    composeRule.onNodeWithTag("no-provider").assertIsDisplayed()
    composeRule.onNodeWithText(string(R.string.use_webview)).performClick()
    composeRule.onNodeWithText(string(R.string.get_chrome)).performClick()
    assertThat(log).containsExactly("webview", "chrome").inOrder()
  }
}
