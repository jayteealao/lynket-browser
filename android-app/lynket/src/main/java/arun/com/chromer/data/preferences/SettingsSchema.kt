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

package arun.com.chromer.data.preferences

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import arun.com.chromer.R
import timber.log.Timber

enum class SettingType { BOOLEAN, INT, STRING }

/**
 * One settings key: its stored type, whether release 2.1.3 stored it as text, and the value a
 * reader gets when nothing is stored.
 */
class SettingKey(
  val name: String,
  val type: SettingType,
  val legacyText: Boolean = false,
  private val defaultValue: (Context) -> Any?,
) {
  /** The DataStore key of this setting, of its stored type; built once. */
  val preferenceKey: Preferences.Key<*> = when (type) {
    SettingType.BOOLEAN -> booleanPreferencesKey(name)
    SettingType.INT -> intPreferencesKey(name)
    SettingType.STRING -> stringPreferencesKey(name)
  }

  fun default(context: Context): Any? = defaultValue(context)

  /**
   * Converts a value read from the old settings file, or a mistyped stored value, to this key's
   * type. Returns null when the value does not convert.
   */
  fun convert(raw: Any): Any? = when (type) {
    SettingType.BOOLEAN -> when (raw) {
      is Boolean -> raw
      is String -> raw.toBooleanStrictOrNull()
      else -> null
    }
    SettingType.INT -> when (raw) {
      is Int -> raw
      is String -> raw.trim().toIntOrNull()
      else -> null
    }
    SettingType.STRING -> when {
      raw is String -> raw
      // An old setter wrote a boolean to this text key; "2" is "minimize to a web head".
      raw is Boolean && name == SettingsSchema.MINIMIZE_BEHAVIOR -> if (raw) "2" else "1"
      else -> null
    }
  }

  fun hasType(value: Any): Boolean = when (type) {
    SettingType.BOOLEAN -> value is Boolean
    SettingType.INT -> value is Int
    SettingType.STRING -> value is String
  }
}

/** Every settings key the app reads, with its DataStore type and default. */
object SettingsSchema {
  const val MINIMIZE_BEHAVIOR = "minimize_behavior_preference"

  /** The stored switch of web heads mode; [BrowsingMode] reads it with [NATIVE_BUBBLES_SWITCH]. */
  const val WEB_HEADS_SWITCH = "webhead_enabled_pref"

  /** The stored switch of native bubble mode. */
  const val NATIVE_BUBBLES_SWITCH = "native_bubbles_preference"

  /** The search engine for typed searches until the person picks one (a search provider name). */
  const val DEFAULT_SEARCH_ENGINE = "Google"

  /** Set to 1 once the old settings file has been read into DataStore. */
  val MIGRATION_VERSION = intPreferencesKey("settings_migration_version")

  /** Raw text of an old value that did not convert, kept under this prefix plus the key name. */
  const val UNCONVERTED_PREFIX = "legacy_unconverted."

  const val USE_WALLPAPER_COLOURS = "use_wallpaper_colours"

  /** Theme of the app: 0 Auto (follows the system), 1 Light, 2 Dark, 3 Black. */
  const val THEME_MODE = "theme_mode"

  /**
   * Whether a Slide over link opens as a sheet over the app it came from (a Partial Custom Tab)
   * instead of full screen. A browser without sheets opens the link full screen.
   */
  const val CUSTOM_TAB_SHEET = "custom_tab_sheet"

  /** Whether a sheet opens at the side of a window 600 dp wide or more, instead of at the bottom. */
  const val CUSTOM_TAB_SIDE_SHEET = "custom_tab_side_sheet"

  private fun bool(name: String, default: Boolean) =
    SettingKey(name, SettingType.BOOLEAN) { default }

  private fun listInt(name: String) =
    SettingKey(name, SettingType.INT, legacyText = true) { 1 }

  private fun string(name: String, default: String?) =
    SettingKey(name, SettingType.STRING) { default }

  val keys: List<SettingKey> = listOf(
    SettingKey("toolbar_color", SettingType.INT) { ContextCompat.getColor(it, R.color.colorPrimary) },
    SettingKey("webhead_color", SettingType.INT) { ContextCompat.getColor(it, R.color.web_head_color) },
    listInt("animation_preference"),
    listInt("animation_speed_preference"),
    listInt("preferred_action_preference"),
    listInt("webhead_spawn_preference"),
    listInt("webhead_size_preference"),
    listInt("article_theme_preference"),
    string(MINIMIZE_BEHAVIOR, "1"),
    string("preferred_package", null),
    string("secondary_preference", null),
    string("fav_share_preference", null),
    SettingKey("article_text_size_pref", SettingType.INT) { 0 },
    bool("firstrun_3", true),
    bool("toolbar_color_pref", true),
    bool("webhead_favicons_pref", true),
    bool("pre_fetch_notification_preference", true),
    bool("merge_tabs_and_apps_preference", true),
    bool("bottombar_enabled_preference", true),
    bool("dynamic_color", false),
    bool("dynamic_color_app", false),
    bool("dynamic_color_web", false),
    bool("webhead_close_onclick_pref", false),
    bool(WEB_HEADS_SWITCH, false),
    bool("warm_up_preference", false),
    bool("pre_fetch_preference", false),
    bool("wifi_preference", false),
    bool("blacklist_preference_dummy", false),
    bool("blacklist_preference", false),
    bool("aggressive_loading", false),
    bool("amp_mode_pref", false),
    bool("article_mode_pref", false),
    bool("incognito_mode_pref", false),
    bool("full_incognito_mode", false),
    bool("use_webview_pref", false),
    SettingKey("version_code", SettingType.INT) { -1 },
    string("search_engine_preference", DEFAULT_SEARCH_ENGINE),
    bool(NATIVE_BUBBLES_SWITCH, false),
    // Material You: colours from the wallpaper. New in this release, so off after an update.
    bool(USE_WALLPAPER_COLOURS, false),
    // Theme chips. New in this release; Auto is what every earlier version did.
    SettingKey(THEME_MODE, SettingType.INT) { 0 },
    // New in this release; off after an update.
    bool(CUSTOM_TAB_SHEET, false),
    bool(CUSTOM_TAB_SIDE_SHEET, true),
  )

  private val byName = keys.associateBy { it.name }

  operator fun get(name: String): SettingKey? = byName[name]

  fun require(name: String): SettingKey =
    byName[name] ?: throw IllegalArgumentException("Unknown settings key $name")
}

/**
 * The stored value under [name], whatever its type. DataStore keys are equal by name, so any
 * typed key finds the entry without a cast (source: `Preferences$Key.equals` in the installed
 * datastore-preferences-core 1.2.1 compares only `name`).
 */
fun Preferences.raw(name: String): Any? = asMap()[stringPreferencesKey(name)]

/**
 * The value stored under [key], whatever type it was stored with: one lookup by the key's name,
 * with no copy of the map. DataStore keys are equal by name and the lookup checks no type (source:
 * `Preferences$Key.equals` and `MutablePreferences.get` in the installed
 * datastore-preferences-core 1.3.0), so a caller checks the value's type before using it.
 */
// sdlc-debt: unchecked cast to Key<Any> so a value stored under another type reads without a
// ClassCastException (ceiling: the type is checked by the caller at runtime); upgrade path: drop it
// once no store holds a mistyped value.
@Suppress("UNCHECKED_CAST")
internal fun Preferences.storedValue(key: Preferences.Key<*>): Any? = this[key as Preferences.Key<Any>]

fun Preferences.readBoolean(name: String, default: Boolean): Boolean =
  when (val value = storedValue(SettingsSchema[name]?.preferenceKey ?: booleanPreferencesKey(name))) {
    null -> default
    is Boolean -> value
    else -> mistyped(name, value, default)
  }

/** Accepts an int or numeric text, so a value the earlier migration stored as text still reads. */
fun Preferences.readInt(name: String, default: Int): Int =
  when (val value = storedValue(SettingsSchema[name]?.preferenceKey ?: intPreferencesKey(name))) {
    null -> default
    is Int -> value
    is String -> value.trim().toIntOrNull() ?: mistyped(name, value, default)
    else -> mistyped(name, value, default)
  }

fun Preferences.readString(name: String, default: String?): String? =
  when (val value = storedValue(SettingsSchema[name]?.preferenceKey ?: stringPreferencesKey(name))) {
    null -> default
    is String -> value
    else -> mistyped(name, value, default)
  }

private fun <T> mistyped(name: String, value: Any, default: T): T {
  Timber.w("Settings key %s holds %s (%s); using the default", name, value, value.javaClass.simpleName)
  return default
}

/** Typed key for [name] that matches the runtime type of [value]. */
// sdlc-debt: unchecked cast to Key<Any>; the key type follows the value's runtime type, so a wrong
// type shows up only at runtime (ceiling); upgrade path: typed keys from SettingsSchema at every caller.
@Suppress("UNCHECKED_CAST")
fun keyFor(name: String, value: Any): Preferences.Key<Any> = when (value) {
  is Boolean -> booleanPreferencesKey(name)
  is Int -> intPreferencesKey(name)
  is String -> stringPreferencesKey(name)
  is Long -> longPreferencesKey(name)
  is Float -> floatPreferencesKey(name)
  is Double -> doublePreferencesKey(name)
  is Set<*> -> stringSetPreferencesKey(name)
  else -> throw IllegalArgumentException("Unsupported settings value type ${value.javaClass} for $name")
} as Preferences.Key<Any>
