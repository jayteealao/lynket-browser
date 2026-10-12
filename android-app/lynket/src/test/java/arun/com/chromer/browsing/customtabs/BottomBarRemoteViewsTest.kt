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

package arun.com.chromer.browsing.customtabs

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.R
import arun.com.chromer.browsing.customtabs.bottombar.BottomBarManager
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.robolectric.annotation.Config

/** The bottom bar keeps its five actions in the old order, with labels, on the neutral surface. */
@HiltAndroidTest
class BottomBarRemoteViewsTest : LynketRobolectricSuite() {

  private fun applied(): View =
    BottomBarManager.createBottomBarRemoteViews(application).apply(application, FrameLayout(application))

  @Test
  fun fiveActionsInTheOldOrder() {
    assertThat(BottomBarManager.getClickableIDs().toList()).containsExactly(
      R.id.bottom_bar_open_in_new_tab,
      R.id.bottom_bar_share,
      R.id.bottom_bar_tabs,
      R.id.bottom_bar_minimize_tab,
      R.id.bottom_bar_article_view,
    ).inOrder()
  }

  @Test
  fun eachActionHasItsLabelAndDescription() {
    val bar = applied()
    val labels = listOf(
      R.id.bottom_bar_open_in_new_tab_text,
      R.id.bottom_bar_share_text,
      R.id.bottom_bar_tabs_text,
      R.id.bottom_bar_minimize_text,
      R.id.bottom_bar_article_view_text,
    ).map { bar.findViewById<TextView>(it).text.toString() }
    assertThat(labels).containsExactly("New tab", "Share", "Tabs", "Minimize", "Article").inOrder()
    val descriptions = BottomBarManager.getClickableIDs().map { bar.findViewById<View>(it).contentDescription.toString() }
    assertThat(descriptions).containsExactly("Open in new tab", "Share", "Tabs", "Minimize", "Article").inOrder()
  }

  @Test
  fun theBarUsesTheNeutralSurfaceAndOneContentColour() {
    val colours = BottomBarManager.barColours(application)
    val bar = applied()
    assertThat((bar.background as ColorDrawable).color).isEqualTo(colours.background)
    assertThat(bar.findViewById<TextView>(R.id.bottom_bar_share_text).currentTextColor).isEqualTo(colours.content)
    assertThat(BottomBarManager.bottomBarColor(application)).isEqualTo(colours.background)
  }

  @Test
  @Config(qualifiers = "night")
  fun theDarkThemeGivesADarkSurface() {
    val light = 0xFF000000.toInt() or 0x808080
    val colours = BottomBarManager.barColours(application)
    // A dark surface is darker than mid grey.
    assertThat(colours.background and 0xFF).isLessThan(light and 0xFF)
  }
}
