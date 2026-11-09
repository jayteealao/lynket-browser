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

package arun.com.chromer.ui.home

import android.app.Activity
import android.app.Application
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.SearchBarState
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.material3.rememberSearchBarState
import androidx.activity.ComponentActivity
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.StringResource
import arun.com.chromer.home.CustomTabProviderInfo
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.provider.SearchProvider
import arun.com.chromer.search.provider.SearchProviders
import arun.com.chromer.search.suggestion.items.SuggestionItem
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.util.appicon.ApplicationIcon
import androidx.core.net.toUri
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Home search sample data, shared by the behaviour tests and the search goldens. */
internal object HomeSearchSamples {
  val engines: List<SearchProvider> = listOf(
    SearchProviders.GOOGLE_SEARCH_PROVIDER,
    SearchProvider(SearchProviders.DUCKDUCKGO, "https://duckduckgo.example/icon.png".toUri(), "https://duckduckgo.com/?q="),
    SearchProvider(SearchProviders.BING, "https://bing.example/icon.png".toUri(), "https://www.bing.com/search?q="),
    SearchProvider(SearchProviders.QWANT, "https://qwant.example/icon.png".toUri(), "https://www.qwant.com/?q="),
    SearchProvider(SearchProviders.ECOSIA, "https://ecosia.example/icon.png".toUri(), "https://www.ecosia.org/search?q="),
  )

  val provider = CustomTabProviderInfo(
    iconUri = ApplicationIcon.createUri("com.android.chrome"),
    providerDescription = StringResource(R.string.tab_provider_status_message_home, listOf("Chrome")),
    providerReason = StringResource(0),
  )

  val recents = listOf("github", "wikipedia", "reddit", "medium")
    .map { Website("https://$it.example/").apply { title = it.replaceFirstChar(Char::uppercase) } }

  fun history(title: String, url: String) =
    SuggestionItem.HistorySuggestionItem(Website(url).apply { this.title = title }, title, url)

  /** What the home lists for "kotlin": Google suggestions, then two history rows. */
  fun kotlin() = Suggestions(
    query = "kotlin",
    google = listOf("kotlin", "kotlin tutorial", "kotlin coroutines").map { SuggestionItem.GoogleSuggestionItem(it) },
    history = listOf(
      history("Kotlin Programming Language", "https://kotlinlang.org/"),
      history("Kotlin coroutines guide", "https://kotlinlang.org/docs/coroutines-guide.html"),
    ),
  )
}

/**
 * Puts the window in touch mode, as a person's first tap does on a phone. Without it the view
 * system hands focus back to the first focusable control when the field lets go of it, and the
 * search field opens again. The test injects its taps past the view root, so the root is told.
 */
internal fun Activity.enterTouchMode() {
  val root = window.decorView
  val viewRoot = View::class.java.getMethod("getViewRootImpl").invoke(root)
  viewRoot.javaClass.getDeclaredMethod("ensureTouchMode", Boolean::class.javaPrimitiveType)
    .apply { isAccessible = true }
    .invoke(viewRoot, true)
}

/** The editable search field; while search is open it is the one of the open search. */
internal fun SemanticsNodeInteractionsProvider.searchField(): SemanticsNodeInteraction {
  val matcher = hasTestTag("home-search-field") and hasSetTextAction()
  val count = onAllNodes(matcher).fetchSemanticsNodes().size
  return onAllNodes(matcher)[count - 1]
}

/**
 * The home search bar as a person uses it: open, type, pick an engine, pick a suggestion, search,
 * close with Back, open the drawer, and keep open search across a recreation.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class HomeSearchBarTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val events = mutableListOf<String>()
  private lateinit var drawerState: DrawerState

  /** The real home content around a query held here, as the home screen holds it. */
  @Composable
  private fun Host(suggestions: Suggestions = HomeSearchSamples.kotlin()) {
    var query by remember { mutableStateOf("") }
    drawerState = rememberDrawerState(DrawerValue.Closed)
    ChromerTheme {
      HomeScreenContent(
        state = HomeContentState(
          recents = HomeSearchSamples.recents,
          providerInfo = HomeSearchSamples.provider,
          query = query,
          suggestions = suggestions,
          searchEngines = HomeSearchSamples.engines,
        ),
        actions = HomeActions(
          onQueryChange = { query = it; events += "query:$it" },
          onSubmit = { events += "submit:$it" },
          onSuggestion = { events += "suggestion:${it.title}" },
          onSelectEngine = { events += "engine:${it.name}" },
        ),
        drawerState = drawerState,
      )
    }
  }

  private fun show() {
    composeRule.setContent { Host() }
    touchMode()
  }

  private fun touchMode() = composeRule.activity.enterTouchMode()

  /** The editable search field; while search is open it is the one of the open search. */
  private fun field() = composeRule.searchField()

  private fun open() {
    field().performClick()
    composeRule.waitForIdle()
  }

  private fun isOpen() = composeRule.onAllNodesWithTag("home-suggestions").fetchSemanticsNodes().isNotEmpty()

  @Test
  fun aTapOnTheFieldOpensSearchWithTheEnginesAndSuggestions() {
    show()
    assertThat(isOpen()).isFalse()
    open()
    composeRule.onNodeWithTag("home-suggestions").assertIsDisplayed()
    HomeSearchSamples.engines.forEach { composeRule.onNodeWithText(it.name).assertIsDisplayed() }
  }

  @Test
  fun typingPassesTheQueryUpAndTheFieldShowsIt() {
    show()
    open()
    field().performTextInput("kotlin")
    composeRule.waitForIdle()
    assertThat(events).contains("query:kotlin")
    field().assert(hasText("kotlin"))
  }

  @Test
  fun aTapOnAnEngineChipSelectsIt() {
    show()
    open()
    composeRule.onNodeWithText(SearchProviders.BING).performClick()
    assertThat(events).containsExactly("engine:${SearchProviders.BING}")
  }

  @Test
  fun aTapOnASuggestionOpensItAndClosesSearch() {
    show()
    open()
    composeRule.onNodeWithText("kotlin tutorial").performClick()
    composeRule.waitForIdle()
    assertThat(events).containsExactly("suggestion:kotlin tutorial")
    assertThat(isOpen()).isFalse()
  }

  @Test
  fun theKeyboardSearchActionSubmitsAndClosesSearch() {
    show()
    open()
    field().performTextInput("kotlin")
    field().performImeAction()
    composeRule.waitForIdle()
    assertThat(events).contains("submit:kotlin")
    assertThat(isOpen()).isFalse()
  }

  @Test
  fun theBackIconAndSystemBackCloseSearchAndHomeStays() {
    show()
    open()
    // While search is open the field shows in the closed bar and in the open search; tap the open one.
    val back = composeRule.onAllNodesWithContentDescription(composeRule.activity.getString(R.string.back))
    back[back.fetchSemanticsNodes().size - 1].performClick()
    composeRule.waitForIdle()
    assertThat(isOpen()).isFalse()

    open()
    assertThat(isOpen()).isTrue()
    Espresso.pressBack()
    composeRule.waitForIdle()
    assertThat(isOpen()).isFalse()
    assertThat(composeRule.activity.isFinishing).isFalse()
    composeRule.onNodeWithTag("home-feed").assertIsDisplayed()
  }

  @Test
  fun theMenuButtonOfTheClosedBarOpensTheDrawer() {
    show()
    composeRule.onNodeWithTag("home-menu").performClick()
    composeRule.waitForIdle()
    assertThat(drawerState.isOpen).isTrue()
  }

  @Test
  fun openSearchSurvivesARecreation() {
    val restoration = StateRestorationTester(composeRule)
    restoration.setContent { Host() }
    touchMode()
    open()
    assertThat(isOpen()).isTrue()
    restoration.emulateSavedInstanceStateRestore()
    composeRule.waitForIdle()
    assertThat(isOpen()).isTrue()
  }

  /** The bar alone, around a search state with the home's timing held here. */
  private fun showBarOnly(): SearchBarState {
    lateinit var state: SearchBarState
    composeRule.setContent {
      state = rememberSearchBarState(
        animationSpecForExpand = SearchOpenSpec,
        animationSpecForCollapse = SearchCloseSpec,
      )
      ChromerTheme {
        HomeSearchBar(
          query = "",
          onQueryChange = {},
          searchBarState = state,
          suggestions = HomeSearchSamples.kotlin(),
          searchEngines = HomeSearchSamples.engines,
          selectedEngine = SearchProviders.GOOGLE_SEARCH_PROVIDER,
          onSelectEngine = {},
          onSubmit = {},
          onSuggestion = {},
          onMenu = {},
          onVoiceUnavailable = {},
        )
      }
    }
    touchMode()
    composeRule.waitForIdle()
    return state
  }

  private fun progressAt(state: SearchBarState, millis: Long, from: Long): Float {
    composeRule.mainClock.advanceTimeBy(millis - (composeRule.mainClock.currentTime - from))
    return state.progress
  }

  @Test
  fun searchOpensOver600MsAfter100MsAndClosesOver350MsAfter100Ms() {
    val state = showBarOnly()
    composeRule.mainClock.autoAdvance = false
    val tap = composeRule.mainClock.currentTime
    field().performClick()
    assertThat(progressAt(state, 100, tap)).isWithin(0.01f).of(0f)
    val decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    // An animation starts on the frame after its trigger, so each point may sit one frame (16 ms) late.
    assertThat(progressAt(state, 400, tap)).isIn(Range.closed(decelerate.transform(284f / 600f) - 0.01f, decelerate.transform(300f / 600f) + 0.01f))
    assertThat(progressAt(state, 700, tap)).isWithin(0.01f).of(1f)
    composeRule.mainClock.advanceTimeBy(100)

    val close = composeRule.mainClock.currentTime
    val back = composeRule.onAllNodesWithContentDescription(composeRule.activity.getString(R.string.back))
    back[back.fetchSemanticsNodes().size - 1].performClick()
    val exit = CubicBezierEasing(0f, 1f, 0f, 1f)
    assertThat(progressAt(state, 275, close)).isIn(Range.closed(1f - exit.transform(175f / 350f) - 0.01f, 1f - exit.transform(159f / 350f) + 0.01f))
    assertThat(progressAt(state, 450, close)).isWithin(0.01f).of(0f)
  }

  @Config(qualifiers = "w841dp-h701dp-xxhdpi")
  @Test
  fun onAFoldTheOpenSearchStartsAtTheBarAndSettlesInTheCentredColumn() {
    show()
    val density = composeRule.activity.resources.displayMetrics.density
    val barLeft = field().fetchSemanticsNode().boundsInWindow.left / density
    composeRule.mainClock.autoAdvance = false
    val tap = composeRule.mainClock.currentTime
    field().performClick()
    composeRule.mainClock.advanceTimeBy(116 - (composeRule.mainClock.currentTime - tap))
    val openLeft = field().fetchSemanticsNode().boundsInWindow.left / density
    assertThat(openLeft).isWithin(1f).of(barLeft)

    composeRule.mainClock.autoAdvance = true
    composeRule.waitForIdle()
    val settled = field().fetchSemanticsNode().boundsInWindow
    assertThat(settled.width / density).isWithin(1f).of(680f)
    assertThat(settled.left / density).isWithin(1f).of((841f - 680f) / 2f)
  }

  /** The open search is a window of its own; device checks find its field by the same id. */
  @Test
  fun theOpenSearchKeepsItsTestTagsAsResourceIds() {
    show()
    open()
    var node: SemanticsNode? = composeRule.searchField().fetchSemanticsNode()
    var exposed = false
    while (node != null && !exposed) {
      exposed = node.config.getOrElseNullable(SemanticsPropertiesAndroid.TestTagsAsResourceId) { null } == true
      node = node.parent
    }
    assertThat(exposed).isTrue()
  }
}
