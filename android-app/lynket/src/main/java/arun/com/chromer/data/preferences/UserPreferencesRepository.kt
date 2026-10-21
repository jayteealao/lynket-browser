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

package arun.com.chromer.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import arun.com.chromer.di.hilt.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The settings store of the app: one Preferences DataStore holding every key of [SettingsSchema].
 *
 * - [userPreferencesFlow] and [snapshot] for readers that observe;
 * - [current] for readers that need a value at once (the `Preferences` facade, tiles, receivers);
 * - [set] and the named setters for writers; each write is visible to [current] when it returns.
 *
 * Values are read with type tolerance: a value stored with the wrong type yields the default and
 * a warning, never a crash.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope appScope: CoroutineScope,
) {
    private val store = PreferencesSnapshot(dataStore, appScope)

    /** Latest stored values; null until the first read finishes. */
    val snapshot: StateFlow<Preferences?> get() = store.flow

    /** Latest stored values, waiting once for the first read if it has not finished. */
    fun current(): Preferences = store.current()

    /** Reads the store itself, bypassing the hot copy. */
    suspend fun fresh(): Preferences = store.fresh()

    /** Writes [value] under [key]; null removes the key. */
    suspend fun <T> set(key: Preferences.Key<T>, value: T?) {
        store.edit { preferences ->
            if (value == null) preferences.remove(key) else preferences[key] = value
        }
    }

    /** Writes [value] under [name] with the key type of the value; null removes the key. */
    suspend fun setValue(name: String, value: Any?) {
        store.edit { preferences ->
            preferences.remove(stringPreferencesKey(name))
            if (value != null) preferences[keyFor(name, value)] = value
        }
    }

    /** Writes every entry of [values] in one edit, with the key type of each value; null removes. */
    suspend fun setValues(values: Map<String, Any?>) {
        setValuesIf(values) { true }
    }

    /**
     * Writes every entry of [values] in one edit when [condition] holds for the stored values. The
     * check and the write are one step, so a write made meanwhile by another caller is not lost.
     */
    suspend fun setValuesIf(values: Map<String, Any?>, condition: (Preferences) -> Boolean) {
        store.edit { preferences ->
            if (condition(preferences)) {
                values.forEach { (name, value) ->
                    preferences.remove(stringPreferencesKey(name))
                    if (value != null) preferences[keyFor(name, value)] = value
                }
            }
        }
    }

    /**
     * Data class representing all user preferences.
     * Emitted by the preferences Flow whenever any preference changes.
     */
    data class UserPreferences(
        // First Run
        val isFirstRun: Boolean = true,

        // Toolbar Settings
        val isColoredToolbar: Boolean = true,
        val toolbarColor: Int = 0, // Will be set to colorPrimary in UI
        val dynamicToolbar: Boolean = false,
        val dynamicToolbarOnApp: Boolean = false,
        val dynamicToolbarOnWeb: Boolean = false,

        // Web Heads Settings
        val webHeadsEnabled: Boolean = false,
        val webHeadsColor: Int = 0, // Will be set to web_head_color in UI
        val webHeadsFavicons: Boolean = true,
        val webHeadsSpawnLocation: Int = 1,
        val webHeadsSize: Int = 1,
        val webHeadsCloseOnOpen: Boolean = false,

        // Animation Settings
        val animationType: Int = 1,
        val animationSpeed: Int = 1,

        // Browser Settings
        val preferredCustomTabPackage: String? = null,
        val secondaryBrowserComponent: String? = null,
        val useWebView: Boolean = false,
        val preferredAction: Int = 1, // PREFERRED_ACTION_BROWSER

        // Article Mode
        val articleMode: Boolean = false,
        val articleTheme: Int = 1, // THEME_DARK
        val articleTextSizeIncrement: Int = 0,

        // Features
        val ampMode: Boolean = false,
        val incognitoMode: Boolean = false,
        val fullIncognitoMode: Boolean = false,
        val bottomBarEnabled: Boolean = true,
        val mergeTabsAndApps: Boolean = true,
        val perAppSettings: Boolean = false,

        // Performance
        val warmUp: Boolean = false,
        val preFetch: Boolean = false,
        val wifiOnlyPrefetch: Boolean = false,
        val preFetchNotification: Boolean = true,
        val aggressiveLoading: Boolean = false,

        // Sharing
        val favShareComponent: String? = null,

        // Minimize
        val minimizeBehavior: String = "1"
    ) {
        /**
         * Helper to check if animation is enabled
         */
        val isAnimationEnabled: Boolean
            get() = animationType != 0

        /**
         * Helper to check if minimize to webhead is enabled
         */
        val minimizeToWebHead: Boolean
            get() = minimizeBehavior == "2"

        /**
         * Helper to check if dynamic toolbar is enabled for both app and web
         */
        val dynamicToolbarEnabledForBoth: Boolean
            get() = dynamicToolbar && dynamicToolbarOnWeb

        /**
         * Helper to check if app-based toolbar color is enabled
         */
        val isAppBasedToolbar: Boolean
            get() = dynamicToolbarOnApp && dynamicToolbar
    }

    /**
     * Flow of user preferences.
     * Emits the latest preferences whenever any value changes.
     * Catches IOExceptions and emits default values on error.
     */
    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            mapPreferences(preferences)
        }

    // ========== Update Methods ==========
    // Screens write a setting with [set] or [setValues]; the two below also clear a dependent value.

    suspend fun setUseWebView(enabled: Boolean) {
        store.edit { preferences ->
            preferences[PreferencesKeys.USE_WEBVIEW_PREF] = enabled
        }
    }

    suspend fun setCustomTabPackage(packageName: String?) {
        store.edit { preferences ->
            if (packageName != null) {
                preferences[PreferencesKeys.PREFERRED_CUSTOM_TAB_PACKAGE] = packageName
            } else {
                preferences.remove(PreferencesKeys.PREFERRED_CUSTOM_TAB_PACKAGE)
            }
            // When setting custom tab, disable WebView
            preferences[PreferencesKeys.USE_WEBVIEW_PREF] = false
        }
    }

    /**
     * Clear all preferences (for testing or reset)
     */
    suspend fun clearAll() {
        store.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * DataStore preference keys
     */
    private object PreferencesKeys {
        val FIRST_RUN = booleanPreferencesKey("firstrun_3")
        val TOOLBAR_COLOR_PREF = booleanPreferencesKey("toolbar_color_pref")
        val TOOLBAR_COLOR = intPreferencesKey("toolbar_color")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DYNAMIC_COLOR_APP = booleanPreferencesKey("dynamic_color_app")
        val DYNAMIC_COLOR_WEB = booleanPreferencesKey("dynamic_color_web")
        val WEB_HEAD_ENABLED = booleanPreferencesKey("webhead_enabled_pref")
        val WEB_HEADS_COLOR = intPreferencesKey("webhead_color")
        val WEB_HEAD_FAVICON = booleanPreferencesKey("webhead_favicons_pref")
        val WEB_HEAD_SPAWN_LOCATION = intPreferencesKey("webhead_spawn_preference")
        val WEB_HEAD_SIZE = intPreferencesKey("webhead_size_preference")
        val WEB_HEAD_CLOSE_ON_OPEN = booleanPreferencesKey("webhead_close_onclick_pref")
        val ANIMATION_TYPE = intPreferencesKey("animation_preference")
        val ANIMATION_SPEED = intPreferencesKey("animation_speed_preference")
        val PREFERRED_CUSTOM_TAB_PACKAGE = stringPreferencesKey("preferred_package")
        val SECONDARY_PREF = stringPreferencesKey("secondary_preference")
        val USE_WEBVIEW_PREF = booleanPreferencesKey("use_webview_pref")
        val PREFERRED_ACTION = intPreferencesKey("preferred_action_preference")
        val ARTICLE_MODE = booleanPreferencesKey("article_mode_pref")
        val ARTICLE_THEME = intPreferencesKey("article_theme_preference")
        val ARTICLE_TEXT_SIZE = intPreferencesKey("article_text_size_pref")
        val AMP_MODE = booleanPreferencesKey("amp_mode_pref")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode_pref")
        val FULL_INCOGNITO_MODE = booleanPreferencesKey("full_incognito_mode")
        val BOTTOM_BAR_ENABLED = booleanPreferencesKey("bottombar_enabled_preference")
        val MERGE_TABS_AND_APPS = booleanPreferencesKey("merge_tabs_and_apps_preference")
        val PER_APP_SETTINGS = booleanPreferencesKey("blacklist_preference")
        val WARM_UP = booleanPreferencesKey("warm_up_preference")
        val PRE_FETCH = booleanPreferencesKey("pre_fetch_preference")
        val WIFI_PREFETCH = booleanPreferencesKey("wifi_preference")
        val PRE_FETCH_NOTIFICATION = booleanPreferencesKey("pre_fetch_notification_preference")
        val AGGRESSIVE_LOADING = booleanPreferencesKey("aggressive_loading")
        val FAV_SHARE_PREF = stringPreferencesKey("fav_share_preference")
        val MINIMIZE_BEHAVIOR_PREFERENCE = stringPreferencesKey("minimize_behavior_preference")
    }
}

/**
 * Maps stored settings to [UserPreferencesRepository.UserPreferences]; a missing key reads as its
 * schema default, except the toolbar and web head colours, which read 0 for "not stored" because
 * their default is a colour resource. Colour readers use the settings class instead.
 */
internal fun mapPreferences(preferences: Preferences): UserPreferencesRepository.UserPreferences {
    return UserPreferencesRepository.UserPreferences(
        isFirstRun = preferences.readBoolean("firstrun_3", true),
        isColoredToolbar = preferences.readBoolean("toolbar_color_pref", true),
        toolbarColor = preferences.readInt("toolbar_color", 0),
        dynamicToolbar = preferences.readBoolean("dynamic_color", false),
        dynamicToolbarOnApp = preferences.readBoolean("dynamic_color_app", false),
        dynamicToolbarOnWeb = preferences.readBoolean("dynamic_color_web", false),
        webHeadsEnabled = preferences.readBoolean("webhead_enabled_pref", false),
        webHeadsColor = preferences.readInt("webhead_color", 0),
        webHeadsFavicons = preferences.readBoolean("webhead_favicons_pref", true),
        webHeadsSpawnLocation = preferences.readInt("webhead_spawn_preference", 1),
        webHeadsSize = preferences.readInt("webhead_size_preference", 1),
        webHeadsCloseOnOpen = preferences.readBoolean("webhead_close_onclick_pref", false),
        animationType = preferences.readInt("animation_preference", 1),
        animationSpeed = preferences.readInt("animation_speed_preference", 1),
        preferredCustomTabPackage = preferences.readString("preferred_package", null),
        secondaryBrowserComponent = preferences.readString("secondary_preference", null),
        useWebView = preferences.readBoolean("use_webview_pref", false),
        preferredAction = preferences.readInt("preferred_action_preference", 1),
        articleMode = preferences.readBoolean("article_mode_pref", false),
        articleTheme = preferences.readInt("article_theme_preference", 1),
        articleTextSizeIncrement = preferences.readInt("article_text_size_pref", 0),
        ampMode = preferences.readBoolean("amp_mode_pref", false),
        incognitoMode = preferences.readBoolean("incognito_mode_pref", false),
        fullIncognitoMode = preferences.readBoolean("full_incognito_mode", false),
        bottomBarEnabled = preferences.readBoolean("bottombar_enabled_preference", true),
        mergeTabsAndApps = preferences.readBoolean("merge_tabs_and_apps_preference", true),
        perAppSettings = preferences.readBoolean("blacklist_preference", false),
        warmUp = preferences.readBoolean("warm_up_preference", false),
        preFetch = preferences.readBoolean("pre_fetch_preference", false),
        wifiOnlyPrefetch = preferences.readBoolean("wifi_preference", false),
        preFetchNotification = preferences.readBoolean("pre_fetch_notification_preference", true),
        aggressiveLoading = preferences.readBoolean("aggressive_loading", false),
        favShareComponent = preferences.readString("fav_share_preference", null),
        minimizeBehavior = preferences.readString("minimize_behavior_preference", "1") ?: "1",
    )
    }
