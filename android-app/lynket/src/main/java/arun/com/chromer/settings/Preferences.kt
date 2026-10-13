/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

// Phase 7: Converted to Kotlin
package arun.com.chromer.settings

import android.content.ComponentName
import android.content.Context
import arun.com.chromer.R
import arun.com.chromer.browsing.customtabs.CustomTabs
import arun.com.chromer.data.preferences.SettingsSchema
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.readBoolean
import arun.com.chromer.data.preferences.readInt
import arun.com.chromer.data.preferences.readString
import arun.com.chromer.shared.Constants.CHROME_PACKAGE
import arun.com.chromer.util.Utils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous access to the settings for code that reads a value at once and is not a screen:
 * tiles, receivers, services and the link path. Every read and write goes to the settings
 * DataStore through [UserPreferencesRepository]: reads use its hot copy, and a write finishes
 * before the setter returns, so the next read anywhere sees it.
 *
 * A write blocks its thread until the store has it on disk. Screens and view models write
 * through [UserPreferencesRepository]'s suspend functions instead, so the main thread never waits.
 */
@Singleton
class Preferences @Inject constructor(
  @ApplicationContext context: Context,
  private val repository: UserPreferencesRepository,
) {
  private val context: Context = context.applicationContext

  private fun bool(name: String): Boolean =
    repository.current().readBoolean(name, SettingsSchema.require(name).default(context) as Boolean)

  private fun int(name: String): Int =
    repository.current().readInt(name, SettingsSchema.require(name).default(context) as Int)

  private fun string(name: String): String? =
    repository.current().readString(name, SettingsSchema.require(name).default(context) as String?)

  private fun put(name: String, value: Any?) {
    runBlocking(Dispatchers.IO) { repository.setValue(name, value) }
  }

  val isFirstRun: Boolean
    get() {
      if (bool(FIRST_RUN)) {
        put(FIRST_RUN, false)
        return true
      }
      return false
    }

  /** True until the first start has been recorded; reading it changes nothing. */
  fun firstRunPending(): Boolean = bool(FIRST_RUN)

  fun versionCode(): Int = int(VERSION_CODE)

  fun versionCode(versionCode: Int) = put(VERSION_CODE, versionCode)

  fun isColoredToolbar(): Boolean {
    return bool(TOOLBAR_COLOR_PREF)
  }

  fun toolbarColor(): Int {
    return int(TOOLBAR_COLOR)
  }

  fun toolbarColor(selectedColor: Int) {
    put(TOOLBAR_COLOR, selectedColor)
  }

  fun webHeadColor(): Int {
    return int(WEB_HEADS_COLOR)
  }

  fun webHeadColor(selectedColor: Int) {
    put(WEB_HEADS_COLOR, selectedColor)
  }

  fun isAnimationEnabled(): Boolean {
    return animationType() != 0
  }

  fun animationType(): Int {
    return int(ANIMATION_TYPE)
  }

  fun animationSpeed(): Int {
    return int(ANIMATION_SPEED)
  }

  fun articleTheme(): Int {
    return int(ARTICLE_THEME)
  }

  fun preferredAction(): Int {
    return int(PREFERRED_ACTION)
  }

  fun customTabPackage(): String? {
    var packageName = string(PREFERRED_CUSTOM_TAB_PACKAGE)
    if (packageName != null && Utils.isPackageInstalled(context, packageName)) {
      return packageName
    } else {
      packageName = defaultCustomTabApp
      // update the new custom tab package
      customTabPackage(packageName)
    }
    return packageName
  }

  val defaultCustomTabApp: String?
    get() = findDefaultCustomTabApp(context)

  fun customTabPackage(string: String?) {
    useWebView(false)
    put(PREFERRED_CUSTOM_TAB_PACKAGE, string)
  }

  fun secondaryBrowserComponent(): String? {
    return string(SECONDARY_PREF)
  }

  fun secondaryBrowserComponent(componentString: String?) {
    put(SECONDARY_PREF, componentString)
  }

  fun secondaryBrowserPackage(): String? {
    val flatString = secondaryBrowserComponent() ?: return null
    val cN = ComponentName.unflattenFromString(flatString) ?: return null
    return cN.packageName
  }

  fun favShareComponent(): String? {
    return string(FAV_SHARE_PREF)
  }

  fun favShareComponent(componentString: String?) {
    put(FAV_SHARE_PREF, componentString)
  }

  fun favSharePackage(): String? {
    val flatString = favShareComponent() ?: return null
    val cN = ComponentName.unflattenFromString(flatString) ?: return null
    return cN.packageName
  }

  fun warmUp(): Boolean {
    return bool(WARM_UP)
  }

  fun warmUp(preference: Boolean) {
    put(WARM_UP, preference)
  }

  fun preFetch(): Boolean {
    return bool(PRE_FETCH)
  }

  fun ampMode(): Boolean {
    return bool(AMP_MODE)
  }

  fun ampMode(preference: Boolean) {
    put(AMP_MODE, preference)
  }

  fun articleMode(): Boolean {
    return bool(ARTICLE_MODE)
  }

  fun articleMode(preference: Boolean) {
    put(ARTICLE_MODE, preference)
  }

  fun historyDisabled(): Boolean {
    return bool(INCOGNITO_MODE)
  }

  fun historyDisabled(preference: Boolean) {
    put(INCOGNITO_MODE, preference)
  }

  fun fullIncognitoMode(preference: Boolean) {
    put(FULL_INCOGNITO_MODE, preference)
  }

  fun fullIncognitoMode(): Boolean {
    return bool(FULL_INCOGNITO_MODE)
  }

  fun useWebView(preference: Boolean) {
    put(USE_WEBVIEW_PREF, preference)
  }

  fun useWebView(): Boolean {
    return bool(USE_WEBVIEW_PREF)
  }

  fun minimizeToWebHead(): Boolean {
    return string(MINIMIZE_BEHAVIOR_PREFERENCE) == MINIMIZE_BEHAVIOR_PREFERENCE_KEY
  }

  fun articleTextSizeIncrement(increment: Int) {
    put(ARTICLE_TEXT_SIZE, increment)
  }

  fun articleTextSizeIncrement(): Int {
    return int(ARTICLE_TEXT_SIZE)
  }

  fun preFetch(preference: Boolean) {
    put(PRE_FETCH, preference)
  }

  fun wifiOnlyPrefetch(): Boolean {
    return bool(WIFI_PREFETCH)
  }

  fun wifiOnlyPrefetch(preference: Boolean) {
    put(WIFI_PREFETCH, preference)
  }

  fun preFetchNotification(): Boolean {
    return bool(PRE_FETCH_NOTIFICATION)
  }

  fun preFetchNotification(preference: Boolean) {
    put(PRE_FETCH_NOTIFICATION, preference)
  }

  fun dynamicToolbar(): Boolean {
    return bool(DYNAMIC_COLOR)
  }

  fun dynamiceToolbarEnabledAndWebEnabled(): Boolean {
    return dynamicToolbar() && dynamicToolbarOnWeb()
  }

  fun dynamicToolbar(preference: Boolean) {
    put(DYNAMIC_COLOR, preference)
  }

  fun dynamicToolbarOnApp(): Boolean {
    return bool(DYNAMIC_COLOR_APP)
  }

  private fun dynamicToolbarOnApp(preference: Boolean) {
    put(DYNAMIC_COLOR_APP, preference)
  }

  fun dynamicToolbarOnWeb(): Boolean {
    return bool(DYNAMIC_COLOR_WEB)
  }

  private fun dynamicToolbarOnWeb(preference: Boolean) {
    put(DYNAMIC_COLOR_WEB, preference)
  }

  fun aggressiveLoading(): Boolean {
    return Utils.ANDROID_LOLLIPOP &&
      webHeads() &&
      bool(AGGRESSIVE_LOADING)
  }

  fun aggressiveLoading(preference: Boolean) {
    put(AGGRESSIVE_LOADING, preference)
  }

  fun dynamicColorSummary(): CharSequence {
    return when {
      dynamicToolbarOnApp() && dynamicToolbarOnWeb() -> context.getString(R.string.dynamic_summary_appweb)
      dynamicToolbarOnApp() -> context.getString(R.string.dynamic_summary_app)
      dynamicToolbarOnWeb() -> context.getString(R.string.dynamic_summary_web)
      else -> context.getString(R.string.no_option_selected)
    }
  }

  /** Material You: take the app colours from the wallpaper (Android 12 and later). */
  fun useWallpaperColours(): Boolean = bool(SettingsSchema.USE_WALLPAPER_COLOURS)

  fun useWallpaperColours(preference: Boolean) = put(SettingsSchema.USE_WALLPAPER_COLOURS, preference)

  /** The stored theme: 0 Auto, 1 Light, 2 Dark, 3 Black. */
  fun themeMode(): Int = int(SettingsSchema.THEME_MODE)

  fun themeMode(mode: Int) = put(SettingsSchema.THEME_MODE, mode)

  fun webHeads(): Boolean {
    return bool(WEB_HEAD_ENABLED)
  }

  fun webHeads(preference: Boolean) {
    put(WEB_HEAD_ENABLED, preference)
  }

  fun favicons(): Boolean {
    return bool(WEB_HEAD_FAVICON)
  }

  fun favicons(preference: Boolean) {
    put(WEB_HEAD_FAVICON, preference)
  }

  fun webHeadsSpawnLocation(): Int {
    return int(WEB_HEAD_SPAWN_LOCATION)
  }

  fun webHeadsSize(): Int {
    return int(WEB_HEAD_SIZE)
  }

  fun webHeadsCloseOnOpen(): Boolean {
    return bool(WEB_HEAD_CLOSE_ON_OPEN)
  }

  fun webHeadsCloseOnOpen(preference: Boolean) {
    put(WEB_HEAD_CLOSE_ON_OPEN, preference)
  }

  fun perAppSettings(): Boolean {
    return bool(PER_APP_SETTINGS)
  }

  fun perAppSettings(preference: Boolean) {
    put(PER_APP_SETTINGS, preference)
  }

  @Suppress("BooleanMethodIsAlwaysInverted")
  fun mergeTabs(): Boolean {
    return Utils.isLollipopAbove() && bool(MERGE_TABS_AND_APPS)
  }

  fun mergeTabs(preference: Boolean) {
    put(MERGE_TABS_AND_APPS, preference)
  }

  fun bottomBar(): Boolean {
    return bool(BOTTOM_BAR_ENABLED)
  }

  fun bottomBar(preference: Boolean) {
    put(BOTTOM_BAR_ENABLED, preference)
  }

  fun isAppBasedToolbar(): Boolean {
    return dynamicToolbarOnApp() && dynamicToolbar()
  }

  companion object {
    const val TOOLBAR_COLOR = "toolbar_color"
    const val WEB_HEADS_COLOR = "webhead_color"
    const val ANIMATION_TYPE = "animation_preference"
    const val ANIMATION_SPEED = "animation_speed_preference"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val WEB_HEAD_CLOSE_ON_OPEN = "webhead_close_onclick_pref"
    const val PREFERRED_ACTION = "preferred_action_preference"
    const val WEB_HEAD_ENABLED = "webhead_enabled_pref"
    const val WEB_HEAD_SPAWN_LOCATION = "webhead_spawn_preference"
    const val WEB_HEAD_SIZE = "webhead_size_preference"
    const val BOTTOM_BAR_ENABLED = "bottombar_enabled_preference"
    const val PREFERRED_ACTION_BROWSER = 1
    const val PREFERRED_ACTION_FAV_SHARE = 2
    const val PREFERRED_ACTION_GEN_SHARE = 3
    const val ANIMATION_MEDIUM = 1
    const val ANIMATION_SHORT = 2
    const val TOOLBAR_COLOR_PREF = "toolbar_color_pref"
    const val WARM_UP = "warm_up_preference"
    const val PRE_FETCH = "pre_fetch_preference"
    const val WIFI_PREFETCH = "wifi_preference"
    const val PRE_FETCH_NOTIFICATION = "pre_fetch_notification_preference"
    const val PER_APP_PREFERENCE_DUMMY = "blacklist_preference_dummy"
    const val MERGE_TABS_AND_APPS = "merge_tabs_and_apps_preference"
    const val AGGRESSIVE_LOADING = "aggressive_loading"
    const val PREFERRED_CUSTOM_TAB_PACKAGE = "preferred_package"
    const val DYNAMIC_COLOR_APP = "dynamic_color_app"
    const val DYNAMIC_COLOR_WEB = "dynamic_color_web"
    const val AMP_MODE = "amp_mode_pref"
    const val ARTICLE_MODE = "article_mode_pref"
    const val ARTICLE_THEME = "article_theme_preference"
    const val THEME_DARK = 1
    const val THEME_LIGHT = 2
    const val THEME_AUTO = 3
    const val THEME_BLACK = 4
    const val INCOGNITO_MODE = "incognito_mode_pref"
    const val FULL_INCOGNITO_MODE = "full_incognito_mode"
    const val ARTICLE_TEXT_SIZE = "article_text_size_pref"
    const val USE_WEBVIEW_PREF = "use_webview_pref"
    const val MINIMIZE_BEHAVIOR_PREFERENCE = "minimize_behavior_preference"
    const val MINIMIZE_BEHAVIOR_PREFERENCE_KEY = "2"
    private const val WEB_HEAD_FAVICON = "webhead_favicons_pref"
    private const val PER_APP_SETTINGS = "blacklist_preference"
    const val FIRST_RUN = "firstrun_3"
    private const val SECONDARY_PREF = "secondary_preference"
    private const val FAV_SHARE_PREF = "fav_share_preference"

    const val VERSION_CODE = "version_code"

    /** Chrome when it supports Custom Tabs, else the first installed browser that does, else null. */
    @JvmStatic
    fun findDefaultCustomTabApp(context: Context): String? =
      if (CustomTabs.isPackageSupportCustomTabs(context, CHROME_PACKAGE)) {
        CHROME_PACKAGE
      } else {
        CustomTabs.getCustomTabSupportingPackages(context).firstOrNull()
      }

    /** The one Hilt instance, for classes that are not injected. */
    @JvmStatic
    fun get(context: Context): Preferences = entryPoint(context).preferences()

    @JvmStatic
    fun entryPoint(context: Context): PreferencesEntryPoint =
      EntryPointAccessors.fromApplication(context.applicationContext, PreferencesEntryPoint::class.java)
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesEntryPoint {
  fun preferences(): Preferences

  fun userPreferencesRepository(): UserPreferencesRepository
}
