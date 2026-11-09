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

import android.app.Application
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import arun.com.chromer.R
import arun.com.chromer.ui.settings.SettingsValues
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.File
import java.nio.file.Files

/**
 * Settings stored before reads went through one typed key per setting read the same values after.
 *
 * The fixture `settings-before-typed-keys/` was written by [writeFixture] with the write path of
 * that time (`keyFor`, the key type follows the value), including the mistyped forms an earlier
 * migration and old setters left: whole numbers stored as text, a Long colour, a boolean in a text
 * setting, a number in a text setting and text in a switch. The expected values below were read on
 * the code before the change and are written out by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsReadParityTest {
  private val context: Application get() = RuntimeEnvironment.getApplication()
  private val warnings = WarningTree()

  @Before
  fun setUp() {
    Timber.plant(warnings)
  }

  @After
  fun tearDown() {
    Timber.uproot(warnings)
  }

  /** Writes the fixture; runs only with LYNKET_WRITE_SETTINGS_FIXTURE=1. */
  @Test
  fun writeFixture(): Unit = runBlocking {
    assumeTrue(System.getenv("LYNKET_WRITE_SETTINGS_FIXTURE") == "1")
    val file = File("src/test/resources/$FIXTURE")
    file.parentFile!!.mkdirs()
    file.delete()
    val store = TestStore(file)
    store.dataStore.edit { prefs -> OLD_FORM.forEach { (name, value) -> prefs[keyFor(name, value)] = value } }
    store.close()
  }

  @Test
  fun everySettingReadsAsBefore(): Unit = runBlocking {
    val stored = openFixture()
    val expected = expected()
    assertThat(SettingsSchema.keys).hasSize(42)
    assertThat(expected.keys).containsExactlyElementsIn(SettingsSchema.keys.map { it.name })

    for (key in SettingsSchema.keys) {
      val default = key.default(context)
      val read: Any? = when (key.type) {
        SettingType.BOOLEAN -> stored.readBoolean(key.name, default as Boolean)
        SettingType.INT -> stored.readInt(key.name, default as Int)
        SettingType.STRING -> stored.readString(key.name, default as String?)
      }
      assertWithMessage("reader of %s", key.name).that(read).isEqualTo(expected[key.name])
    }

    assertThat(SettingsValues.from(stored, context).values).isEqualTo(expected)

    assertThat(mapPreferences(stored)).isEqualTo(
      UserPreferencesRepository.UserPreferences(
        isFirstRun = false,
        isColoredToolbar = false,
        toolbarColor = 0,
        dynamicToolbar = false,
        dynamicToolbarOnApp = true,
        dynamicToolbarOnWeb = true,
        webHeadsEnabled = true,
        webHeadsColor = -1499549,
        webHeadsFavicons = false,
        webHeadsSpawnLocation = 3,
        webHeadsSize = 1,
        webHeadsCloseOnOpen = true,
        animationType = 2,
        animationSpeed = 2,
        preferredCustomTabPackage = "com.android.chrome",
        secondaryBrowserComponent = null,
        useWebView = true,
        preferredAction = 2,
        articleMode = true,
        articleTheme = 4,
        articleTextSizeIncrement = 3,
        ampMode = true,
        incognitoMode = true,
        fullIncognitoMode = true,
        bottomBarEnabled = false,
        mergeTabsAndApps = false,
        perAppSettings = true,
        warmUp = true,
        preFetch = true,
        wifiOnlyPrefetch = true,
        preFetchNotification = false,
        aggressiveLoading = true,
        favShareComponent = null,
        minimizeBehavior = "1",
      ),
    )

    assertThat(warnings.names.toSet()).containsExactly(
      "webhead_size_preference",
      "toolbar_color",
      "minimize_behavior_preference",
      "secondary_preference",
      "dynamic_color",
    )
  }

  private suspend fun openFixture(): Preferences {
    val file = File(Files.createTempDirectory("settings-parity").toFile(), "user_preferences.preferences_pb")
    file.writeBytes(resourceBytes(FIXTURE))
    val store = TestStore(file)
    val stored = store.dataStore.data.first()
    store.close()
    return stored
  }

  private fun expected(): Map<String, Any?> = mapOf(
    // A Long in an int setting reads the schema default.
    "toolbar_color" to ContextCompat.getColor(context, R.color.colorPrimary),
    "webhead_color" to -1499549,
    // Whole numbers stored as text read as numbers; text that is not a number reads the default.
    "animation_preference" to 2,
    "animation_speed_preference" to 2,
    "preferred_action_preference" to 2,
    "webhead_spawn_preference" to 3,
    "webhead_size_preference" to 1,
    "article_theme_preference" to 4,
    // A boolean in this text setting reads the default, and a number in a text setting reads null.
    "minimize_behavior_preference" to "1",
    "preferred_package" to "com.android.chrome",
    "secondary_preference" to null,
    "fav_share_preference" to null,
    "article_text_size_pref" to 3,
    "firstrun_3" to false,
    "toolbar_color_pref" to false,
    "webhead_favicons_pref" to false,
    "pre_fetch_notification_preference" to false,
    "merge_tabs_and_apps_preference" to false,
    "bottombar_enabled_preference" to false,
    // Text in a switch reads the default.
    "dynamic_color" to false,
    "dynamic_color_app" to true,
    "dynamic_color_web" to true,
    "webhead_close_onclick_pref" to true,
    "webhead_enabled_pref" to true,
    "warm_up_preference" to true,
    "pre_fetch_preference" to true,
    "wifi_preference" to true,
    "blacklist_preference_dummy" to true,
    "blacklist_preference" to true,
    "aggressive_loading" to true,
    "amp_mode_pref" to true,
    "article_mode_pref" to true,
    "incognito_mode_pref" to true,
    "full_incognito_mode" to true,
    "use_webview_pref" to true,
    "version_code" to 56,
    "search_engine_preference" to "Google",
    "native_bubbles_preference" to true,
    "use_wallpaper_colours" to true,
    "theme_mode" to 0,
    "custom_tab_sheet" to true,
    "custom_tab_side_sheet" to true,
  )

  /** Collects the setting names of the "using the default" warnings. */
  private class WarningTree : Timber.Tree() {
    val names = mutableListOf<String>()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
      if (priority != Log.WARN) return
      Regex("^Settings key (\\S+) holds").find(message)?.let { names += it.groupValues[1] }
    }
  }

  private companion object {
    const val FIXTURE = "settings-before-typed-keys/user_preferences.preferences_pb"

    /** The stored form, each value with the type the old write path gave it. */
    val OLD_FORM: Map<String, Any> = mapOf(
      "animation_preference" to "2",
      "webhead_spawn_preference" to " 3 ",
      "webhead_size_preference" to "abc",
      "preferred_action_preference" to "2",
      "animation_speed_preference" to 2,
      "article_theme_preference" to 4,
      "article_text_size_pref" to 3,
      "version_code" to 56,
      "webhead_color" to -1499549,
      "toolbar_color" to 5L,
      "minimize_behavior_preference" to true,
      "secondary_preference" to 7,
      "preferred_package" to "com.android.chrome",
      "dynamic_color" to "true",
      "firstrun_3" to false,
      "toolbar_color_pref" to false,
      "webhead_favicons_pref" to false,
      "pre_fetch_notification_preference" to false,
      "merge_tabs_and_apps_preference" to false,
      "bottombar_enabled_preference" to false,
      "dynamic_color_app" to true,
      "dynamic_color_web" to true,
      "webhead_close_onclick_pref" to true,
      "webhead_enabled_pref" to true,
      "warm_up_preference" to true,
      "pre_fetch_preference" to true,
      "wifi_preference" to true,
      "blacklist_preference_dummy" to true,
      "blacklist_preference" to true,
      "aggressive_loading" to true,
      "amp_mode_pref" to true,
      "article_mode_pref" to true,
      "incognito_mode_pref" to true,
      "full_incognito_mode" to true,
      "use_webview_pref" to true,
      "native_bubbles_preference" to true,
      "use_wallpaper_colours" to true,
      "custom_tab_sheet" to true,
      "settings_migration_version" to 1,
      "legacy_unconverted.webhead_size_preference" to "abc",
      "old_unknown_key" to "x",
    )
  }
}
