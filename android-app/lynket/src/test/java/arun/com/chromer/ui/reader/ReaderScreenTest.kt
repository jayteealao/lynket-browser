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

package arun.com.chromer.ui.reader

import arun.com.chromer.browsing.article.BlockKind
import arun.com.chromer.browsing.article.ArticleBlock
import arun.com.chromer.browsing.article.articleBlocks
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.browsing.article.ReaderArticle
import arun.com.chromer.browsing.article.ReaderState
import arun.com.chromer.browsing.menu.ActionButton
import arun.com.chromer.browsing.menu.BrowsingMenuItem
import arun.com.chromer.browsing.menu.BrowsingScreen
import arun.com.chromer.browsing.menu.browsingBottomBarItems
import arun.com.chromer.browsing.menu.browsingMenuItems
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A fixed article for the reader tests and goldens (no header image, so nothing loads). */
internal fun sampleReaderArticle(): ReaderArticle {
  val blocks = articleBlocks(
    org.jsoup.Jsoup.parse(
      """
      <p>Google's next release removes orientation locks on large screens, so apps that assumed a portrait phone now have to adapt.</p>
      <blockquote>Every app is a large-screen app now, the team said at the developer summit.</blockquote>
      <h2>What changes for readers</h2>
      <p>For most Compose apps the change is small: window size classes and adaptive panes do the work, and <a href="https://developer.android.com">the guide</a> is the place to start.</p>
      <ul><li>Text stays readable at every font scale.</li><li>Controls keep a 48 dp touch target.</li></ul>
      <pre>val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass</pre>
      """.trimIndent()
    ).body().children()
  )
  return ReaderArticle(
    url = "https://www.theverge.com/2026/10/android-17",
    title = "Android 17 brings resizable apps everywhere",
    siteName = "theverge.com",
    imageUrl = "",
    keywords = listOf("android", "compose", "large screens"),
    blocks = blocks,
  )
}

internal fun readerMenu(text: (Int) -> String, bottomBar: Boolean = true) = browsingMenuItems(
  BrowsingScreen.Article, bottomBar, ActionButton(text(R.string.share), iconRes = R.drawable.ic_share_24dp), null, text,
)

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ReaderScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }

  private val events = mutableListOf<String>()

  /** The size the reader shows; a test may change it from outside, as a stored change would. */
  private var increment by mutableIntStateOf(0)

  @Composable
  private fun Reader(state: ReaderState, saved: Int = 0, bottomBar: Boolean = true) {
    ReaderScreen(
      state = state,
      menuItems = readerMenu(text, bottomBar),
      bottomBarItems = browsingBottomBarItems(BrowsingScreen.Article, bottomBar, text),
      textSizeIncrement = increment,
      savedTextSizeIncrement = saved,
      accent = Color(0xFF3F51B5),
      onClose = { events += "close" },
      onMenuItem = { item: BrowsingMenuItem -> events += context.resources.getResourceEntryName(item.id) },
      onRetry = { events += "retry" },
      onOpenInBrowser = { events += "open" },
      onImage = { events += "image:$it" },
      onKeyword = { events += "keyword:$it" },
      onTextSizeChange = {
        increment = it
        events += "size:$it"
      },
      onSaveTextSize = { events += "save:$it" },
    )
  }

  private fun show(state: ReaderState, saved: Int = 0, bottomBar: Boolean = true) {
    increment = saved
    composeRule.setContent { ChromerTheme(themeMode = ThemeMode.Light) { Reader(state, saved, bottomBar) } }
  }

  @Test
  fun blocksOfOneKindShareAContentType() {
    val one = readerBlockContentType(ArticleBlock(BlockKind.Numbered(1), "one"))
    val two = readerBlockContentType(ArticleBlock(BlockKind.Numbered(2), "two"))
    assertThat(one).isEqualTo(two)
    assertThat(readerBlockContentType(ArticleBlock(BlockKind.Heading(1), "a")))
      .isEqualTo(readerBlockContentType(ArticleBlock(BlockKind.Heading(3), "b")))
    assertThat(readerBlockContentType(ArticleBlock(BlockKind.Paragraph, "p")))
      .isNotEqualTo(readerBlockContentType(ArticleBlock(BlockKind.Heading(2), "h")))
  }

  @Test
  fun loadingShowsTheSkeletonAndItsLabel() {
    show(ReaderState.Loading)
    composeRule.onNodeWithTag("reader-loading").assertIsDisplayed()
    composeRule.onNodeWithText(context.getString(R.string.reader_loading)).assertExists()
  }

  @Test
  fun aPageWithNoArticleShowsTheErrorStateWithRetryAndOpenInBrowser() {
    show(ReaderState.Error)
    composeRule.onNodeWithTag("error-state").assertIsDisplayed()
    composeRule.onNodeWithText(context.getString(R.string.reader_error_title)).assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Open in browser").performClick()
    assertThat(events).containsExactly("retry", "open").inOrder()
  }

  @Test
  fun contentShowsTitleSiteKeywordsAndBlocks() {
    show(ReaderState.Content(sampleReaderArticle()))
    composeRule.onNodeWithTag("reader-title").assertIsDisplayed()
    composeRule.onNodeWithText("theverge.com").assertIsDisplayed()
    composeRule.onNodeWithTag("keyword-compose").performClick()
    composeRule.onNodeWithTag("article-quote").assertIsDisplayed()
    composeRule.onNodeWithTag("reader-header-image").assertDoesNotExist()
    assertThat(events).containsExactly("keyword:compose")
  }

  @Test
  fun topBarKeepsTheLegacyActionsAndTheOverflow() {
    show(ReaderState.Content(sampleReaderArticle()))
    composeRule.onNodeWithTag("reader-close").performClick()
    composeRule.onNodeWithTag("menu-menu_open_full_page").performClick()
    composeRule.onNodeWithTag("menu-menu_action_button").performClick()
    composeRule.onNodeWithTag("browsing-overflow").performClick()
    composeRule.onNodeWithTag("menu-menu_copy_link").assertIsDisplayed()
    composeRule.onNodeWithTag("menu-menu_add_to_home_screen").performClick()
    assertThat(events).containsExactly("close", "menu_open_full_page", "menu_action_button", "menu_add_to_home_screen").inOrder()
  }

  @Test
  fun bottomBarFollowsTheSetting() {
    show(ReaderState.Content(sampleReaderArticle()))
    composeRule.onNodeWithTag("menu-bottom_bar_minimize_tab").performClick()
    assertThat(events).containsExactly("bottom_bar_minimize_tab")
  }

  @Test
  fun noBottomBarWithTheSettingOff() {
    show(ReaderState.Content(sampleReaderArticle()), bottomBar = false)
    composeRule.onNodeWithTag("browsing-bottom-bar").assertDoesNotExist()
  }

  @Test
  fun textSizeCardOpensFromTheMenuAndAsksToSaveAChangedSize() {
    show(ReaderState.Content(sampleReaderArticle()), saved = 2)
    composeRule.onNodeWithTag("menu-menu_text_size").performClick()
    composeRule.onNodeWithTag("text-size-card").assertIsDisplayed()
    composeRule.onNodeWithTag("text-size-slider").assertIsDisplayed()
    composeRule.onNodeWithTag("text-size-slider").performSemanticsActionSetProgress(5f)
    composeRule.onNodeWithTag("text-size-close").performClick()
    composeRule.onNodeWithTag("save-size-dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("save-size-yes").performClick()
    assertThat(events).containsExactly("size:5", "save:5").inOrder()
  }

  private fun openTextSize(saved: Int = 0) {
    show(ReaderState.Content(sampleReaderArticle()), saved = saved)
    composeRule.onNodeWithTag("menu-menu_text_size").performClick()
  }

  private fun sliderProgress() = composeRule.onNodeWithTag("text-size-slider").fetchSemanticsNode()
    .config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo]

  @Test
  fun theSliderRunsFrom0To15InWholeStepsAndSaysTheSize() {
    openTextSize(saved = 4)
    val range = sliderProgress()
    assertThat(range.range).isEqualTo(0f..15f)
    assertThat(range.steps).isEqualTo(14)
    assertThat(range.current).isEqualTo(4f)
    composeRule.onNodeWithTag("text-size-slider").assert(
      androidx.compose.ui.test.SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
        context.getString(R.string.text_size_state, 4),
      )
    )
  }

  @Test
  fun everyOneOfThe16PositionsGivesItsSize() {
    openTextSize()
    (1..15).forEach { composeRule.onNodeWithTag("text-size-slider").performSemanticsActionSetProgress(it.toFloat()) }
    composeRule.onNodeWithTag("text-size-slider").performSemanticsActionSetProgress(0f)
    composeRule.waitForIdle()
    assertThat(events).containsExactlyElementsIn((1..15).map { "size:$it" } + "size:0").inOrder()
  }

  @Test
  fun aDragToEitherEndGivesTheLargestAndTheSmallestSize() {
    openTextSize()
    composeRule.onNodeWithTag("text-size-slider").performTouchInput { drag(from = centerX, to = width + 40.dp.toPx()) }
    composeRule.waitForIdle()
    assertThat(events.last()).isEqualTo("size:15")
    composeRule.onNodeWithTag("text-size-slider").performTouchInput { drag(from = centerX, to = -40.dp.toPx()) }
    composeRule.waitForIdle()
    assertThat(events.last()).isEqualTo("size:0")
  }

  @Test
  fun theSliderFollowsTheStoredSizeWhenItChangesFromOutside() {
    openTextSize()
    composeRule.runOnIdle { increment = 7 }
    composeRule.waitForIdle()
    assertThat(sliderProgress().current).isEqualTo(7f)
  }

  @Test
  fun anUnchangedSizeClosesWithoutAsking() {
    show(ReaderState.Content(sampleReaderArticle()), saved = 2)
    composeRule.onNodeWithTag("menu-menu_text_size").performClick()
    composeRule.onNodeWithTag("text-size-close").performClick()
    composeRule.onNodeWithTag("save-size-dialog").assertDoesNotExist()
    composeRule.onNodeWithTag("text-size-card").assertDoesNotExist()
  }
}

/**
 * A finger drag along the slider's middle line. It starts at the centre, because the close
 * button's touch area reaches a few dp over the slider's right end, and runs past the end.
 */
private fun androidx.compose.ui.test.TouchInjectionScope.drag(from: Float, to: Float) {
  down(androidx.compose.ui.geometry.Offset(from, centerY))
  repeat(10) { moveBy(androidx.compose.ui.geometry.Offset((to - from) / 10f, 0f)) }
  up()
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performSemanticsActionSetProgress(value: Float) =
  performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(value) }
