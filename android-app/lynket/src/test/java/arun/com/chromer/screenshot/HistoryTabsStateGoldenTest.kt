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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.history.HistoryListItem
import arun.com.chromer.history.HistoryUiState
import arun.com.chromer.tabs.ARTICLE
import arun.com.chromer.tabs.CUSTOM_TAB
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.WEB_VIEW
import arun.com.chromer.tabs.ui.TabsUiState
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.history.HistoryActions
import arun.com.chromer.ui.history.HistoryContent
import arun.com.chromer.ui.history.TEST_NOW
import arun.com.chromer.ui.history.TEST_ZONE
import arun.com.chromer.ui.history.UiDispatcherResetRule
import arun.com.chromer.ui.history.loaded
import arun.com.chromer.ui.history.testSites
import arun.com.chromer.ui.history.withRefresh
import arun.com.chromer.ui.history.worstSites
import arun.com.chromer.ui.history.worstTabs
import arun.com.chromer.ui.tabs.TabsActions
import arun.com.chromer.ui.tabs.TabsContent
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History and Tabs in each of their states (default, empty, loading, error and the worst rows
 * people keep), light and dark, on a phone and a fold, plus the worst rows at 200% font on a
 * phone. The worst goldens show the address lines: no scheme or `www.`, cut in the middle, the
 * `file:` scheme kept, untitled pages titled by their host, and on Tabs the type label whole.
 * Record with `./gradlew :lynket:recordRoborazziDebug --tests '*HistoryTabsStateGoldenTest*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class HistoryTabsStateGoldenTest(
  private val screen: String,
  private val state: String,
  private val theme: ThemeMode,
  private val size: Size,
  private val fontScale: Float,
) {

  enum class Size(val qualifiers: String) {
    Phone("w411dp-h891dp-xxhdpi"),
    Fold("w841dp-h701dp-xxhdpi"),
  }

  @get:Rule(order = 0)
  val window: TestRule = TestRule { base, _: Description ->
    object : Statement() {
      override fun evaluate() {
        RuntimeEnvironment.setQualifiers(size.qualifiers)
        base.evaluate()
      }
    }
  }

  @get:Rule(order = 1)
  val uiDispatcherReset = UiDispatcherResetRule()

  @get:Rule(order = 2)
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  @Composable
  private fun history(data: PagingData<HistoryListItem>) = HistoryContent(
    state = HistoryUiState(),
    items = flowOf(data).collectAsLazyPagingItems(),
    providerName = "Chrome",
    actions = HistoryActions(),
    now = TEST_NOW,
    timeZone = TEST_ZONE,
  )

  @Composable
  private fun tabs(state: TabsUiState) = TabsContent(state, closeIcon = true, actions = TabsActions())

  @Composable
  private fun content() {
    when (screen) {
      "history" -> history(
        when (state) {
          "default" -> loaded(testSites)
          "empty" -> loaded(emptyList())
          "loading" -> withRefresh(LoadState.Loading)
          "error" -> withRefresh(LoadState.Error(IllegalStateException("disk")))
          else -> loaded(worstSites)
        },
      )
      else -> tabs(
        when (state) {
          "default" -> TabsUiState.Loaded(defaultTabs)
          "empty" -> TabsUiState.Loaded(emptyList())
          "loading" -> TabsUiState.Loading
          "error" -> TabsUiState.Error("The open tabs could not be read.")
          else -> TabsUiState.Loaded(worstTabs)
        },
      )
    }
  }

  @Test
  fun capture() {
    composeRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * fontScale)) {
        ChromerTheme(themeMode = theme) {
          Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) { content() }
        }
      }
    }
    composeRule.waitForIdle()
    val font = if (fontScale == 1f) "" else "_font200"
    val name = "${screen}_${state}${font}_${theme.name.lowercase()}_${size.name.lowercase()}"
    composeRule.onRoot().captureRoboImage("src/test/screenshots/arun.com.chromer.screenshot.HistoryTabsStateGoldenTest.$name.png")
  }

  companion object {
    private fun tab(url: String, type: Int, title: String) =
      TabsManager.Tab(url, type, Website(title, url, null, null, null, null, false, 0, 1))

    private val defaultTabs = listOf(
      tab("https://developer.android.com/compose", CUSTOM_TAB, "Compose BOM mapping"),
      tab("https://www.reddit.com/r/androiddev", CUSTOM_TAB, "r/androiddev"),
      tab("https://www.theverge.com/android", ARTICLE, "The Verge"),
      tab("https://medium.com/kotlin-coroutines", WEB_VIEW, "Kotlin coroutines guide"),
    )

    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}_{2}_{3}_{4}")
    fun cases(): List<Array<Any>> {
      val matrix = listOf("history", "tabs").flatMap { screen ->
        listOf("default", "empty", "loading", "error", "worst").flatMap { state ->
          listOf(ThemeMode.Light, ThemeMode.Dark).flatMap { theme ->
            Size.entries.map { size -> arrayOf<Any>(screen, state, theme, size, 1f) }
          }
        }
      }
      val largestFont = listOf("history", "tabs").map { arrayOf<Any>(it, "worst", ThemeMode.Light, Size.Phone, 2f) }
      return matrix + largestFont
    }
  }
}
