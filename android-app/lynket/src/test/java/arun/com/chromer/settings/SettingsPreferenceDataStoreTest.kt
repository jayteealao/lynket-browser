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

package arun.com.chromer.settings

import android.app.Application
import arun.com.chromer.data.preferences.TestStore
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.raw
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The storage the legacy preference screens read and write. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsPreferenceDataStoreTest {
  private val store = TestStore()
  private val repository = UserPreferencesRepository(store.dataStore, store.scope)
  private val bridge = SettingsPreferenceDataStore(RuntimeEnvironment.getApplication(), repository)

  @After
  fun tearDown() = runBlocking { store.close() }

  @Test
  fun aListSettingIsTextOnScreenAndAnIntInTheStore() {
    bridge.putString(Preferences.ANIMATION_TYPE, "2")

    assertThat(repository.current().raw(Preferences.ANIMATION_TYPE)).isEqualTo(2)
    assertThat(bridge.getString(Preferences.ANIMATION_TYPE, null)).isEqualTo("2")
  }

  @Test
  fun aMissingKeyAnswersTheSchemaDefault() {
    assertThat(bridge.getString(Preferences.ARTICLE_THEME, "4")).isEqualTo("1")
    assertThat(bridge.getBoolean(Preferences.MERGE_TABS_AND_APPS, false)).isTrue()
    assertThat(bridge.getString(Preferences.MINIMIZE_BEHAVIOR_PREFERENCE, null)).isEqualTo("1")
  }

  @Test
  fun aSwitchRoundTrips() {
    bridge.putBoolean(Preferences.WEB_HEAD_ENABLED, true)
    assertThat(bridge.getBoolean(Preferences.WEB_HEAD_ENABLED, false)).isTrue()

    bridge.putBoolean(Preferences.WEB_HEAD_ENABLED, false)
    assertThat(repository.current().raw(Preferences.WEB_HEAD_ENABLED)).isEqualTo(false)
  }
}
