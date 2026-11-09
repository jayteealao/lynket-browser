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
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.browsing.menu.ActionButton
import arun.com.chromer.browsing.menu.BrowsingScreen
import arun.com.chromer.browsing.menu.browsingBottomBarItems
import arun.com.chromer.browsing.menu.browsingMenuItems
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A page that did not load: the cause and Retry in place of the page, under the same top bar. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class WebPageErrorTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }
  private var retries = 0

  private fun show(
    page: WebViewPage,
    bars: WebViewBarsState = WebViewBarsState(),
    fontScale: Float = 1f,
    height: Dp? = null,
  ): MutableState<WebViewPage> {
    val state = mutableStateOf(page)
    composeRule.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
        ChromerTheme {
          Box(if (height != null) Modifier.height(height) else Modifier) {
            WebViewScreen(
              page = state.value,
              toolbarColour = Color(0xFF3F51B5),
              menuItems = browsingMenuItems(BrowsingScreen.WebView, true, ActionButton("Share", iconRes = R.drawable.ic_share_24dp), null, text),
              bottomBarItems = browsingBottomBarItems(BrowsingScreen.WebView, true, text),
              bars = bars,
              onClose = {},
              onMenuItem = {},
              onRetry = { retries++ },
            ) { modifier -> Box(modifier.background(Color.White).testTag("page")) }
          }
        }
      }
    }
    return state
  }

  private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.single()
  }

  private fun assertCause(cause: LoadFailure, title: String, body: String) {
    show(failedPage(cause))
    composeRule.onNodeWithText(title).assertIsDisplayed()
    composeRule.onNodeWithText(body).assertIsDisplayed()
    composeRule.onNodeWithText("Retry").assertIsDisplayed().assertHasClickAction()
    // The body names the host; the full address shows only in the top bar.
    composeRule.onAllNodes(hasText(FAILED_ADDRESS, substring = true), useUnmergedTree = true).assertCountEquals(1)
  }

  @Test
  fun offlineShowsItsCauseAndRetry() =
    assertCause(LoadFailure.Offline, "You are offline", "Check your connection, then try again.")

  @Test
  fun aSiteNotReachedNamesTheSite() =
    assertCause(LoadFailure.CannotReach, "This site cannot be reached", "lemire.me did not answer. The address can be wrong, or the site can be down.")

  @Test
  fun aCertificateErrorNamesTheSite() =
    assertCause(LoadFailure.Certificate, "This connection is not secure", "lemire.me could not prove that it is the site it claims to be, so the page did not load, to keep you safe.")

  @Test
  fun anyOtherFailureNamesTheSite() =
    assertCause(LoadFailure.General, "This page did not load", "Something went wrong while loading lemire.me. Try again in a moment.")

  @Test
  fun theTopBarKeepsCloseTheTitleTheAddressAndTheCount() {
    show(failedPage(LoadFailure.Offline))
    listOf("webview-close", "webview-title", "webview-address").forEach {
      composeRule.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed()
    }
    val count = composeRule.onNodeWithTag("webview-count", useUnmergedTree = true).assertIsDisplayed()
    assertThat(count.textLayout().layoutInput.text.text).endsWith("3 of 7")
  }

  @Test
  fun theErrorReplacesThePageTheBottomBarAndTheProgressBar() {
    show(failedPage(LoadFailure.Offline, progress = 40))
    composeRule.onNodeWithTag("webview-error").assertIsDisplayed()
    composeRule.onNodeWithTag("browsing-bottom-bar").assertDoesNotExist()
    composeRule.onNodeWithTag("webview-progress").assertDoesNotExist()
    composeRule.onNodeWithTag("page").assertDoesNotExist()
  }

  @Test
  fun aLoadingOrLoadedPageShowsThePageAndTheBottomBar() {
    val page = show(WebViewPage("Fixture", "http://localhost:8000/a.html", 40, load = PageLoad.Loading))
    composeRule.onNodeWithTag("page").assertIsDisplayed()
    composeRule.onNodeWithTag("browsing-bottom-bar").assertIsDisplayed()
    composeRule.onNodeWithTag("webview-error").assertDoesNotExist()
    page.value = WebViewPage("Fixture", "http://localhost:8000/a.html", 100, load = PageLoad.Loaded)
    composeRule.onNodeWithTag("page").assertIsDisplayed()
    composeRule.onNodeWithTag("browsing-bottom-bar").assertIsDisplayed()
    composeRule.onNodeWithTag("webview-error").assertDoesNotExist()
  }

  @Test
  fun retryCallsBackOnce() {
    show(failedPage(LoadFailure.CannotReach))
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.runOnIdle { assertThat(retries).isEqualTo(1) }
  }

  @Test
  fun aCertificateErrorOffersNoWayPastIt() {
    show(failedPage(LoadFailure.Certificate))
    listOf("Proceed", "Continue", "Open anyway").forEach {
      composeRule.onAllNodes(hasText(it, substring = true, ignoreCase = true), useUnmergedTree = true).assertCountEquals(0)
    }
    composeRule.onNodeWithText("Retry").assertIsDisplayed()
  }

  // Real text measuring, so the line breaks are the ones a person sees.
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Test
  fun theWorstPageWrapsTheHostInTheBodyAndKeepsTheCountWhole() {
    show(worstFailedPage(), fontScale = 2f)
    val body = composeRule.onNodeWithText("$WORST_HOST did not answer. The address can be wrong, or the site can be down.").textLayout()
    assertThat(body.lineCount).isGreaterThan(1)
    assertThat(body.hasVisualOverflow).isFalse()
    val count = composeRule.onNodeWithTag("webview-count", useUnmergedTree = true).textLayout()
    assertThat(count.layoutInput.text.text).endsWith("12 of 12")
    assertThat(count.hasVisualOverflow).isFalse()
    assertThat(count.lineCount).isEqualTo(1)
    val address = composeRule.onNodeWithTag("webview-address", useUnmergedTree = true).textLayout()
    assertThat(address.isLineEllipsized(0)).isTrue()
  }

  @Test
  fun aShortPageAreaDropsThePictureAndKeepsTheTitleAndRetry() {
    show(failedPage(LoadFailure.Offline, count = null), height = 300.dp)
    composeRule.onNodeWithTag("error-state-blob", useUnmergedTree = true).assertDoesNotExist()
    composeRule.onNodeWithText("You are offline").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").assertIsDisplayed()
  }

  @Test
  fun atTwiceTheTextSizeAPageAreaThatKeptThePictureDropsIt() {
    show(failedPage(LoadFailure.Offline, count = null), fontScale = 2f, height = 640.dp)
    composeRule.onNodeWithTag("error-state-blob", useUnmergedTree = true).assertDoesNotExist()
    composeRule.onNodeWithText("Retry").assertExists()
  }

  @Test
  fun aTallPageAreaKeepsThePicture() {
    show(failedPage(LoadFailure.Offline, count = null), height = 640.dp)
    composeRule.onNodeWithTag("error-state-blob", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun aCollapsedTopBarShowsAgainWhenThePageFails() {
    val bars = WebViewBarsState()
    val page = show(WebViewPage("lemire.me", FAILED_ADDRESS, 40), bars = bars)
    composeRule.runOnIdle { bars.onPageScroll(800, 0) }
    composeRule.runOnIdle { assertThat(bars.top.heightOffset).isLessThan(0f) }
    page.value = failedPage(LoadFailure.Offline)
    composeRule.runOnIdle { assertThat(bars.top.heightOffset).isEqualTo(0f) }
  }

  @Test
  fun aRetryingPageKeepsTheCauseWithRetryDisabled() {
    show(retryingPage())
    composeRule.onNodeWithText("You are offline").assertIsDisplayed()
    composeRule.onNodeWithText("Check your connection, then try again.").assertIsDisplayed()
    composeRule.onNodeWithText("Retrying…").assertIsDisplayed().assertIsNotEnabled()
  }

  @Test
  fun aRetryingPageShowsTheProgressBarBeforeAndDuringItsProgress() {
    val page = show(retryingPage(progress = 40))
    composeRule.onNodeWithTag("webview-progress").assertIsDisplayed()
    page.value = retryingPage(progress = 0)
    composeRule.onNodeWithTag("webview-progress").assertIsDisplayed()
    page.value = retryingPage(progress = 100)
    composeRule.onNodeWithTag("webview-progress").assertIsDisplayed()
  }

  @Test
  fun aTapOnTheDisabledRetryDoesNothing() {
    show(retryingPage())
    composeRule.onNodeWithText("Retrying…").performClick()
    composeRule.runOnIdle { assertThat(retries).isEqualTo(0) }
  }

  @Test
  fun aRetryingPageKeepsTheTopBarAndShowsNoPageOrBottomBar() {
    show(retryingPage())
    composeRule.onNodeWithTag("page").assertDoesNotExist()
    composeRule.onNodeWithTag("browsing-bottom-bar").assertDoesNotExist()
    composeRule.onNodeWithTag("webview-close", useUnmergedTree = true).assertIsDisplayed()
    composeRule.onNodeWithTag("webview-count", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun aFailedPageThatIsNotRetryingHasRetryEnabledAndNoProgressBar() {
    show(failedPage(LoadFailure.Offline, progress = 40))
    composeRule.onNodeWithText("Retry").assertIsEnabled()
    composeRule.onNodeWithTag("webview-progress").assertDoesNotExist()
  }

  @Test
  fun aPullOnTheErrorPageRetriesOnce() {
    show(failedPage(LoadFailure.Offline))
    composeRule.onNodeWithTag("webview-error").performTouchInput { swipeDown() }
    composeRule.runOnIdle { assertThat(retries).isEqualTo(1) }
  }

  @Test
  fun aPullDuringARetryDoesNothing() {
    show(retryingPage())
    composeRule.onNodeWithTag("webview-error").performTouchInput { swipeDown() }
    composeRule.runOnIdle { assertThat(retries).isEqualTo(0) }
  }

  @Test
  fun aRetryThatFailsAgainShowsTheNewCauseWithRetryEnabled() {
    val page = show(retryingPage())
    page.value = failedPage(LoadFailure.CannotReach)
    composeRule.onNodeWithText("This site cannot be reached").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").assertIsEnabled()
    composeRule.onNodeWithTag("webview-progress").assertDoesNotExist()
  }
}

/** Each reason a page did not load has its own icon and title, so a new one cannot fall through. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WebPageErrorContentTest {

  @Test
  fun everyCauseHasADistinctIconAndTitle() {
    val contents = LoadFailure.entries.map(::errorContentOf)
    assertThat(contents.map { it.icon }.toSet()).hasSize(LoadFailure.entries.size)
    assertThat(contents.map { it.title }.toSet()).hasSize(LoadFailure.entries.size)
    assertThat(contents.map { it.body }.toSet()).hasSize(LoadFailure.entries.size)
  }

  @Test
  fun onlyTheOfflineBodyLeavesTheSiteOut() {
    assertThat(LoadFailure.entries.filterNot { errorContentOf(it).bodyNamesSite }).containsExactly(LoadFailure.Offline)
  }

  @Test
  fun theSiteIsTheHostOfTheAddress() {
    assertThat(siteOf("https://lemire.me/blog")).isEqualTo("lemire.me")
    assertThat(siteOf("http://$WORST_HOST/some/deep/path")).isEqualTo(WORST_HOST)
  }

  @Test
  fun anAddressWithNoHostIsItsOwnSite() {
    assertThat(siteOf("not a url")).isEqualTo("not a url")
    assertThat(siteOf("about:blank")).isEqualTo("about:blank")
  }
}
