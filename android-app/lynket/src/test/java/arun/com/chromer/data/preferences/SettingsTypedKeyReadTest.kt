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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import arun.com.chromer.ui.settings.SettingsValues
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber

/** Each setting reads through its own typed key: one lookup, and no copy of the stored map. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsTypedKeyReadTest {
  private val context: Application get() = RuntimeEnvironment.getApplication()
  private val warnings = mutableListOf<String>()
  private val tree = object : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
      if (priority == Log.WARN) warnings += message
    }
  }

  @Before
  fun setUp() {
    Timber.plant(tree)
  }

  @After
  fun tearDown() {
    Timber.uproot(tree)
  }

  @Test
  fun everySettingHasOneKeyOfItsNameBuiltOnce() {
    for (key in SettingsSchema.keys) {
      assertWithMessage("key of %s", key.name).that(key.preferenceKey.name).isEqualTo(key.name)
      assertWithMessage("key of %s", key.name)
        .that(SettingsSchema.require(key.name).preferenceKey)
        .isSameInstanceAs(SettingsSchema.require(key.name).preferenceKey)
    }
  }

  @Test
  fun everySettingReadsItsDefaultWhenNothingIsStored() {
    for (key in SettingsSchema.keys) {
      assertWithMessage("default of %s", key.name)
        .that(emptyPreferences().read(key))
        .isEqualTo(key.default(context))
    }
  }

  @Test
  fun everySettingReadsTheValueStoredWithItsType() {
    for (key in SettingsSchema.keys) {
      val sample = sampleOf(key)
      // sdlc-debt: unchecked cast to write a sample through the key's untyped view (ceiling: a wrong
      // sample type shows only when the test runs); upgrade path: typed keys on SettingKey.
      @Suppress("UNCHECKED_CAST")
      val stored = mutablePreferencesOf().apply { this[key.preferenceKey as Preferences.Key<Any>] = sample }
      assertWithMessage("stored %s", key.name).that(stored.read(key)).isEqualTo(sample)
    }
    assertThat(warnings).isEmpty()
  }

  @Test
  fun numericTextInAWholeNumberSettingReadsAsTheNumber() {
    val stored = mutablePreferencesOf(stringPreferencesKey("animation_preference") to " 7 ")

    assertThat(stored.readInt("animation_preference", 1)).isEqualTo(7)
    assertThat(warnings).isEmpty()
  }

  @Test
  fun textInASwitchReadsTheDefaultAndWarns() {
    val stored = mutablePreferencesOf(stringPreferencesKey("dynamic_color") to "true")

    assertThat(stored.readBoolean("dynamic_color", false)).isFalse()
    assertThat(warnings).hasSize(1)
    assertThat(warnings.single()).contains("dynamic_color")
  }

  @Test
  fun aNameOutsideTheSchemaReadsThroughAKeyOfTheReadersType() {
    val name = "blacklisted.com.example"
    assertThat(SettingsSchema[name]).isNull()
    val counting = CountingPreferences(mutablePreferencesOf(booleanPreferencesKey(name) to true))

    assertThat(counting.readBoolean(name, false)).isTrue()
    assertThat(counting.keysRead.map { it.name }).containsExactly(name)
  }

  @Test
  fun everyReadLooksUpOneKeyAndCopiesNoMap() {
    for (key in SettingsSchema.keys) {
      val counting = CountingPreferences(mutablePreferencesOf())
      counting.read(key)
      assertWithMessage("lookups to read %s", key.name).that(counting.keysRead).containsExactly(key.preferenceKey)
    }

    val counting = CountingPreferences(mutablePreferencesOf())
    SettingsValues.from(counting, context)
    assertThat(counting.keysRead).hasSize(42)
    assertThat(counting.keysRead.toSet()).isEqualTo(SettingsSchema.keys.map { it.preferenceKey }.toSet())
  }

  private fun Preferences.read(key: SettingKey): Any? {
    val default = key.default(context)
    return when (key.type) {
      SettingType.BOOLEAN -> readBoolean(key.name, default as Boolean)
      SettingType.INT -> readInt(key.name, default as Int)
      SettingType.STRING -> readString(key.name, default as String?)
    }
  }

  private fun sampleOf(key: SettingKey): Any = when (key.type) {
    SettingType.BOOLEAN -> !(key.default(context) as Boolean)
    SettingType.INT -> 12345
    SettingType.STRING -> "sample ${key.name}"
  }
}
