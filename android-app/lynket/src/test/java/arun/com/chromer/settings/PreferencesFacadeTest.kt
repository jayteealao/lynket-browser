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

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.util.MAIN_THREAD_WAIT_TAG
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.robolectric.shadows.ShadowLog

/**
 * Every reader of [Preferences] (tiles, receivers, the link path) sees each write at once,
 * whether the facade or the Compose settings screen made it.
 */
@HiltAndroidTest
class PreferencesFacadeTest : LynketRobolectricSuite() {

  @Test
  fun aFacadeWriteIsReadBackAtOnce() {
    preferences.webHeads(true)
    assertThat(preferences.webHeads()).isTrue()

    preferences.webHeads(false)
    assertThat(preferences.webHeads()).isFalse()
  }

  @Test
  fun aWriteFromTheComposeSettingsScreenReachesTheFacade() {
    runBlocking { userPreferencesRepository.setWebHeadsEnabled(true) }
    assertThat(preferences.webHeads()).isTrue()

    runBlocking { userPreferencesRepository.setAnimationType(2) }
    assertThat(preferences.animationType()).isEqualTo(2)
  }

  @Test
  fun theStaticAccessorReturnsTheInjectedInstance() {
    assertThat(Preferences.get(application)).isSameInstanceAs(preferences)
  }

  @Test
  fun aMissingProviderReturnsTheDefaultAndStoresItWithoutWaitingOnTheMainThread() {
    preferences.useWebView(true)
    runBlocking { userPreferencesRepository.setValue(Preferences.PREFERRED_CUSTOM_TAB_PACKAGE, "com.example.gone") }
    ShadowLog.clear()

    val provider = preferences.customTabPackage()

    assertThat(provider).isEqualTo(preferences.defaultCustomTabApp)
    assertThat(ShadowLog.getLogsForTag(MAIN_THREAD_WAIT_TAG)).isEmpty()
    val stored = runBlocking {
      withTimeout(5_000) {
        var values = userPreferencesRepository.fresh()
        while (values[booleanPreferencesKey(Preferences.USE_WEBVIEW_PREF)] != false) {
          delay(10)
          values = userPreferencesRepository.fresh()
        }
        values
      }
    }
    assertThat(stored[stringPreferencesKey(Preferences.PREFERRED_CUSTOM_TAB_PACKAGE)]).isEqualTo(provider)
    assertThat(preferences.useWebView()).isFalse()
  }
}
