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
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubblePageCount
import arun.com.chromer.bubbles.BubblePlace
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.bubbleNotificationId
import arun.com.chromer.bubbles.system.EXTRA_BUBBLE_NOTIFICATION_ID
import arun.com.chromer.data.preferences.BrowsingMode.NativeBubbles
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.webview.WebViewPage
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * A page opened by its bubble reads its place and the total from the open bubbles, from the first
 * draw and as bubbles open and close. Any other page shows no count.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class EmbeddableWebViewCountTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createEmptyComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  @Inject
  lateinit var registry: BubbleRegistry

  private val application: Application get() = RuntimeEnvironment.getApplication()
  private val links = (1..7).map { "https://example.com/$it" }
  private val third = links[2]

  private val count get() = composeRule.onNodeWithTag("webview-count", useUnmergedTree = true)
  private val title get() = composeRule.onNodeWithTag("webview-title", useUnmergedTree = true)

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  private fun openAll() = links.forEach { registry.add(NativeBubbles, it, incognito = false) }

  private fun <T : WebViewActivity> controller(cls: Class<T>, link: String, bubble: Int?, incognito: Boolean = false) =
    Robolectric.buildActivity(
      cls,
      Intent(application, cls).setData(Uri.parse(link)).putExtra(Constants.EXTRA_KEY_INCOGNITO, incognito).apply {
        bubble?.let { putExtra(EXTRA_BUBBLE_NOTIFICATION_ID, it) }
      },
    )

  /** Opens a page as its bubble does: the intent names the bubble of [link]. */
  private fun openPage(link: String, incognito: Boolean = false) =
    controller(EmbeddableWebViewActivity::class.java, link, bubbleNotificationId(link, incognito), incognito).setup()

  /** The activity's page state, read directly because the screen may not have composed yet. */
  private fun WebViewActivity.pageState(): WebViewPage {
    val state = WebViewActivity::class.java.getDeclaredField("page\$delegate").apply { isAccessible = true }.get(this)
    return (state as MutableState<*>).value as WebViewPage
  }

  @Test
  fun theThirdOfSevenReadsItsPlaceAtTheFirstDrawBeforeAnyProgress() {
    openAll()
    openPage(third)

    count.assertTextContains("3 of 7", substring = true)
    composeRule.onNodeWithTag("webview-progress").assertExists()
  }

  @Test
  fun theCountIsSetBeforeTheScreenComposes() {
    openAll()
    val activity = controller(EmbeddableWebViewActivity::class.java, third, bubbleNotificationId(third)).create().get()

    assertThat(activity.pageState().count).isEqualTo(BubblePageCount(3, 7))
  }

  @Test
  fun bubblesInTheOverflowStayInTheTotal() {
    openAll()
    registry.reconcile(NativeBubbles) {
      links.associate { bubbleNotificationId(it) to if (it in links.take(2)) BubblePlace.OVERFLOW else BubblePlace.STACK }
    }
    openPage(third)

    count.assertTextContains("3 of 7", substring = true)
  }

  @Test
  fun aCloseChangesTheCountInPlaceAndTheLastCloseTakesItAway() {
    openAll()
    openPage(third)
    val node = count.fetchSemanticsNode().id

    registry.remove(BubbleKey.of(NativeBubbles, links[6], incognito = false))
    composeRule.waitForIdle()

    count.assertTextContains("3 of 6", substring = true)
    assertThat(count.fetchSemanticsNode().id).isEqualTo(node)

    links.filterNot { it == third || it == links[6] }
      .forEach { registry.remove(BubbleKey.of(NativeBubbles, it, incognito = false)) }
    composeRule.waitForIdle()

    count.assertDoesNotExist()
    title.assertTextEquals(third)
  }

  @Test
  fun aBubbleThatOpensLaterRaisesTheTotalInPlace() {
    openAll()
    openPage(third)

    registry.add(NativeBubbles, "https://example.com/8", incognito = false)
    composeRule.waitForIdle()

    count.assertTextContains("3 of 8", substring = true)
  }

  @Test
  fun anIncognitoBubbleReadsItsCount() {
    openAll()
    val secret = "https://example.com/secret"
    registry.add(NativeBubbles, secret, incognito = true)
    openPage(secret, incognito = true)

    count.assertTextContains("8 of 8", substring = true)
  }

  @Test
  fun aPageTheShortcutOpensShowsNoCountThoughItsLinkIsListed() {
    openAll()
    controller(EmbeddableWebViewActivity::class.java, third, bubble = null).setup()
    composeRule.waitForIdle()

    title.assertExists()
    count.assertDoesNotExist()
  }

  @Test
  fun aPageInATabShowsNoCount() {
    openAll()
    controller(WebViewActivity::class.java, third, bubbleNotificationId(third)).setup()
    composeRule.waitForIdle()

    title.assertExists()
    count.assertDoesNotExist()
  }

  @Test
  fun aBubbleTheListDoesNotHoldShowsNoCount() {
    openAll()
    openPage("https://example.com/not-listed")
    composeRule.waitForIdle()

    title.assertExists()
    count.assertDoesNotExist()
  }

  @Test
  fun aRefusedLinkFinishesWithNoCount() {
    openAll()
    val activity = controller(EmbeddableWebViewActivity::class.java, "javascript:alert(1)", bubbleNotificationId(third)).setup().get()

    assertThat(activity.isFinishing).isTrue()
    assertThat(activity.pageState().count).isNull()
  }
}
