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
import arun.com.chromer.search.provider.SearchProviders
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

  /** Set to 1 once the old settings file has been read into DataStore. */
  val MIGRATION_VERSION = intPreferencesKey("settings_migration_version")

  /** Raw text of an old value that did not convert, kept under this prefix plus the key name. */
  const val UNCONVERTED_PREFIX = "legacy_unconverted."

  const val USE_WALLPAPER_COLOURS = "use_wallpaper_colours"

  /** Theme of the app: 0 Auto (follows the system), 1 Light, 2 Dark, 3 Black. */
  const val THEME_MODE = "theme_mode"

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
    bool("webhead_enabled_pref", false),
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
    string("search_engine_preference", SearchProviders.GOOGLE),
    bool("native_bubbles_preference", false),
    // Material You: colours from the wallpaper. New in this release, so off after an update.
    bool(USE_WALLPAPER_COLOURS, false),
    // Theme chips. New in this release; Auto is what every earlier version did.
    SettingKey(THEME_MODE, SettingType.INT) { 0 },
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

fun Preferences.readBoolean(name: String, default: Boolean): Boolean =
  when (val value = raw(name)) {
    null -> default
    is Boolean -> value
    else -> mistyped(name, value, default)
  }

/** Accepts an int or numeric text, so a value the earlier migration stored as text still reads. */
fun Preferences.readInt(name: String, default: Int): Int =
  when (val value = raw(name)) {
    null -> default
    is Int -> value
    is String -> value.trim().toIntOrNull() ?: mistyped(name, value, default)
    else -> mistyped(name, value, default)
  }

fun Preferences.readString(name: String, default: String?): String? =
  when (val value = raw(name)) {
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
