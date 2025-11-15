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
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.browsing.customtabs.CustomTabs
import arun.com.chromer.shared.Constants.CHROME_PACKAGE
import arun.com.chromer.util.Utils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Arun on 05/01/2016.
 */
@Singleton
class Preferences @Inject constructor(context: Context) {
  private val context: Context = context.applicationContext

  /**
   * Returns default shared preferences.
   *
   * @return [SharedPreferences] instance
   */
  val defaultSharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(context)

  val isFirstRun: Boolean
    get() {
      if (defaultSharedPreferences.getBoolean(FIRST_RUN, true)) {
        defaultSharedPreferences.edit().putBoolean(FIRST_RUN, false).apply()
        return true
      }
      return false
    }

  fun isColoredToolbar(): Boolean {
    return defaultSharedPreferences.getBoolean(TOOLBAR_COLOR_PREF, true)
  }

  fun toolbarColor(): Int {
    return defaultSharedPreferences.getInt(TOOLBAR_COLOR, ContextCompat.getColor(context, R.color.colorPrimary))
  }

  fun toolbarColor(selectedColor: Int) {
    defaultSharedPreferences.edit().putInt(TOOLBAR_COLOR, selectedColor).apply()
  }

  fun webHeadColor(): Int {
    return defaultSharedPreferences.getInt(WEB_HEADS_COLOR, ContextCompat.getColor(context, R.color.web_head_color))
  }

  fun webHeadColor(selectedColor: Int) {
    defaultSharedPreferences.edit().putInt(WEB_HEADS_COLOR, selectedColor).apply()
  }

  fun isAnimationEnabled(): Boolean {
    return animationType() != 0
  }

  fun animationType(): Int {
    return defaultSharedPreferences.getString(ANIMATION_TYPE, "1")?.toInt() ?: 1
  }

  fun animationSpeed(): Int {
    return defaultSharedPreferences.getString(ANIMATION_SPEED, "1")?.toInt() ?: 1
  }

  fun articleTheme(): Int {
    return defaultSharedPreferences.getString(ARTICLE_THEME, "1")?.toInt() ?: 1
  }

  fun preferredAction(): Int {
    return defaultSharedPreferences.getString(PREFERRED_ACTION, "1")?.toInt() ?: 1
  }

  fun customTabPackage(): String? {
    var packageName = defaultSharedPreferences.getString(PREFERRED_CUSTOM_TAB_PACKAGE, null)
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
    get() {
      if (CustomTabs.isPackageSupportCustomTabs(context, CHROME_PACKAGE)) {
        return CHROME_PACKAGE
      }
      val supportingPackages = CustomTabs.getCustomTabSupportingPackages(context)
      return if (supportingPackages.isNotEmpty()) {
        supportingPackages[0]
      } else {
        null
      }
    }

  fun customTabPackage(string: String?) {
    useWebView(false)
    defaultSharedPreferences.edit().putString(PREFERRED_CUSTOM_TAB_PACKAGE, string).apply()
  }

  fun secondaryBrowserComponent(): String? {
    return defaultSharedPreferences.getString(SECONDARY_PREF, null)
  }

  fun secondaryBrowserComponent(componentString: String?) {
    defaultSharedPreferences.edit().putString(SECONDARY_PREF, componentString).apply()
  }

  fun secondaryBrowserPackage(): String? {
    val flatString = secondaryBrowserComponent() ?: return null
    val cN = ComponentName.unflattenFromString(flatString) ?: return null
    return cN.packageName
  }

  fun favShareComponent(): String? {
    return defaultSharedPreferences.getString(FAV_SHARE_PREF, null)
  }

  fun favShareComponent(componentString: String?) {
    defaultSharedPreferences.edit().putString(FAV_SHARE_PREF, componentString).apply()
  }

  fun favSharePackage(): String? {
    val flatString = favShareComponent() ?: return null
    val cN = ComponentName.unflattenFromString(flatString) ?: return null
    return cN.packageName
  }

  fun warmUp(): Boolean {
    return defaultSharedPreferences.getBoolean(WARM_UP, false)
  }

  fun warmUp(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(WARM_UP, preference).apply()
  }

  fun preFetch(): Boolean {
    return defaultSharedPreferences.getBoolean(PRE_FETCH, false)
  }

  fun ampMode(): Boolean {
    return defaultSharedPreferences.getBoolean(AMP_MODE, false)
  }

  fun ampMode(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(AMP_MODE, preference).apply()
  }

  fun articleMode(): Boolean {
    return defaultSharedPreferences.getBoolean(ARTICLE_MODE, false)
  }

  fun articleMode(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(ARTICLE_MODE, preference).apply()
  }

  fun historyDisabled(): Boolean {
    return defaultSharedPreferences.getBoolean(INCOGNITO_MODE, false)
  }

  fun historyDisabled(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(INCOGNITO_MODE, preference).apply()
  }

  fun fullIncognitoMode(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(FULL_INCOGNITO_MODE, preference).apply()
  }

  fun fullIncognitoMode(): Boolean {
    return defaultSharedPreferences.getBoolean(FULL_INCOGNITO_MODE, false)
  }

  fun useWebView(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(USE_WEBVIEW_PREF, preference).apply()
  }

  fun useWebView(): Boolean {
    return defaultSharedPreferences.getBoolean(USE_WEBVIEW_PREF, false)
  }

  fun minimizeToWebHead(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(MINIMIZE_BEHAVIOR_PREFERENCE, preference).apply()
  }

  fun minimizeToWebHead(): Boolean {
    return defaultSharedPreferences
      .getString(MINIMIZE_BEHAVIOR_PREFERENCE, "1") == MINIMIZE_BEHAVIOR_PREFERENCE_KEY
  }

  fun articleTextSizeIncrement(increment: Int) {
    defaultSharedPreferences.edit().putInt(ARTICLE_TEXT_SIZE, increment).apply()
  }

  fun articleTextSizeIncrement(): Int {
    return defaultSharedPreferences.getInt(ARTICLE_TEXT_SIZE, 0)
  }

  fun preFetch(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(PRE_FETCH, preference).apply()
  }

  fun wifiOnlyPrefetch(): Boolean {
    return defaultSharedPreferences.getBoolean(WIFI_PREFETCH, false)
  }

  fun wifiOnlyPrefetch(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(WIFI_PREFETCH, preference).apply()
  }

  fun preFetchNotification(): Boolean {
    return defaultSharedPreferences.getBoolean(PRE_FETCH_NOTIFICATION, true)
  }

  fun preFetchNotification(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(PRE_FETCH_NOTIFICATION, preference).apply()
  }

  fun dynamicToolbar(): Boolean {
    return defaultSharedPreferences.getBoolean(DYNAMIC_COLOR, false)
  }

  fun dynamiceToolbarEnabledAndWebEnabled(): Boolean {
    return dynamicToolbar() && dynamicToolbarOnWeb()
  }

  fun dynamicToolbar(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(DYNAMIC_COLOR, preference).apply()
  }

  fun dynamicToolbarOnApp(): Boolean {
    return defaultSharedPreferences.getBoolean(DYNAMIC_COLOR_APP, false)
  }

  private fun dynamicToolbarOnApp(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(DYNAMIC_COLOR_APP, preference).apply()
  }

  fun dynamicToolbarOnWeb(): Boolean {
    return defaultSharedPreferences.getBoolean(DYNAMIC_COLOR_WEB, false)
  }

  private fun dynamicToolbarOnWeb(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(DYNAMIC_COLOR_WEB, preference).apply()
  }

  fun aggressiveLoading(): Boolean {
    return Utils.ANDROID_LOLLIPOP &&
      webHeads() &&
      defaultSharedPreferences.getBoolean(AGGRESSIVE_LOADING, false)
  }

  fun aggressiveLoading(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(AGGRESSIVE_LOADING, preference).apply()
  }

  fun dynamicColorSummary(): CharSequence {
    return when {
      dynamicToolbarOnApp() && dynamicToolbarOnWeb() -> context.getString(R.string.dynamic_summary_appweb)
      dynamicToolbarOnApp() -> context.getString(R.string.dynamic_summary_app)
      dynamicToolbarOnWeb() -> context.getString(R.string.dynamic_summary_web)
      else -> context.getString(R.string.no_option_selected)
    }
  }

  fun webHeads(): Boolean {
    return defaultSharedPreferences.getBoolean(WEB_HEAD_ENABLED, false)
  }

  fun webHeads(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(WEB_HEAD_ENABLED, preference).apply()
  }

  fun favicons(): Boolean {
    return defaultSharedPreferences.getBoolean(WEB_HEAD_FAVICON, true)
  }

  fun favicons(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(WEB_HEAD_FAVICON, preference).apply()
  }

  fun webHeadsSpawnLocation(): Int {
    return defaultSharedPreferences.getString(WEB_HEAD_SPAWN_LOCATION, "1")?.toInt() ?: 1
  }

  fun webHeadsSize(): Int {
    return defaultSharedPreferences.getString(WEB_HEAD_SIZE, "1")?.toInt() ?: 1
  }

  fun webHeadsCloseOnOpen(): Boolean {
    return defaultSharedPreferences.getBoolean(WEB_HEAD_CLOSE_ON_OPEN, false)
  }

  fun webHeadsCloseOnOpen(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(WEB_HEAD_CLOSE_ON_OPEN, preference).apply()
  }

  fun perAppSettings(): Boolean {
    return defaultSharedPreferences.getBoolean(PER_APP_SETTINGS, false)
  }

  fun perAppSettings(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(PER_APP_SETTINGS, preference).apply()
  }

  @Suppress("BooleanMethodIsAlwaysInverted")
  fun mergeTabs(): Boolean {
    return Utils.isLollipopAbove() && defaultSharedPreferences.getBoolean(MERGE_TABS_AND_APPS, true)
  }

  fun mergeTabs(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(MERGE_TABS_AND_APPS, preference).apply()
  }

  fun bottomBar(): Boolean {
    return defaultSharedPreferences.getBoolean(BOTTOM_BAR_ENABLED, true)
  }

  fun bottomBar(preference: Boolean) {
    defaultSharedPreferences.edit().putBoolean(BOTTOM_BAR_ENABLED, preference).apply()
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
    private const val FIRST_RUN = "firstrun_3"
    private const val SECONDARY_PREF = "secondary_preference"
    private const val FAV_SHARE_PREF = "fav_share_preference"

    // Singleton instance
    @Volatile
    private var INSTANCE: Preferences? = null

    @JvmStatic
    @Synchronized
    fun get(context: Context): Preferences {
      return INSTANCE ?: Preferences(context).also { INSTANCE = it }
    }
  }
}
