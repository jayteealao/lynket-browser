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

package arun.com.chromer.data.apps

import android.app.Application
import arun.com.chromer.data.apps.store.AppDiskStore
import arun.com.chromer.data.preferences.TestStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Per-app rules survive a new store instance on the same file (a process restart). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppDiskStorePersistenceTest {
  private val application: Application get() = RuntimeEnvironment.getApplication()

  private fun restart(store: TestStore) = TestStore(file = store.file)

  @Test
  fun aBlacklistRuleSurvivesARestart() = runBlocking {
    val first = TestStore()
    AppDiskStore(application, first.dataStore, first.scope).setPackageBlacklisted(PKG)
    first.close()

    val second = restart(first)
    val store = AppDiskStore(application, second.dataStore, second.scope)
    assertThat(store.isPackageBlacklisted(PKG)).isTrue()
    assertThat(store.isPackageIncognito(PKG)).isFalse()
    second.close()
  }

  @Test
  fun incognitoAndColourSurviveARestart() = runBlocking {
    val first = TestStore()
    val writer = AppDiskStore(application, first.dataStore, first.scope)
    writer.setPackageIncognito(PKG)
    writer.setPackageColor(PKG, COLOR)
    assertThat(writer.isPackageIncognito(PKG)).isTrue()
    first.close()

    val second = restart(first)
    val store = AppDiskStore(application, second.dataStore, second.scope)
    assertThat(store.isPackageIncognito(PKG)).isTrue()
    assertThat(store.getPackageColorSync(PKG)).isEqualTo(COLOR)
    assertThat(store.getApp(PKG).incognito).isTrue()
    second.close()
  }

  private companion object {
    const val PKG = "com.example.reader"
    const val COLOR = -16738680
  }
}
