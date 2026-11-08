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

package arun.com.chromer.browsing.menu

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import arun.com.chromer.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The menu and the bottom bar of a page in a bubble follow the table of the device check that
 * tapped each item in an expanded native bubble (`bubble-menu-audit.tsv`, one row per item,
 * setting and Android version): an item that misbehaved on any version is left out in a bubble,
 * every other item stays in its place, and outside a bubble every item shows as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BubbleMenuAuditTest {

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }
  private val share = ActionButton("Share", iconRes = R.drawable.ic_share_24dp)

  private class Row(val id: String, val where: String, val setting: String, val android: String, val outcome: String)

  private val rows: List<Row> by lazy {
    val stream = javaClass.classLoader!!.getResourceAsStream("bubble-menu-audit.tsv")
    assertThat(stream).isNotNull()
    val lines = stream.bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
    val header = lines.first().split('\t')
    assertThat(header.take(9)).containsExactly(
      "item", "id", "where", "setting", "android", "device", "outcome", "reason", "evidence",
    ).inOrder()
    lines.drop(1).map { line ->
      val cells = line.split('\t')
      Row(id = cells[1], where = cells[2], setting = cells[3], android = cells[4], outcome = cells[6])
    }
  }

  private val misbehaving: Set<String> by lazy { rows.filter { it.outcome == "misbehaves" }.map { it.id }.toSet() }
  private val working: Set<String> by lazy { rows.filter { it.outcome == "works" }.map { it.id }.toSet() - misbehaving }

  private fun ids(items: List<BrowsingMenuItem>) = items.map { context.resources.getResourceEntryName(it.id) }

  private fun menu(bottomBar: Boolean, button: ActionButton?, shareWith: String?, inBubble: Boolean, screen: BrowsingScreen = BrowsingScreen.WebView) =
    ids(browsingMenuItems(screen, bottomBar, button, shareWith, text, inBubble))

  private fun bar(bottomBar: Boolean, inBubble: Boolean, screen: BrowsingScreen = BrowsingScreen.WebView) =
    ids(browsingBottomBarItems(screen, bottomBar, text, inBubble))

  /** Every web view setting the device check covered: the bottom bar on and off, a share-with app or none. */
  private val settings = listOf(true, false).flatMap { bottomBar -> listOf(null, "Messages").map { bottomBar to it } }

  @Test
  fun theTableNamesEveryItemOfTheBubbleMenuWithAnOutcome() {
    val items = listOf(
      "menu_copy_link", "menu_open_with", "menu_share", "menu_share_with", "menu_history", "menu_add_to_home_screen",
      "tabs", "bottom_bar_article_view", "menu_action_button", "bottom_bar_open_in_new_tab", "bottom_bar_share",
      "bottom_bar_tabs", "bottom_bar_minimize_tab",
    )
    val decided = rows.filter { it.outcome == "works" || it.outcome == "misbehaves" }
    items.forEach { id -> assertThat(decided.map { it.id }).contains(id) }
    // Article mode is tapped in the menu and in the bar.
    assertThat(decided.filter { it.id == "bottom_bar_article_view" }.map { it.where }.toSet()).containsExactly("menu", "bar")
    assertThat(rows.map { it.outcome }.toSet()).containsNoneOf("undecided", "")
    assertThat(rows.map { it.android }.toSet()).doesNotContain("")
  }

  @Test
  fun theHiddenItemsAreTheOnesTheTableSaysMisbehave() {
    assertThat(HIDDEN_IN_A_BUBBLE.map { context.resources.getResourceEntryName(it) }.toSet()).isEqualTo(misbehaving)
    assertThat(HIDDEN_IN_A_BUBBLE).doesNotContain(R.id.menu_open_in_full_window)
  }

  @Test
  fun aBubbleShowsNoItemThatMisbehaves() {
    for ((bottomBar, shareWith) in settings) {
      assertThat(menu(bottomBar, share, shareWith, inBubble = true)).containsNoneIn(misbehaving)
      assertThat(bar(bottomBar, inBubble = true)).containsNoneIn(misbehaving)
    }
  }

  @Test
  fun aBubbleShowsEveryWorkingItemInItsPlaceAfterOpenInFullWindow() {
    for ((bottomBar, shareWith) in settings) {
      val outside = menu(bottomBar, share, shareWith, inBubble = false)
      val inside = menu(bottomBar, share, shareWith, inBubble = true)
      assertThat(inside).containsExactlyElementsIn(listOf("menu_open_in_full_window") + outside.filter { it in working }).inOrder()
      // No item reaches a bubble untested.
      assertThat(outside.filter { it !in misbehaving }).containsExactlyElementsIn(outside.filter { it in working })

      val outsideBar = bar(bottomBar, inBubble = false)
      assertThat(bar(bottomBar, inBubble = true)).containsExactlyElementsIn(outsideBar.filter { it in working }).inOrder()
      assertThat(outsideBar.filter { it !in misbehaving }).containsExactlyElementsIn(outsideBar.filter { it in working })
    }
  }

  @Test
  fun outsideABubbleEveryItemShowsAsBefore() {
    for (screen in BrowsingScreen.entries) {
      val article = screen == BrowsingScreen.Article
      for (bottomBar in listOf(true, false)) {
        for (button in listOf(share, null)) {
          for (shareWith in listOf(null, "Messages")) {
            val expected = buildList {
              if (article) add("menu_open_full_page")
              if (button != null) add("menu_action_button")
              if (article) add("menu_text_size")
              addAll(listOf("menu_copy_link", "menu_open_with", "menu_share"))
              if (shareWith != null) add("menu_share_with")
              addAll(listOf("menu_history", "menu_add_to_home_screen"))
              if (!bottomBar) {
                add("tabs")
                if (!article) add("bottom_bar_article_view")
              }
            }
            assertThat(menu(bottomBar, button, shareWith, inBubble = false, screen = screen)).containsExactlyElementsIn(expected).inOrder()
          }
        }
        val expectedBar = if (!bottomBar) {
          emptyList()
        } else {
          listOf("bottom_bar_open_in_new_tab", "bottom_bar_share", "bottom_bar_tabs", "bottom_bar_minimize_tab") +
            (if (article) emptyList() else listOf("bottom_bar_article_view"))
        }
        assertThat(bar(bottomBar, inBubble = false, screen = screen)).containsExactlyElementsIn(expectedBar).inOrder()
        assertThat(ids(browsingBottomBarItems(screen, bottomBar, text))).containsExactlyElementsIn(expectedBar).inOrder()
      }
    }
  }
}
