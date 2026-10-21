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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.history.HistoryDialog
import arun.com.chromer.history.HistoryUiState
import arun.com.chromer.tabs.ARTICLE
import arun.com.chromer.tabs.CUSTOM_TAB
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.WEB_VIEW
import arun.com.chromer.tabs.ui.TabsUiState
import arun.com.chromer.ui.history.HistoryActions
import arun.com.chromer.ui.history.HistoryContent
import arun.com.chromer.history.HistoryListItem
import arun.com.chromer.ui.history.TEST_NOW
import arun.com.chromer.ui.history.UiDispatcherResetRule
import arun.com.chromer.ui.history.TEST_ZONE
import arun.com.chromer.ui.history.loaded
import arun.com.chromer.ui.history.testSites
import arun.com.chromer.ui.history.withRefresh
import arun.com.chromer.ui.tabs.TabsActions
import arun.com.chromer.ui.tabs.TabsContent
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of History (default on a phone and a fold, empty, loading, the delete-all dialog, dark)
 * and Tabs (default, empty, black). Record with `./gradlew :lynket:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = HT_PHONE)
class HistoryTabsGoldenTest {

  @get:Rule(order = 0)
  val uiDispatcherReset = UiDispatcherResetRule()

  @get:Rule(order = 1)
  val composeRule = createComposeRule()

  private val tabs = listOf(
    tab("https://developer.android.com/compose", CUSTOM_TAB, "Compose BOM mapping"),
    tab("https://www.reddit.com/r/androiddev", CUSTOM_TAB, "r/androiddev"),
    tab("https://www.theverge.com/android", ARTICLE, "The Verge"),
    tab("https://medium.com/kotlin-coroutines", WEB_VIEW, "Kotlin coroutines guide"),
  )

  private fun tab(url: String, type: Int, title: String) =
    TabsManager.Tab(url, type, Website(title, url, null, null, null, null, false, 0, 1))

  private fun capture(theme: ThemeMode = ThemeMode.Light, dialog: Boolean = false, content: @Composable () -> Unit) {
    composeRule.setContent {
      ChromerTheme(themeMode = theme) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { content() }
      }
    }
    composeRule.waitForIdle()
    if (dialog) composeRule.onNode(isDialog()).captureRoboImage() else composeRule.onRoot().captureRoboImage()
  }

  @Composable
  private fun history(data: PagingData<HistoryListItem>, state: HistoryUiState = HistoryUiState()) = HistoryContent(
    state = state,
    items = flowOf(data).collectAsLazyPagingItems(),
    providerName = "Chrome",
    actions = HistoryActions(),
    now = TEST_NOW,
    timeZone = TEST_ZONE,
  )

  @Test fun history_default_phone() = capture { history(loaded(testSites)) }

  @Config(qualifiers = HT_FOLD)
  @Test fun history_default_fold() = capture { history(loaded(testSites)) }

  @Test fun history_empty_phone() = capture { history(loaded(emptyList())) }

  @Test fun history_loading_phone() = capture { history(withRefresh(LoadState.Loading)) }

  @Test fun history_clear_confirm_phone() = capture(dialog = true) {
    history(loaded(testSites), HistoryUiState(dialog = HistoryDialog.ClearConfirm))
  }

  @Test fun history_default_dark_phone() = capture(ThemeMode.Dark) { history(loaded(testSites)) }

  @Test fun tabs_default_phone() = capture { TabsContent(TabsUiState.Loaded(tabs), closeIcon = true, actions = TabsActions()) }

  @Test fun tabs_empty_phone() = capture { TabsContent(TabsUiState.Loaded(emptyList()), closeIcon = true, actions = TabsActions()) }

  @Test fun tabs_default_black_phone() = capture(ThemeMode.Black) {
    TabsContent(TabsUiState.Loaded(tabs), closeIcon = true, actions = TabsActions())
  }
}

private const val HT_PHONE = "w411dp-h891dp-xxhdpi"
private const val HT_FOLD = "w841dp-h701dp-xxhdpi"
