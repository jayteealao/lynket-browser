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

package arun.com.chromer.browsing.webview

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.browsing.menu.HIDDEN_IN_A_BUBBLE
import arun.com.chromer.bubbles.BubbleLoadData
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.bubbles.pageCount
import arun.com.chromer.bubbles.system.BubbleNotificationManager
import arun.com.chromer.bubbles.system.EXTRA_BUBBLE_NOTIFICATION_ID
import arun.com.chromer.bubbles.system.NativeFloatingBubble
import arun.com.chromer.bubbles.system.bubbleIdsOf
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Open in full window, the first item of the menu in a bubble, opens the page on screen in a full
 * window of its own with its colour and its incognito state, and closes the bubble, so the other
 * bubbles' count changes. A page that is not in a bubble has no such item.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class OpenInFullWindowTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createEmptyComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  @Inject
  lateinit var manager: BubbleNotificationManager

  @Inject
  lateinit var registry: BubbleRegistry

  @Inject
  lateinit var nativeFloatingBubble: NativeFloatingBubble

  private val application: Application get() = RuntimeEnvironment.getApplication()
  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val first = "https://example.com/one"
  private val second = "https://example.com/two"

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  /**
   * A return runs on a real thread, and the notifications of Robolectric outlive a test, so a return
   * still running would post into the next test. An empty list stops it, and a last call waits for it.
   */
  @After
  fun waitForTheReturns() {
    registry.bubbles.value.forEach { registry.remove(it.key) }
    runBlocking { withTimeout(WAIT_FOR_RETURNS_MS) { nativeFloatingBubble.returnNewestOverflowBubble().join() } }
  }

  private fun post(url: String, incognito: Boolean = false) {
    val website = Website("Title", url, null, null, null, null, false, 0, 0)
    val data = BubbleLoadData(website, false, false, incognito)
    runBlocking { manager.showBubbles(data) }
    registry.add(BrowsingMode.NativeBubbles, url, incognito, data)
  }

  private fun notificationOf(url: String): Notification? =
    shadowOf(notificationManager).allNotifications.singleOrNull { it.shortcutId in bubbleIdsOf(url) }

  /** The page of [url] as its bubble opens it, or with no bubble as the conversation shortcut does. */
  private fun page(url: String, incognito: Boolean = false, inBubble: Boolean = true): EmbeddableWebViewActivity {
    val intent = Intent(application, EmbeddableWebViewActivity::class.java)
      .setData(Uri.parse(url))
      .putExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR, BLUE)
      .putExtra(Constants.EXTRA_KEY_INCOGNITO, incognito)
    if (inBubble) intent.putExtra(EXTRA_BUBBLE_NOTIFICATION_ID, bubbleNotificationId(url, incognito))
    return Robolectric.buildActivity(EmbeddableWebViewActivity::class.java, intent).setup().get()
  }

  private fun tapOpenInFullWindow() {
    composeRule.onNodeWithTag("browsing-overflow").performClick()
    composeRule.onNodeWithTag("menu-menu_open_in_full_window").assertIsDisplayed().performClick()
    composeRule.waitForIdle()
  }

  private fun started(): Intent? = shadowOf(application).nextStartedActivity

  private fun WebViewActivity.webView(): WebView =
    WebViewActivity::class.java.getDeclaredField("webView").apply { isAccessible = true }.get(this) as WebView

  private fun WebViewActivity.setToolbarColour(colour: Int) {
    WebViewActivity::class.java.getDeclaredMethod("onToolbarColorSet", Int::class.javaPrimitiveType)
      .apply { isAccessible = true }
      .invoke(this, colour)
  }

  @Test
  fun theSamePageOpensInAFullWindowOfItsOwnAndTheBubbleCloses() {
    post(first)
    post(second)
    val page = page(second)

    tapOpenInFullWindow()

    val intent = started()!!
    assertThat(intent.component?.className).isEqualTo(WebViewActivity::class.java.name)
    assertThat(intent.dataString).isEqualTo(second)
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    assertThat(intent.flags and flags).isEqualTo(flags)
    assertThat(intent.getBooleanExtra(Constants.EXTRA_KEY_INCOGNITO, true)).isFalse()
    assertThat(intent.getIntExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR, 0)).isEqualTo(BLUE)
    assertThat(intent.hasExtra(EXTRA_BUBBLE_NOTIFICATION_ID)).isFalse()

    assertThat(page.isFinishing).isTrue()
    assertThat(notificationOf(second)).isNull()
    assertThat(notificationOf(first)).isNotNull()
    val left = registry.list(BrowsingMode.NativeBubbles)
    assertThat(left.map { it.url }).containsExactly(first)
    assertThat(left.pageCount(BubbleKey.of(BrowsingMode.NativeBubbles, first, incognito = false))).isNull()
  }

  @Test
  fun anIncognitoBubbleOpensAnIncognitoFullWindow() {
    post(first, incognito = true)
    page(first, incognito = true)

    tapOpenInFullWindow()

    assertThat(started()!!.getBooleanExtra(Constants.EXTRA_KEY_INCOGNITO, false)).isTrue()
  }

  @Test
  fun theFullWindowTakesTheColourTheBarShowsNow() {
    post(first)
    val page = page(first)
    // The page sets the colour of its intent on the main thread after it starts; a colour set
    // before that would be painted over.
    composeRule.waitForIdle()
    page.setToolbarColour(GREEN)

    tapOpenInFullWindow()

    assertThat(started()!!.getIntExtra(Constants.EXTRA_KEY_TOOLBAR_COLOR, 0)).isEqualTo(GREEN)
  }

  @Test
  fun theFullWindowTakesThePageOnScreenNotTheOneTheBubbleOpened() {
    post(first)
    val page = page(first)
    page.webView().loadUrl(second)

    tapOpenInFullWindow()

    assertThat(started()!!.dataString).isEqualTo(second)
  }

  @Test
  fun aPageFromTheConversationShortcutHasNoItemAndStaysOpen() {
    post(first)
    val page = page(first, inBubble = false)

    composeRule.onNodeWithTag("browsing-overflow").performClick()
    composeRule.onNodeWithTag("menu-menu_copy_link").assertIsDisplayed()
    assertThat(composeRule.onAllNodesWithTag("menu-menu_open_in_full_window").fetchSemanticsNodes()).isEmpty()

    page.openInFullWindow()

    assertThat(started()).isNull()
    assertThat(page.isFinishing).isFalse()
    assertThat(notificationOf(first)).isNotNull()
  }

  @Test
  fun theWebViewProviderInAFullWindowHasNoItem() {
    val intent = Intent(application, WebViewActivity::class.java).setData(Uri.parse(first))
    Robolectric.buildActivity(WebViewActivity::class.java, intent).setup()

    composeRule.onNodeWithTag("browsing-overflow").performClick()
    composeRule.onNodeWithTag("menu-menu_copy_link").assertIsDisplayed()
    assertThat(composeRule.onAllNodesWithTag("menu-menu_open_in_full_window").fetchSemanticsNodes()).isEmpty()
  }

  private fun tagsUnder(parent: String): List<String> =
    composeRule.onAllNodes(hasAnyAncestor(hasTestTag(parent)))
      .fetchSemanticsNodes()
      .filter { SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith("menu-") }
      .map { it.config[SemanticsProperties.TestTag] }

  private val hiddenTags get() = HIDDEN_IN_A_BUBBLE.map { "menu-" + application.resources.getResourceEntryName(it) }

  @Test
  fun aBubblePageShowsNoHiddenItem() {
    post(first)
    page(first)

    composeRule.onNodeWithTag("browsing-overflow").performClick()
    val menu = tagsUnder("browsing-overflow-menu")
    assertThat(menu.first()).isEqualTo("menu-menu_open_in_full_window")
    assertThat(menu).containsNoneIn(hiddenTags)
    assertThat(menu).contains("menu-menu_copy_link")
    val bar = tagsUnder("browsing-bottom-bar")
    assertThat(bar).containsNoneIn(hiddenTags)
    assertThat(bar).contains("menu-bottom_bar_share")
  }

  @Test
  fun theShortcutPageAndTheWebViewProviderShowEveryItem() {
    post(first)
    page(first, inBubble = false)
    composeRule.onNodeWithTag("browsing-overflow").performClick()
    assertThat(tagsUnder("browsing-overflow-menu")).contains("menu-menu_history")
    assertThat(tagsUnder("browsing-bottom-bar")).contains("menu-bottom_bar_minimize_tab")
  }

  @Test
  fun theWebViewProviderShowsEveryItem() {
    val intent = Intent(application, WebViewActivity::class.java).setData(Uri.parse(first))
    Robolectric.buildActivity(WebViewActivity::class.java, intent).setup()

    composeRule.onNodeWithTag("browsing-overflow").performClick()
    assertThat(tagsUnder("browsing-overflow-menu")).contains("menu-menu_history")
    assertThat(tagsUnder("browsing-bottom-bar")).contains("menu-bottom_bar_minimize_tab")
  }

  private companion object {
    const val BLUE = 0xFF1E88E5.toInt()
    const val GREEN = 0xFF2E7D32.toInt()
    const val WAIT_FOR_RETURNS_MS = 10_000L
  }
}
