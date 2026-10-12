/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.ui.settings

import androidx.annotation.StringRes
import arun.com.chromer.R
import arun.com.chromer.data.preferences.SettingsSchema

/** The screen that shows a setting; [None] for a setting with no control on a settings screen. */
enum class SettingsScreenId { BrowsingMode, LookAndFeel, BrowsingOptions, PerApp, ProviderSelection, None }

/** The control a setting has on its screen. */
enum class ControlKind {
  /** One of the browsing mode choices. */
  Radio,
  Switch,
  Checkbox,

  /** A checkbox indented under another row, shown only while that row is on. */
  SubCheckbox,

  /** A row that opens a single-choice dialog. */
  List,

  /** A row that opens the colour sheet. */
  Colour,

  /** The four theme chips. */
  Chips,

  /** A row that opens the app picker sheet. */
  AppChoice,

  /** A row that opens another screen. */
  Link,
  None,
}

/**
 * One settings key: where it is shown, in which group and position, with which control, and which
 * key turns it on or off. [usedBy] names the reader of a key that has no control on these screens.
 */
data class SettingsEntry(
  val key: String,
  val screen: SettingsScreenId,
  @StringRes val group: Int,
  val position: Int,
  val control: ControlKind,
  val dependsOn: String? = null,
  val usedBy: String? = null,
)

/**
 * Every settings key, taken from the old preference screens (the seven XML files) and the four old
 * settings activities, in the order they showed them. Screens draw their rows in this order.
 */
object SettingsKeyTable {
  const val THEME_MODE = "theme_mode"
  const val TOOLBAR_COLOR_PREF = "toolbar_color_pref"
  const val TOOLBAR_COLOR = "toolbar_color"
  const val DYNAMIC_COLOR = "dynamic_color"
  const val DYNAMIC_COLOR_APP = "dynamic_color_app"
  const val DYNAMIC_COLOR_WEB = "dynamic_color_web"
  const val PREFERRED_ACTION = "preferred_action_preference"
  const val ANIMATION_TYPE = "animation_preference"
  const val ANIMATION_SPEED = "animation_speed_preference"
  const val WEB_HEAD_ENABLED = "webhead_enabled_pref"
  const val NATIVE_BUBBLES = "native_bubbles_preference"
  const val WEB_HEADS_COLOR = "webhead_color"
  const val WEB_HEAD_SPAWN = "webhead_spawn_preference"
  const val WEB_HEAD_SIZE = "webhead_size_preference"
  const val ARTICLE_THEME = "article_theme_preference"
  const val AMP_MODE = "amp_mode_pref"
  const val ARTICLE_MODE = "article_mode_pref"
  const val PREFERRED_PACKAGE = "preferred_package"
  const val SECONDARY_BROWSER = "secondary_preference"
  const val FAV_SHARE = "fav_share_preference"
  const val MERGE_TABS = "merge_tabs_and_apps_preference"
  const val PER_APP_LINK = "blacklist_preference_dummy"
  const val AGGRESSIVE_LOADING = "aggressive_loading"
  const val WEB_HEAD_CLOSE_ON_OPEN = "webhead_close_onclick_pref"
  const val BOTTOM_BAR = "bottombar_enabled_preference"
  const val MINIMIZE_BEHAVIOR = SettingsSchema.MINIMIZE_BEHAVIOR
  const val PER_APP_SETTINGS = "blacklist_preference"
  const val USE_WEBVIEW = "use_webview_pref"
  const val USE_WALLPAPER_COLOURS = SettingsSchema.USE_WALLPAPER_COLOURS

  private fun mode(key: String, position: Int) =
    SettingsEntry(key, SettingsScreenId.BrowsingMode, 0, position, ControlKind.Radio)

  val entries: List<SettingsEntry> = listOf(
    // Browsing mode: the mode list (slide over is "neither key on"), then the load faster group.
    mode(WEB_HEAD_ENABLED, 1),
    mode(NATIVE_BUBBLES, 2),
    SettingsEntry(AMP_MODE, SettingsScreenId.BrowsingMode, R.string.load_faster_heading, 3, ControlKind.Switch),
    SettingsEntry(ARTICLE_MODE, SettingsScreenId.BrowsingMode, R.string.load_faster_heading, 4, ControlKind.Switch),

    // Look and feel: the new theme group first, then the legacy groups in their order.
    SettingsEntry(THEME_MODE, SettingsScreenId.LookAndFeel, R.string.settings_theme, 1, ControlKind.Chips),
    SettingsEntry(USE_WALLPAPER_COLOURS, SettingsScreenId.LookAndFeel, R.string.settings_theme, 2, ControlKind.Switch),
    SettingsEntry(TOOLBAR_COLOR_PREF, SettingsScreenId.LookAndFeel, R.string.personalization, 3, ControlKind.Switch),
    SettingsEntry(TOOLBAR_COLOR, SettingsScreenId.LookAndFeel, R.string.personalization, 4, ControlKind.Colour, dependsOn = TOOLBAR_COLOR_PREF),
    SettingsEntry(DYNAMIC_COLOR, SettingsScreenId.LookAndFeel, R.string.personalization, 5, ControlKind.Switch, dependsOn = TOOLBAR_COLOR_PREF),
    SettingsEntry(DYNAMIC_COLOR_APP, SettingsScreenId.LookAndFeel, R.string.personalization, 6, ControlKind.SubCheckbox, dependsOn = DYNAMIC_COLOR),
    SettingsEntry(DYNAMIC_COLOR_WEB, SettingsScreenId.LookAndFeel, R.string.personalization, 7, ControlKind.SubCheckbox, dependsOn = DYNAMIC_COLOR),
    SettingsEntry(PREFERRED_ACTION, SettingsScreenId.LookAndFeel, R.string.personalization, 8, ControlKind.List),
    SettingsEntry(ANIMATION_TYPE, SettingsScreenId.LookAndFeel, R.string.personalization, 9, ControlKind.List),
    SettingsEntry(ANIMATION_SPEED, SettingsScreenId.LookAndFeel, R.string.personalization, 10, ControlKind.List, dependsOn = ANIMATION_TYPE),
    SettingsEntry(WEB_HEADS_COLOR, SettingsScreenId.LookAndFeel, R.string.web_heads, 11, ControlKind.Colour, dependsOn = WEB_HEAD_ENABLED),
    SettingsEntry(WEB_HEAD_SPAWN, SettingsScreenId.LookAndFeel, R.string.web_heads, 12, ControlKind.List, dependsOn = WEB_HEAD_ENABLED),
    SettingsEntry(WEB_HEAD_SIZE, SettingsScreenId.LookAndFeel, R.string.web_heads, 13, ControlKind.List, dependsOn = WEB_HEAD_ENABLED),
    SettingsEntry(ARTICLE_THEME, SettingsScreenId.LookAndFeel, R.string.article_mode, 14, ControlKind.List),

    // Browsing options.
    SettingsEntry(PREFERRED_PACKAGE, SettingsScreenId.BrowsingOptions, R.string.app_preferences, 1, ControlKind.Link),
    SettingsEntry(SECONDARY_BROWSER, SettingsScreenId.BrowsingOptions, R.string.app_preferences, 2, ControlKind.AppChoice),
    SettingsEntry(FAV_SHARE, SettingsScreenId.BrowsingOptions, R.string.app_preferences, 3, ControlKind.AppChoice),
    SettingsEntry(MERGE_TABS, SettingsScreenId.BrowsingOptions, R.string.behavior_preferences, 4, ControlKind.Switch),
    SettingsEntry(PER_APP_LINK, SettingsScreenId.BrowsingOptions, R.string.behavior_preferences, 5, ControlKind.Link),
    SettingsEntry(AGGRESSIVE_LOADING, SettingsScreenId.BrowsingOptions, R.string.web_heads, 6, ControlKind.Checkbox, dependsOn = WEB_HEAD_ENABLED),
    SettingsEntry(WEB_HEAD_CLOSE_ON_OPEN, SettingsScreenId.BrowsingOptions, R.string.web_heads, 7, ControlKind.Checkbox, dependsOn = WEB_HEAD_ENABLED),
    SettingsEntry(BOTTOM_BAR, SettingsScreenId.BrowsingOptions, R.string.bottom_bar, 8, ControlKind.Switch),
    SettingsEntry(MINIMIZE_BEHAVIOR, SettingsScreenId.BrowsingOptions, R.string.bottom_bar, 9, ControlKind.List),

    // Per-app settings and provider selection.
    SettingsEntry(PER_APP_SETTINGS, SettingsScreenId.PerApp, 0, 1, ControlKind.Switch),
    SettingsEntry(USE_WEBVIEW, SettingsScreenId.ProviderSelection, 0, 1, ControlKind.Radio),

    // Keys with no control on a settings screen today; listed with the code that reads them.
    none("firstrun_3", "first-run intro trigger"),
    none("version_code", "changelog trigger"),
    none("webhead_favicons_pref", "web heads (favicon on each head)"),
    none("pre_fetch_notification_preference", "Custom Tabs pre-fetch"),
    none("warm_up_preference", "Custom Tabs warm-up"),
    none("pre_fetch_preference", "Custom Tabs pre-fetch"),
    none("wifi_preference", "Custom Tabs pre-fetch"),
    none("incognito_mode_pref", "History screen incognito row"),
    none("full_incognito_mode", "browsing in incognito"),
    none("article_text_size_pref", "article reader text size"),
    none("search_engine_preference", "home search bar"),
  )

  private fun none(key: String, usedBy: String) =
    SettingsEntry(key, SettingsScreenId.None, 0, 0, ControlKind.None, usedBy = usedBy)

  private val byKey = entries.associateBy { it.key }

  operator fun get(key: String): SettingsEntry? = byKey[key]

  /** The rows of [screen] in their order on the screen. */
  fun forScreen(screen: SettingsScreenId): List<SettingsEntry> =
    entries.filter { it.screen == screen }.sortedBy { it.position }
}
