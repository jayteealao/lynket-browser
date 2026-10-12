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

package arun.com.chromer.ui.settings

import arun.com.chromer.R
import arun.com.chromer.ui.settings.SettingsKeyTable.AGGRESSIVE_LOADING
import arun.com.chromer.ui.settings.SettingsKeyTable.AMP_MODE
import arun.com.chromer.ui.settings.SettingsKeyTable.ANIMATION_SPEED
import arun.com.chromer.ui.settings.SettingsKeyTable.ANIMATION_TYPE
import arun.com.chromer.ui.settings.SettingsKeyTable.ARTICLE_MODE
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR_APP
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR_WEB
import arun.com.chromer.ui.settings.SettingsKeyTable.MERGE_TABS
import arun.com.chromer.ui.settings.SettingsKeyTable.NATIVE_BUBBLES
import arun.com.chromer.ui.settings.SettingsKeyTable.TOOLBAR_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.TOOLBAR_COLOR_PREF
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEADS_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_CLOSE_ON_OPEN
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_ENABLED
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_SIZE
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_SPAWN
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The dependent rows and the dialogs of the old preference screens, one rule at a time. */
class SettingsRulesTest {

  private val none = SettingsValues(emptyMap())

  private fun values(vararg pairs: Pair<String, Any?>) = SettingsValues(pairs.toMap())

  @Test
  fun colouredToolbarOffClearsDynamicColourAndItsBoxes() {
    val result = SettingsRules.change(TOOLBAR_COLOR_PREF, false, values(DYNAMIC_COLOR to true), canReadUsageStats = true)
    assertThat(result.writes).containsExactly(
      TOOLBAR_COLOR_PREF, false, DYNAMIC_COLOR, false, DYNAMIC_COLOR_APP, false, DYNAMIC_COLOR_WEB, false,
    )
    assertThat(result.dialogs).isEmpty()
  }

  @Test
  fun colouredToolbarOnWritesOnlyItself() {
    assertThat(SettingsRules.change(TOOLBAR_COLOR_PREF, true, none, true).writes).containsExactly(TOOLBAR_COLOR_PREF, true)
  }

  @Test
  fun colouredToolbarEnablesTheColourAndDynamicColourRows() {
    assertThat(SettingsRules.isEnabled(TOOLBAR_COLOR, values(TOOLBAR_COLOR_PREF to false))).isFalse()
    assertThat(SettingsRules.isEnabled(DYNAMIC_COLOR, values(TOOLBAR_COLOR_PREF to false))).isFalse()
    assertThat(SettingsRules.isEnabled(TOOLBAR_COLOR, values(TOOLBAR_COLOR_PREF to true))).isTrue()
    assertThat(SettingsRules.isEnabled(DYNAMIC_COLOR, values(TOOLBAR_COLOR_PREF to true))).isTrue()
  }

  @Test
  fun dynamicColourOnShowsItsHelp() {
    val result = SettingsRules.change(DYNAMIC_COLOR, true, none, true)
    assertThat(result.dialogs).containsExactly(SettingsDialog.DynamicColourHelp)
  }

  @Test
  fun dynamicColourOffHidesAndClearsItsBoxes() {
    val result = SettingsRules.change(DYNAMIC_COLOR, false, values(DYNAMIC_COLOR_APP to true, DYNAMIC_COLOR_WEB to true), true)
    assertThat(result.writes).containsExactly(DYNAMIC_COLOR, false, DYNAMIC_COLOR_APP, false, DYNAMIC_COLOR_WEB, false)
    assertThat(SettingsRules.isVisible(DYNAMIC_COLOR_APP, values(DYNAMIC_COLOR to false))).isFalse()
    assertThat(SettingsRules.isVisible(DYNAMIC_COLOR_WEB, values(DYNAMIC_COLOR to true))).isTrue()
  }

  @Test
  fun appColourNeedsUsageAccessOnlyWhenChecked() {
    assertThat(SettingsRules.change(DYNAMIC_COLOR_APP, true, none, canReadUsageStats = false).dialogs)
      .containsExactly(SettingsDialog.UsageAccessForAppColour)
    assertThat(SettingsRules.change(DYNAMIC_COLOR_APP, true, none, canReadUsageStats = true).dialogs).isEmpty()
    assertThat(SettingsRules.change(DYNAMIC_COLOR_APP, false, none, canReadUsageStats = false).dialogs).isEmpty()
  }

  @Test
  fun dynamicColourSummaryFollowsTheBoxes() {
    assertThat(SettingsRules.dynamicColourSummary(none)).isEqualTo(R.string.no_option_selected)
    assertThat(SettingsRules.dynamicColourSummary(values(DYNAMIC_COLOR_APP to true))).isEqualTo(R.string.dynamic_summary_app)
    assertThat(SettingsRules.dynamicColourSummary(values(DYNAMIC_COLOR_WEB to true))).isEqualTo(R.string.dynamic_summary_web)
    assertThat(SettingsRules.dynamicColourSummary(values(DYNAMIC_COLOR_APP to true, DYNAMIC_COLOR_WEB to true)))
      .isEqualTo(R.string.dynamic_summary_appweb)
  }

  @Test
  fun noAnimationDisablesItsSpeed() {
    assertThat(SettingsRules.isEnabled(ANIMATION_SPEED, values(ANIMATION_TYPE to 0))).isFalse()
    assertThat(SettingsRules.isEnabled(ANIMATION_SPEED, values(ANIMATION_TYPE to 1))).isTrue()
  }

  @Test
  fun webHeadRowsTakeInputOnlyWithWebHeadsOn() {
    listOf(WEB_HEADS_COLOR, WEB_HEAD_SPAWN, WEB_HEAD_SIZE, AGGRESSIVE_LOADING, WEB_HEAD_CLOSE_ON_OPEN).forEach { key ->
      assertThat(SettingsRules.isEnabled(key, values(WEB_HEAD_ENABLED to false))).isFalse()
      assertThat(SettingsRules.isEnabled(key, values(WEB_HEAD_ENABLED to true))).isTrue()
    }
    assertThat(SettingsRules.showsSlideOverNotice(values(WEB_HEAD_ENABLED to false))).isTrue()
    assertThat(SettingsRules.showsSlideOverNotice(values(WEB_HEAD_ENABLED to true))).isFalse()
  }

  @Test
  fun mergeTabsOffClearsAggressiveLoadingWhileItIsInEffect() {
    val result = SettingsRules.change(MERGE_TABS, false, values(WEB_HEAD_ENABLED to true, AGGRESSIVE_LOADING to true), true)
    assertThat(result.writes).containsExactly(MERGE_TABS, false, AGGRESSIVE_LOADING, false)
    assertThat(result.dialogs).containsExactly(SettingsDialog.MergeTabsOff)
  }

  @Test
  fun mergeTabsOffLeavesAggressiveLoadingWithWebHeadsOff() {
    val result = SettingsRules.change(MERGE_TABS, false, values(WEB_HEAD_ENABLED to false, AGGRESSIVE_LOADING to true), true)
    assertThat(result.writes).containsExactly(MERGE_TABS, false)
    assertThat(result.dialogs).isEmpty()
  }

  @Test
  fun aggressiveLoadingTurnsMergeTabsOn() {
    val result = SettingsRules.change(AGGRESSIVE_LOADING, true, values(MERGE_TABS to false), true)
    assertThat(result.writes).containsExactly(AGGRESSIVE_LOADING, true, MERGE_TABS, true)
    assertThat(result.dialogs).containsExactly(SettingsDialog.AggressiveLoadingNeedsMergeTabs)
    assertThat(SettingsRules.change(AGGRESSIVE_LOADING, true, values(MERGE_TABS to true), true).dialogs).isEmpty()
  }

  @Test
  fun ampInSlideOverWarnsAndAmpWithArticleExplains() {
    assertThat(SettingsRules.change(AMP_MODE, true, values(WEB_HEAD_ENABLED to false), true).dialogs)
      .containsExactly(SettingsDialog.AmpInSlideOver)
    assertThat(SettingsRules.change(AMP_MODE, true, values(WEB_HEAD_ENABLED to true, ARTICLE_MODE to true), true).dialogs)
      .containsExactly(SettingsDialog.AmpAndArticle)
    assertThat(SettingsRules.change(AMP_MODE, true, values(WEB_HEAD_ENABLED to false, ARTICLE_MODE to true), true).dialogs)
      .containsExactly(SettingsDialog.AmpInSlideOver, SettingsDialog.AmpAndArticle).inOrder()
    assertThat(SettingsRules.change(ARTICLE_MODE, true, values(AMP_MODE to true), true).dialogs)
      .containsExactly(SettingsDialog.AmpAndArticle)
    assertThat(SettingsRules.change(ARTICLE_MODE, true, values(AMP_MODE to false), true).dialogs).isEmpty()
  }

  @Test
  fun slideOverTurnsBothModesOff() {
    val result = SettingsRules.chooseMode(BrowsingMode.SlideOver, overlayGranted = true, notificationsAllowed = true)
    assertThat(result.writes).containsExactly(NATIVE_BUBBLES, false, WEB_HEAD_ENABLED, false)
    assertThat(result.dialogs).isEmpty()
  }

  @Test
  fun webHeadsWithoutTheOverlayPermissionExplainAndStayOff() {
    val result = SettingsRules.chooseMode(BrowsingMode.WebHeads, overlayGranted = false, notificationsAllowed = true)
    assertThat(result.writes[WEB_HEAD_ENABLED]).isEqualTo(false)
    assertThat(result.dialogs).containsExactly(SettingsDialog.OverlayPermission)
  }

  @Test
  fun webHeadsWithThePermissionTurnOnAndAskForNotifications() {
    val result = SettingsRules.chooseMode(BrowsingMode.WebHeads, overlayGranted = true, notificationsAllowed = false)
    assertThat(result.writes).containsExactly(NATIVE_BUBBLES, false, WEB_HEAD_ENABLED, true)
    assertThat(result.dialogs).containsExactly(SettingsDialog.NotificationPermission(forBubbles = false))
    assertThat(SettingsRules.overlayGranted(notificationsAllowed = true).dialogs).isEmpty()
  }

  @Test
  fun nativeBubblesWarnFirstThenAskForNotifications() {
    val result = SettingsRules.chooseMode(BrowsingMode.NativeBubbles, overlayGranted = false, notificationsAllowed = false)
    assertThat(result.writes).containsExactly(NATIVE_BUBBLES, true, WEB_HEAD_ENABLED, false)
    assertThat(result.dialogs).containsExactly(SettingsDialog.NativeBubblesWarning)
    assertThat(SettingsRules.afterBubblesWarning(notificationsAllowed = false))
      .containsExactly(SettingsDialog.NotificationPermission(forBubbles = true))
    assertThat(SettingsRules.afterBubblesWarning(notificationsAllowed = true)).isEmpty()
  }

  @Test
  fun theSelectedModeReadsWebHeadsFirst() {
    assertThat(SettingsRules.selectedMode(none)).isEqualTo(BrowsingMode.SlideOver)
    assertThat(SettingsRules.selectedMode(values(NATIVE_BUBBLES to true))).isEqualTo(BrowsingMode.NativeBubbles)
    assertThat(SettingsRules.selectedMode(values(WEB_HEAD_ENABLED to true, NATIVE_BUBBLES to true))).isEqualTo(BrowsingMode.WebHeads)
  }
}
