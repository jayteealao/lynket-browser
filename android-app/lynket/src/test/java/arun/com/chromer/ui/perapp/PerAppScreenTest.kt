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

package arun.com.chromer.ui.perapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.data.apps.AppRepository
import arun.com.chromer.data.common.App
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.ui.settings.SettingsKeyTable
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Per app settings with 300 apps: the loading list, search, the two rules of each app and the
 * master switch; each rule is saved in the per-app rule store as it is tapped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PerAppScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val context: Application get() = ApplicationProvider.getApplicationContext()

  private val apps = (1..300).map { n ->
    PerAppRow(packageName = "com.example.app%03d".format(n), appName = "App %03d".format(n), blacklisted = n % 7 == 0, incognito = n % 11 == 0)
  }

  private val log = mutableListOf<String>()

  private fun show(initial: PerAppState, enabled: Boolean = true): (PerAppState) -> Unit {
    var state by mutableStateOf(initial)
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        PerAppContent(
          state = state,
          enabled = enabled,
          onBack = {},
          onSearch = { state = state.copy(query = it); log += "search:$it" },
          onRefresh = { log += "refresh" },
          onMaster = { log += "master:$it" },
          onBlacklist = { pkg, on -> log += "blacklist:$pkg:$on" },
          onIncognito = { pkg, on -> log += "incognito:$pkg:$on" },
        )
      }
    }
    composeRule.waitForIdle()
    return { state = it }
  }

  private fun scrollTo(tag: String) = composeRule.onNodeWithTag("per-app-list").performScrollToNode(hasTestTag(tag))

  @Test
  fun theLoadingListShowsUntilTheAppsArrive() {
    val update = show(PerAppState(apps = null, refreshing = true))
    composeRule.onNodeWithTag("loading-list").assertExists()
    update(PerAppState(apps = apps))
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("loading-list").assertDoesNotExist()
    composeRule.onNodeWithTag("app-com.example.app001").assertExists()
  }

  @Test
  fun everyOneOfThreeHundredAppsCanBeReached() {
    show(PerAppState(apps = apps))
    scrollTo("app-com.example.app300")
    composeRule.onNodeWithTag("app-com.example.app300").assertExists()
    scrollTo("app-com.example.app014")
    composeRule.onNodeWithTag("blacklist-com.example.app014").assertIsOn()
    composeRule.onNodeWithTag("incognito-com.example.app014").assertIsOff()
  }

  @Test
  fun eachRuleReportsItsApp() {
    show(PerAppState(apps = apps))
    scrollTo("app-com.example.app007")
    composeRule.onNodeWithTag("blacklist-com.example.app007").performClick()
    composeRule.onNodeWithTag("incognito-com.example.app007").performClick()
    assertThat(log).containsExactly("blacklist:com.example.app007:false", "incognito:com.example.app007:true").inOrder()
  }

  @Test
  fun searchNarrowsTheListByNameOrPackage() {
    val update = show(PerAppState(apps = apps))
    composeRule.onNodeWithTag("per-app-search-toggle").performClick()
    update(PerAppState(apps = apps, query = "app 25"))
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("app-com.example.app250").assertExists()
    composeRule.onNodeWithTag("app-com.example.app001").assertDoesNotExist()
    update(PerAppState(apps = apps, query = "nothing like this"))
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("per-app-no-match").assertExists()
  }

  @Test
  fun theSearchFieldShowsOnScreenWhenSearchIsTapped() {
    show(PerAppState(apps = apps))
    composeRule.onNodeWithTag("per-app-search-toggle").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("per-app-search").assertIsDisplayed()
  }

  @Test
  fun theMasterSwitchReportsItsState() {
    show(PerAppState(apps = apps), enabled = false)
    composeRule.onNodeWithTag("row-${SettingsKeyTable.PER_APP_SETTINGS}").assertIsOff().performClick()
    assertThat(log).containsExactly("master:true")
  }

  @Test
  fun visibleFiltersIgnoringCase() {
    val state = PerAppState(apps = apps, query = "COM.EXAMPLE.APP29")
    assertThat(state.visible!!.map { it.packageName }).containsExactly(
      "com.example.app290", "com.example.app291", "com.example.app292", "com.example.app293", "com.example.app294",
      "com.example.app295", "com.example.app296", "com.example.app297", "com.example.app298", "com.example.app299",
    )
    assertThat(PerAppState(apps = null).visible).isNull()
  }
}

/** The view model saves each toggle in the per-app rule store and keeps the row in step. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PerAppViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val repository = mockk<AppRepository>()
  private val preferences = mockk<UserPreferencesRepository>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { preferences.snapshot } returns MutableStateFlow(emptyPreferences())
    every { preferences.current() } returns emptyPreferences()
    coEvery { preferences.setValue(any(), any()) } just runs
    coEvery { repository.allApps() } returns listOf(
      App("Alpha", "com.alpha", false, false, 0),
      App("Beta", "com.beta", true, false, 0),
    )
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() =
    PerAppViewModel(ApplicationProvider.getApplicationContext(), repository, preferences, dispatcher)

  @Test
  fun aToggleIsSavedAndTheRowFollows() = runTest(dispatcher) {
    coEvery { repository.setPackageBlacklisted("com.alpha") } returns App("Alpha", "com.alpha", true, false, 0)
    coEvery { repository.removeBlacklist("com.beta") } returns App("Beta", "com.beta", false, false, 0)
    coEvery { repository.setPackageIncognito("com.alpha") } returns App("Alpha", "com.alpha", true, true, 0)
    val vm = viewModel()
    advanceUntilIdle()
    assertThat(vm.uiState.value.apps).hasSize(2)

    vm.setBlacklisted("com.alpha", true)
    vm.setBlacklisted("com.beta", false)
    advanceUntilIdle()
    vm.setIncognito("com.alpha", true)
    advanceUntilIdle()

    coVerify { repository.setPackageBlacklisted("com.alpha") }
    coVerify { repository.removeBlacklist("com.beta") }
    coVerify { repository.setPackageIncognito("com.alpha") }
    val rows = vm.uiState.value.apps!!.associateBy { it.packageName }
    assertThat(rows.getValue("com.alpha").blacklisted).isTrue()
    assertThat(rows.getValue("com.alpha").incognito).isTrue()
    assertThat(rows.getValue("com.beta").blacklisted).isFalse()
  }

  @Test
  fun theMasterSwitchNeedsUsageAccess() = runTest(dispatcher) {
    val vm = viewModel()
    advanceUntilIdle()
    vm.setPerAppEnabled(on = true, canReadUsageStats = false)
    advanceUntilIdle()
    coVerify(exactly = 0) { preferences.setValue(SettingsKeyTable.PER_APP_SETTINGS, any()) }

    vm.setPerAppEnabled(on = true, canReadUsageStats = true)
    advanceUntilIdle()
    coVerify { preferences.setValue(SettingsKeyTable.PER_APP_SETTINGS, true) }
  }
}
