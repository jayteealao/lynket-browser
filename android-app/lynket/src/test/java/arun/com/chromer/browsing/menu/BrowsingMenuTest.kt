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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The reader's and the web view's menu and bottom bar keep the legacy items, order and rules. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class BrowsingMenuTest {

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }
  private val share = ActionButton("Share", iconRes = R.drawable.ic_share_24dp)

  private fun ids(items: List<BrowsingMenuItem>) = items.map { context.resources.getResourceEntryName(it.id) }

  @Test
  fun articleMenuWithTheBottomBarOn() {
    val items = browsingMenuItems(BrowsingScreen.Article, bottomBar = true, actionButton = share, shareWithApp = null, text = text)
    assertThat(ids(items)).containsExactly(
      "menu_open_full_page", "menu_action_button", "menu_text_size", "menu_copy_link", "menu_open_with",
      "menu_share", "menu_history", "menu_add_to_home_screen",
    ).inOrder()
    assertThat(items.take(3).map { it.placement })
      .containsExactly(MenuPlacement.Always, MenuPlacement.Always, MenuPlacement.IfRoom).inOrder()
    assertThat(items.drop(3).map { it.placement }.toSet()).containsExactly(MenuPlacement.Overflow)
  }

  @Test
  fun webViewMenuWithTheBottomBarOffAddsTabsAndArticle() {
    val items = browsingMenuItems(BrowsingScreen.WebView, bottomBar = false, actionButton = share, shareWithApp = "Messages", text = text)
    assertThat(ids(items)).containsExactly(
      "menu_action_button", "menu_copy_link", "menu_open_with", "menu_share", "menu_share_with", "menu_history",
      "menu_add_to_home_screen", "tabs", "bottom_bar_article_view",
    ).inOrder()
    assertThat(items.first { it.id == R.id.menu_share_with }.title).isEqualTo("Share with Messages")
  }

  @Test
  fun articleMenuWithTheBottomBarOffAddsOnlyTabs() {
    val items = browsingMenuItems(BrowsingScreen.Article, bottomBar = false, actionButton = null, shareWithApp = null, text = text)
    assertThat(ids(items)).contains("tabs")
    assertThat(ids(items)).doesNotContain("bottom_bar_article_view")
    assertThat(ids(items)).doesNotContain("menu_action_button")
  }

  @Test
  fun bottomBarsInTheOldOrder() {
    assertThat(ids(browsingBottomBarItems(BrowsingScreen.Article, true, text))).containsExactly(
      "bottom_bar_open_in_new_tab", "bottom_bar_share", "bottom_bar_tabs", "bottom_bar_minimize_tab",
    ).inOrder()
    assertThat(ids(browsingBottomBarItems(BrowsingScreen.WebView, true, text))).containsExactly(
      "bottom_bar_open_in_new_tab", "bottom_bar_share", "bottom_bar_tabs", "bottom_bar_minimize_tab", "bottom_bar_article_view",
    ).inOrder()
    assertThat(browsingBottomBarItems(BrowsingScreen.WebView, false, text)).isEmpty()
  }

  @Test
  fun preferredActionButtonFollowsTheSetting() {
    val icon = mockk<ImageBitmap>()
    val iconOf = { _: String -> icon }
    val browser = preferredActionButton(Preferences.PREFERRED_ACTION_BROWSER, "com.example/.Main", null, iconOf, text)!!
    assertThat(browser.title).isEqualTo(context.getString(R.string.choose_secondary_browser))
    assertThat(browser.appIcon).isSameInstanceAs(icon)

    val fav = preferredActionButton(Preferences.PREFERRED_ACTION_FAV_SHARE, null, "com.example/.Share", iconOf, text)!!
    assertThat(fav.title).isEqualTo(context.getString(R.string.fav_share_app))

    val gen = preferredActionButton(Preferences.PREFERRED_ACTION_GEN_SHARE, null, null, iconOf, text)!!
    assertThat(gen.title).isEqualTo(context.getString(R.string.share))
    assertThat(gen.iconRes).isEqualTo(R.drawable.ic_share_24dp)
  }

  @Test
  fun preferredActionButtonIsLeftOutWhenItsAppIsGone() {
    val iconOf = { _: String -> mockk<ImageBitmap>() }
    assertThat(preferredActionButton(Preferences.PREFERRED_ACTION_BROWSER, null, null, iconOf, text)).isNull()
    assertThat(preferredActionButton(Preferences.PREFERRED_ACTION_FAV_SHARE, null, null, iconOf, text)).isNull()
    assertThat(preferredActionButton(Preferences.PREFERRED_ACTION_BROWSER, "com.example/.Main", null, { null }, text)).isNull()
  }
}
