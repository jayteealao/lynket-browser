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

package arun.com.chromer.ui.webview

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.browsing.menu.ActionButton
import arun.com.chromer.browsing.menu.BrowsingScreen
import arun.com.chromer.browsing.menu.browsingBottomBarItems
import arun.com.chromer.browsing.menu.browsingMenuItems
import arun.com.chromer.bubbles.BubblePageCount
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The web view's bars: title, address, progress, the actions, the bottom bar and hide on scroll. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WebViewScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }
  private val events = mutableListOf<String>()

  private fun show(
    page: WebViewPage,
    bottomBar: Boolean = true,
    bars: WebViewBarsState = WebViewBarsState(),
    fontScale: Float = 1f,
  ) {
    composeRule.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
        ChromerTheme {
          WebViewScreen(
            page = page,
            toolbarColour = Color(0xFF3F51B5),
            menuItems = browsingMenuItems(BrowsingScreen.WebView, bottomBar, ActionButton("Share", iconRes = R.drawable.ic_share_24dp), null, text),
            bottomBarItems = browsingBottomBarItems(BrowsingScreen.WebView, bottomBar, text),
            bars = bars,
            onClose = { events += "close" },
            onMenuItem = { events += context.resources.getResourceEntryName(it.id) },
          ) { modifier -> Box(modifier.background(Color.White).testTag("page")) }
        }
      }
    }
  }

  private val count get() = composeRule.onNodeWithTag("webview-count", useUnmergedTree = true)
  private val address get() = composeRule.onNodeWithTag("webview-address", useUnmergedTree = true)

  private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.single()
  }

  @Test
  fun titleAddressAndProgressShow() {
    show(WebViewPage("Fixture", "http://localhost:8000/article.html", progress = 40))
    composeRule.onNodeWithText("Fixture").assertIsDisplayed()
    composeRule.onNodeWithText("http://localhost:8000/article.html").assertIsDisplayed()
    composeRule.onNodeWithTag("webview-progress").assertIsDisplayed()
    composeRule.onNodeWithTag("page").assertIsDisplayed()
  }

  @Test
  fun aLoadedPageHasNoProgressBar() {
    show(WebViewPage("Fixture", "http://localhost:8000/article.html", progress = 100))
    composeRule.onNodeWithTag("webview-progress").assertDoesNotExist()
  }

  @Test
  fun closeActionsAndTheFiveBottomBarActions() {
    show(WebViewPage("Fixture", "http://localhost:8000/a.html", 100))
    composeRule.onNodeWithTag("webview-close").performClick()
    composeRule.onNodeWithTag("menu-menu_action_button").performClick()
    listOf("bottom_bar_open_in_new_tab", "bottom_bar_share", "bottom_bar_tabs", "bottom_bar_minimize_tab", "bottom_bar_article_view")
      .forEach { composeRule.onNodeWithTag("menu-$it").performClick() }
    assertThat(events).containsExactly(
      "close", "menu_action_button", "bottom_bar_open_in_new_tab", "bottom_bar_share", "bottom_bar_tabs",
      "bottom_bar_minimize_tab", "bottom_bar_article_view",
    ).inOrder()
  }

  @Test
  fun withTheBottomBarOffTabsAndArticleAreInTheOverflow() {
    show(WebViewPage("Fixture", "http://localhost:8000/a.html", 100), bottomBar = false)
    composeRule.onNodeWithTag("browsing-bottom-bar").assertDoesNotExist()
    composeRule.onNodeWithTag("browsing-overflow").performClick()
    composeRule.onNodeWithTag("menu-tabs").assertIsDisplayed()
    composeRule.onNodeWithTag("menu-bottom_bar_article_view").performClick()
    assertThat(events).containsExactly("bottom_bar_article_view")
  }

  @Test
  fun theCountFollowsTheAddressAfterASeparator() {
    show(WebViewPage("Fixture", "http://localhost:8000/a.html", 100, BubblePageCount(3, 7)))

    count.assertTextEquals("· 3 of 7")
    assertThat(count.fetchSemanticsNode().boundsInRoot.left).isAtLeast(address.fetchSemanticsNode().boundsInRoot.right)
  }

  @Test
  fun withNoAddressTheCountStandsAlone() {
    show(WebViewPage("Fixture", "", 40, BubblePageCount(3, 7)))

    count.assertTextEquals("3 of 7")
    address.assertDoesNotExist()
  }

  @Test
  fun withAnAddressThatRepeatsTheTitleTheCountStandsAlone() {
    show(WebViewPage("http://localhost:8000/a.html", "http://localhost:8000/a.html", 40, BubblePageCount(3, 7)))

    count.assertTextEquals("3 of 7")
    address.assertDoesNotExist()
  }

  @Test
  fun noCountDrawsNoCountAndLeavesTheAddress() {
    show(WebViewPage("Fixture", "http://localhost:8000/a.html", 100))

    count.assertDoesNotExist()
    address.assertTextEquals("http://localhost:8000/a.html")
    composeRule.onNodeWithText("·", substring = true).assertDoesNotExist()
  }

  @Test
  fun theCountIsSpokenAsAPage() {
    show(WebViewPage("Fixture", "http://localhost:8000/a.html", 100, BubblePageCount(3, 7)))

    count.assertContentDescriptionEquals("Page 3 of 7")
  }

  // Real text measurement: the legacy graphics mode does not break lines.
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun atDoubleTextSizeALongAddressGivesWayAndTheCountStaysWhole() {
    val long = "https://subdomain.of.a.very.long.example.co.uk/2026/10/31/" + "a-very-long-path/".repeat(8)
    show(WebViewPage("Fixture", long, 100, BubblePageCount(12, 12)), fontScale = 2f)

    assertThat(count.textLayout().hasVisualOverflow).isFalse()
    assertThat(address.textLayout().hasVisualOverflow).isTrue()
    count.assertIsDisplayed()
  }

  @Test
  fun theCountHidesWithTheTopBar() {
    val bars = WebViewBarsState()
    show(WebViewPage("Fixture", "http://localhost:8000/a.html", 100, BubblePageCount(3, 7)), bars = bars)
    composeRule.waitForIdle()
    count.assertIsDisplayed()

    composeRule.runOnIdle { bars.onPageScroll(2_000, 0) }
    composeRule.runOnIdle { assertThat(bars.top.collapsedFraction).isEqualTo(1f) }

    count.assertIsNotDisplayed()
  }

  @Test
  @Config(qualifiers = "fa")
  fun theNumbersTakeTheDigitsOfTheLocale() {
    show(WebViewPage("Fixture", "", 100, BubblePageCount(3, 7)))

    val text = count.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
    assertThat(text).contains("۳")
    assertThat(text).contains("۷")
    assertThat(text.none { it in '0'..'9' }).isTrue()
    val spoken = count.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
    assertThat(spoken.none { it in '0'..'9' }).isTrue()
  }

  @Test
  fun barsHideWhileThePageScrollsDownAndReturnAtTheTop() {
    val bars = WebViewBarsState()
    show(WebViewPage("Fixture", "http://localhost:8000/a.html", 100), bars = bars)
    composeRule.waitForIdle()
    composeRule.runOnIdle { bars.onPageScroll(2_000, 0) }
    composeRule.runOnIdle {
      assertThat(bars.top.collapsedFraction).isEqualTo(1f)
      assertThat(bars.bottom.fraction).isEqualTo(1f)
      bars.onPageScroll(1_900, 2_000)
      assertThat(bars.top.collapsedFraction).isLessThan(1f)
      bars.onPageScroll(0, 1_900)
      assertThat(bars.top.collapsedFraction).isWithin(0.001f).of(0f)
      assertThat(bars.bottom.fraction).isWithin(0.001f).of(0f)
    }
  }
}
