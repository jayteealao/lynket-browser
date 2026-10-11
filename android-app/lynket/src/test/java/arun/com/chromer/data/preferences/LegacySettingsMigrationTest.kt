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
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.File

/**
 * The settings migration on the settings file that store release 2.1.3 wrote on a device
 * (`settings-2.1.3/`, copied from the upgrade seed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LegacySettingsMigrationTest {
  private val context: Application get() = RuntimeEnvironment.getApplication()
  private val tree = RecordingTree()
  private lateinit var migration: LegacySettingsMigration

  @Before
  fun setUp() {
    Timber.plant(tree)
    migration = LegacySettingsMigration(context, clock = { 1000L })
  }

  @After
  fun tearDown() {
    Timber.uproot(tree)
  }

  private fun installOldFile(bytes: ByteArray = resourceBytes(FIXTURE)): File =
    migration.oldFile.apply {
      parentFile!!.mkdirs()
      writeBytes(bytes)
    }

  @Test
  fun everyKeyOfRelease213ReadsTheSameValueWithItsSchemaType() = runBlocking {
    installOldFile()
    val store = TestStore(migrations = listOf(migration))
    val stored = store.dataStore.data.first()
    store.close()

    val expected = mapOf(
      "version_code" to 56, "toolbar_color" to -16738680, "webhead_color" to -1499549,
      "animation_preference" to 2, "animation_speed_preference" to 2,
      "preferred_action_preference" to 2, "webhead_spawn_preference" to 2,
      "webhead_size_preference" to 2, "article_theme_preference" to 4,
      "article_text_size_pref" to 3, "minimize_behavior_preference" to "2",
      "preferred_package" to "com.android.chrome",
      "secondary_preference" to "com.android.chrome/com.google.android.apps.chrome.Main",
      "fav_share_preference" to "com.google.android.gm/com.google.android.gm.ComposeActivityGmailExternal",
      "dynamic_color" to true, "webhead_close_onclick_pref" to false, "webhead_enabled_pref" to true,
      "bottombar_enabled_preference" to false, "toolbar_color_pref" to false,
      "warm_up_preference" to true, "pre_fetch_preference" to true, "wifi_preference" to true,
      "pre_fetch_notification_preference" to false, "blacklist_preference_dummy" to true,
      "merge_tabs_and_apps_preference" to false, "aggressive_loading" to true,
      "dynamic_color_app" to true, "dynamic_color_web" to true, "amp_mode_pref" to true,
      "article_mode_pref" to true, "incognito_mode_pref" to true, "full_incognito_mode" to true,
      "use_webview_pref" to true, "webhead_favicons_pref" to false, "blacklist_preference" to true,
      "firstrun_3" to false,
    )
    assertThat(expected).hasSize(36) // the 35 settings keys plus version_code
    expected.forEach { (key, value) -> assertThat(stored.raw(key)).isEqualTo(value) }
    assertThat(stored[SettingsSchema.MIGRATION_VERSION]).isEqualTo(1)
    val report = File(migration.migrationDir, LegacySettingsMigration.REPORT).readText()
    assertThat(report).doesNotContain("\tfailed")
    assertThat(report).contains("animation_preference\tconverted")
  }

  @Test
  fun aValueThatDoesNotConvertIsLoggedKeptAndNotOverwritten() = runBlocking {
    val old = String(resourceBytes(FIXTURE)).replace(
      "<string name=\"animation_preference\">2</string>",
      "<string name=\"animation_preference\">abc</string>",
    )
    val oldFile = installOldFile(old.toByteArray())
    val oldBytes = oldFile.readBytes()

    val migrated = migration.migrate(emptyPreferences())

    assertThat(tree.errors.joinToString()).contains("animation_preference")
    assertThat(migrated.raw("animation_preference")).isNull()
    assertThat(migrated[stringPreferencesKey("legacy_unconverted.animation_preference")]).isEqualTo("abc")
    val backup = File(migration.migrationDir, "${context.packageName}_preferences-1000.xml")
    assertThat(backup.readBytes()).isEqualTo(oldBytes)
    assertThat(oldFile.readText()).contains(">abc<")
    assertThat(migrated.raw("animation_speed_preference")).isEqualTo(2)
  }

  @Test
  fun theMigrationRunsOnce() = runBlocking {
    installOldFile()
    val migrated = migration.migrate(emptyPreferences())

    assertThat(migration.shouldMigrate(emptyPreferences())).isTrue()
    assertThat(migration.shouldMigrate(migrated)).isFalse()
  }

  @Test
  fun aListValueStoredAsTextByTheEarlierMigrationIsRepaired() = runBlocking {
    val earlier = mutablePreferencesOf(stringPreferencesKey("animation_preference") to "2")

    val migrated = migration.migrate(earlier)

    assertThat(migrated.raw("animation_preference")).isEqualTo(2)
    assertThat(migrated.readInt("animation_preference", 1)).isEqualTo(2)
  }

  @Test
  fun theSchemaHoldsEveryKeyOfRelease213() {
    val names = Regex("name=\"([^\"]+)\"").findAll(String(resourceBytes(FIXTURE)))
      .map { it.groupValues[1] }.toList()
    assertThat(names).hasSize(36)
    names.forEach { assertThat(SettingsSchema[it]).isNotNull() }
  }

  private companion object {
    const val FIXTURE = "settings-2.1.3/arun.com.chromer.debug_preferences.xml"
  }
}
