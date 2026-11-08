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

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.Preferences as StoredValues
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.bubbles.system.BubbleGate
import arun.com.chromer.bubbles.system.BubbleSetting
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.preferences.TestStore
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.di.hilt.DataStoreModule
import arun.com.chromer.settings.Preferences
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.settings.SettingsKeyTable.AGGRESSIVE_LOADING
import arun.com.chromer.ui.settings.SettingsKeyTable.CUSTOM_TAB_SHEET
import arun.com.chromer.ui.settings.SettingsKeyTable.CUSTOM_TAB_SIDE_SHEET
import arun.com.chromer.ui.settings.SettingsKeyTable.MINIMIZE_BEHAVIOR
import arun.com.chromer.ui.settings.SettingsKeyTable.NATIVE_BUBBLES
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEADS_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_CLOSE_ON_OPEN
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_ENABLED
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_SIZE
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_SPAWN
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.util.events.EventBus
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A person switches between slide over, web heads and native bubbles on a settings file restored
 * from elsewhere. The real settings store, its repair of a restored file and the real view model
 * draw the Browsing mode, Look & feel and Browsing options screens, so each step shows one chosen
 * mode, rows that agree with it, and every stored choice kept on the way back.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ModeSwitchFlowTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private enum class Screen { BrowsingMode, LookAndFeel, BrowsingOptions }

  private val context: Application get() = ApplicationProvider.getApplicationContext()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var screen by mutableStateOf(Screen.BrowsingMode)
  private lateinit var repository: UserPreferencesRepository
  private var shown by mutableStateOf<SettingsViewModel?>(null)
  private val viewModel: SettingsViewModel get() = checkNotNull(shown)

  private val bubbleGate = mockk<BubbleGate>(relaxed = true).also { gate ->
    every { gate.setting() } returns BubbleSetting.ALL
    every { gate.preference() } returns BubbleSetting.ALL
  }

  @After
  fun tearDown() = scope.cancel()

  private fun string(id: Int) = context.getString(id)

  private val minimizeNormally: String get() = context.resources.getStringArray(R.array.minimizePreferenceKeys)[0]
  private val minimizeToWebHead: String get() = context.resources.getStringArray(R.array.minimizePreferenceKeys)[1]

  /**
   * Writes the two mode switches and the Minimize choice into a settings file with no repair, as a
   * file restored from a backup arrives, then opens that file with the app's own settings store and
   * shows the settings screens from the view model over it.
   */
  private fun open(webHeads: Boolean, nativeBubbles: Boolean, minimize: String) {
    val restored = TestStore()
    runBlocking {
      restored.dataStore.edit {
        it[booleanPreferencesKey(WEB_HEAD_ENABLED)] = webHeads
        it[booleanPreferencesKey(NATIVE_BUBBLES)] = nativeBubbles
        it[stringPreferencesKey(MINIMIZE_BEHAVIOR)] = minimize
      }
      restored.close()
    }
    val store = DataStoreModule.settingsDataStore(context) { restored.file }
    repository = UserPreferencesRepository(store, scope)
    val preferences = Preferences(context, repository, scope)
    shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    shown = SettingsViewModel(context, repository, preferences, mockk<TabsManager>(relaxed = true), EventBus(), bubbleGate)
    screen = Screen.BrowsingMode
    if (!contentSet) setContent()
    composeRule.waitForIdle()
  }

  private var contentSet = false

  /** The screens draw from whichever view model the last [open] built. */
  private fun setContent() {
    contentSet = true
    composeRule.setContent {
      val viewModel = shown ?: return@setContent
      key(viewModel) { Screens(viewModel) }
    }
  }

  @Composable
  private fun Screens(viewModel: SettingsViewModel) {
    val values by viewModel.values.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    val labels by viewModel.appLabels.collectAsStateWithLifecycle()
    ChromerTheme(themeMode = ThemeMode.Light) {
      when (screen) {
        Screen.BrowsingMode -> BrowsingModeContent(
          values = values,
          showNativeBubbles = true,
          onBack = {},
          onMode = { viewModel.chooseMode(it, overlayGranted = true, notificationsAllowed = true) },
          onSet = viewModel::set,
          bubbleSetting = BubbleSetting.ALL,
        )
        Screen.LookAndFeel -> LookAndFeelContent(values, showWallpaperColours = false, onBack = {}, onSet = viewModel::set)
        Screen.BrowsingOptions -> BrowsingOptionsContent(values, labels, onBack = {}, onSet = viewModel::set, onOpen = {}, onPickApp = {})
      }
      SettingsDialogHost(dialogs.firstOrNull(), viewModel)
    }
  }

  private fun show(next: Screen) {
    screen = next
    composeRule.waitForIdle()
  }

  private fun row(tag: String): SemanticsNodeInteraction {
    composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
    return composeRule.onNodeWithTag(tag)
  }

  /** Store writes finish on an IO thread and resume on the main looper, so the looper is run between checks. */
  private fun awaitStore(condition: (StoredValues) -> Boolean) {
    repeat(500) {
      composeRule.waitForIdle()
      shadowOf(Looper.getMainLooper()).idle()
      if (condition(runBlocking { repository.fresh() })) return
      Thread.sleep(10)
    }
    error("The settings store did not reach the expected value")
  }

  private fun stored(): StoredValues = runBlocking { repository.fresh() }

  private fun StoredValues.bool(name: String) = this[booleanPreferencesKey(name)]

  private fun StoredValues.text(name: String) = this[stringPreferencesKey(name)]

  private val modeTags = mapOf(
    BrowsingMode.SlideOver to "mode-slide-over",
    BrowsingMode.WebHeads to "mode-web-heads",
    BrowsingMode.NativeBubbles to "mode-native-bubbles",
  )

  /** One radio row is selected, the one of [mode]. */
  private fun assertSelectedMode(mode: BrowsingMode) {
    modeTags.forEach { (each, tag) ->
      val node = composeRule.onNodeWithTag(tag)
      if (each == mode) node.assertIsSelected() else node.assertIsNotSelected()
    }
  }

  private val lookAndFeelWebHeadRows = listOf(WEB_HEADS_COLOR, WEB_HEAD_SPAWN, WEB_HEAD_SIZE)
  private val browsingOptionsWebHeadRows = listOf(AGGRESSIVE_LOADING, WEB_HEAD_CLOSE_ON_OPEN)

  private fun assertTheNoteShows() {
    composeRule.onNodeWithTag("group-note").assertTextEquals(string(R.string.only_used_in_web_heads_mode))
  }

  private fun assertNoNote() {
    composeRule.onAllNodesWithTag("group-note").assertCountEquals(0)
  }

  /** Both screens with every web head row taking input, no note, and Minimize showing [minimizeLabel]. */
  private fun assertWebHeadRowsTakeInput(minimizeLabel: String) {
    show(Screen.LookAndFeel)
    lookAndFeelWebHeadRows.forEach { row("row-$it").assertIsEnabled() }
    assertNoNote()
    show(Screen.BrowsingOptions)
    browsingOptionsWebHeadRows.forEach { row("row-$it").assertIsEnabled() }
    assertNoNote()
    row("row-$MINIMIZE_BEHAVIOR").assertIsEnabled()
      .assertTextContains(minimizeLabel)
      .assert(!hasText(string(R.string.minimize_always_opens_bubble)))
  }

  /** Both screens with every web head row grey under the note. */
  private fun assertWebHeadRowsAreGreyUnderTheNote() {
    show(Screen.LookAndFeel)
    lookAndFeelWebHeadRows.forEach { row("row-$it").assertIsNotEnabled() }
    assertTheNoteShows()
    show(Screen.BrowsingOptions)
    browsingOptionsWebHeadRows.forEach { row("row-$it").assertIsNotEnabled() }
    assertTheNoteShows()
  }

  @Test
  fun aRestoredSetWithBothSwitchesOnOpensInWebHeadsModeAndKeepsTheMinimizeChoice() {
    open(webHeads = true, nativeBubbles = true, minimize = minimizeToWebHead)

    // The first frame already shows one mode: no store write has been waited for.
    assertSelectedMode(BrowsingMode.WebHeads)
    val stored = stored()
    assertThat(stored.bool(WEB_HEAD_ENABLED)).isTrue()
    assertThat(stored.bool(NATIVE_BUBBLES)).isFalse()
    assertThat(stored.text(MINIMIZE_BEHAVIOR)).isEqualTo(minimizeToWebHead)

    assertWebHeadRowsTakeInput(string(R.string.minimize_to_web_head))
  }

  @Test
  fun aRestoredSetWithOneModeKeepsThatModeAndTheRowsAgree() {
    val sets = mapOf(
      BrowsingMode.SlideOver to (false to false),
      BrowsingMode.WebHeads to (true to false),
      BrowsingMode.NativeBubbles to (false to true),
    )
    sets.forEach { (mode, switches) ->
      val (webHeads, nativeBubbles) = switches
      open(webHeads, nativeBubbles, minimize = minimizeNormally)

      assertSelectedMode(mode)
      assertThat(stored().bool(WEB_HEAD_ENABLED)).isEqualTo(webHeads)
      assertThat(stored().bool(NATIVE_BUBBLES)).isEqualTo(nativeBubbles)
      if (mode == BrowsingMode.WebHeads) {
        assertWebHeadRowsTakeInput(string(R.string.minimize_normally))
      } else {
        assertWebHeadRowsAreGreyUnderTheNote()
      }
      show(Screen.BrowsingMode)
    }
  }

  /** From slide over, Native Bubbles (Beta) is chosen and its warning closed. */
  private fun chooseNativeBubbles() {
    open(webHeads = false, nativeBubbles = false, minimize = minimizeToWebHead)
    assertSelectedMode(BrowsingMode.SlideOver)

    composeRule.onNodeWithTag("mode-native-bubbles").performClick()
    composeRule.onNodeWithText(string(R.string.browsing_mode_native_bubbles_warning)).assertExists()
    awaitStore { it.bool(NATIVE_BUBBLES) == true }
    composeRule.onNodeWithText(string(android.R.string.ok)).performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithText(string(R.string.browsing_mode_native_bubbles_warning)).assertDoesNotExist()
    composeRule.onNodeWithText(string(R.string.bubbles_notification_explainer_title)).assertDoesNotExist()
    composeRule.onNodeWithText(string(R.string.bubble_setting_explainer_title)).assertDoesNotExist()
    assertThat(viewModel.dialogs.value).isEmpty()
    assertSelectedMode(BrowsingMode.NativeBubbles)
  }

  @Test
  fun choosingNativeBubblesGreysTheWebHeadRowsAndMinimizeSaysItOpensABubble() {
    chooseNativeBubbles()

    assertWebHeadRowsAreGreyUnderTheNote()
    row("row-$MINIMIZE_BEHAVIOR").assertIsNotEnabled()
      .assertTextContains(string(R.string.minimize_always_opens_bubble))
      .assert(!hasText(string(R.string.minimize_to_web_head)))
      .performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("list-dialog").assertDoesNotExist()
    assertThat(stored().text(MINIMIZE_BEHAVIOR)).isEqualTo(minimizeToWebHead)
  }

  @Test
  fun goingBackToWebHeadsEnablesEveryRowAndShowsTheChosenMinimizeChoiceAgain() {
    chooseNativeBubbles()
    assertWebHeadRowsAreGreyUnderTheNote()

    show(Screen.BrowsingMode)
    composeRule.onNodeWithTag("mode-web-heads").performClick()
    awaitStore { it.bool(WEB_HEAD_ENABLED) == true && it.bool(NATIVE_BUBBLES) == false }
    assertSelectedMode(BrowsingMode.WebHeads)
    assertThat(viewModel.dialogs.value).isEmpty()

    assertWebHeadRowsTakeInput(string(R.string.minimize_to_web_head))
    assertThat(stored().text(MINIMIZE_BEHAVIOR)).isEqualTo(minimizeToWebHead)
  }

  @Test
  fun turningTheSheetOnShowsTheSideSheetOptionAndStoresBoth() {
    // An updated person in slide over: the file holds neither sheet key.
    open(webHeads = false, nativeBubbles = false, minimize = minimizeNormally)
    assertSelectedMode(BrowsingMode.SlideOver)
    row("row-$CUSTOM_TAB_SHEET").assertIsOff()
    composeRule.onNodeWithTag("row-$CUSTOM_TAB_SIDE_SHEET").assertDoesNotExist()
    assertThat(stored().bool(CUSTOM_TAB_SHEET)).isNull()
    assertThat(stored().bool(CUSTOM_TAB_SIDE_SHEET)).isNull()

    row("row-$CUSTOM_TAB_SHEET").performClick()
    awaitStore { it.bool(CUSTOM_TAB_SHEET) == true && it.bool(CUSTOM_TAB_SIDE_SHEET) == true }
    row("row-$CUSTOM_TAB_SHEET").assertIsOn()
    row("row-$CUSTOM_TAB_SIDE_SHEET").assertIsOn()

    // Web heads hide both options and keep their stored values.
    row("mode-web-heads").performClick()
    awaitStore { it.bool(WEB_HEAD_ENABLED) == true }
    assertSelectedMode(BrowsingMode.WebHeads)
    composeRule.onNodeWithTag("row-$CUSTOM_TAB_SHEET").assertDoesNotExist()
    composeRule.onNodeWithTag("row-$CUSTOM_TAB_SIDE_SHEET").assertDoesNotExist()
    assertThat(stored().bool(CUSTOM_TAB_SHEET)).isTrue()
    assertThat(stored().bool(CUSTOM_TAB_SIDE_SHEET)).isTrue()

    // Back in slide over, both options show again, on.
    row("mode-slide-over").performClick()
    awaitStore { it.bool(WEB_HEAD_ENABLED) == false }
    assertSelectedMode(BrowsingMode.SlideOver)
    row("row-$CUSTOM_TAB_SHEET").assertIsOn()
    row("row-$CUSTOM_TAB_SIDE_SHEET").assertIsOn()
  }
}
