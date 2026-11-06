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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import arun.com.chromer.di.hilt.DataStoreModule
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.ui.settings.SettingsKeyTable.NATIVE_BUBBLES
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_ENABLED
import arun.com.chromer.ui.settings.SettingsRules
import arun.com.chromer.ui.settings.SettingsValues
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A stored set with both web heads and native bubbles on is repaired to one mode; any other set is left alone. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BrowsingModeMigrationTest {

  private val webHeadsKey = booleanPreferencesKey(WEB_HEAD_ENABLED)
  private val nativeBubblesKey = booleanPreferencesKey(NATIVE_BUBBLES)
  private val unrelatedKey = stringPreferencesKey("preferred_package")

  private fun set(webHeads: Boolean, nativeBubbles: Boolean): Preferences = mutablePreferencesOf(
    webHeadsKey to webHeads,
    nativeBubblesKey to nativeBubbles,
    unrelatedKey to "com.android.chrome",
  )

  /** The three valid stored states, each with the mode it stands for. */
  private val validStates = mapOf(
    BrowsingMode.SlideOver to (false to false),
    BrowsingMode.WebHeads to (true to false),
    BrowsingMode.NativeBubbles to (false to true),
  )

  @Test
  fun onlyASetWithBothSwitchesOnNeedsTheRepair() = runBlocking {
    validStates.values.forEach { (webHeads, nativeBubbles) ->
      assertThat(BrowsingModeMigration.shouldMigrate(set(webHeads, nativeBubbles))).isFalse()
    }
    assertThat(BrowsingModeMigration.shouldMigrate(mutablePreferencesOf())).isFalse()
    assertThat(BrowsingModeMigration.shouldMigrate(set(webHeads = true, nativeBubbles = true))).isTrue()
  }

  @Test
  fun aSetWithBothSwitchesOnIsStoredAsWebHeadsAndKeepsEveryOtherValue() = runBlocking {
    val repaired = BrowsingModeMigration.migrate(set(webHeads = true, nativeBubbles = true))

    assertThat(repaired[webHeadsKey]).isTrue()
    assertThat(repaired[nativeBubblesKey]).isFalse()
    assertThat(repaired[unrelatedKey]).isEqualTo("com.android.chrome")
    assertThat(repaired.asMap()).hasSize(3)
  }

  @Test
  fun aMistypedSwitchReadsAsOffAndNeedsNoRepair() = runBlocking {
    val mistyped = mutablePreferencesOf(webHeadsKey to true, stringPreferencesKey(NATIVE_BUBBLES) to "yes")
    assertThat(BrowsingModeMigration.shouldMigrate(mistyped)).isFalse()
  }

  /**
   * Writes [webHeads] and [nativeBubbles] (and one unrelated value) into a settings file with no
   * repair, as a file copied in from elsewhere arrives, then opens that file with the app's own
   * settings store builder and returns what it reads.
   */
  private fun restoreThenOpen(webHeads: Boolean, nativeBubbles: Boolean): Preferences = runBlocking {
    val restored = TestStore()
    restored.dataStore.edit {
      it[webHeadsKey] = webHeads
      it[nativeBubblesKey] = nativeBubbles
      it[unrelatedKey] = "com.android.chrome"
    }
    restored.close()
    DataStoreModule.settingsDataStore(context) { restored.file }.data.first()
  }

  private val context: Application get() = RuntimeEnvironment.getApplication()

  @Test
  fun aRestoredSetWithBothSwitchesOnIsStoredAsWebHeadsAndTheSettingsScreenAgrees() {
    val stored = restoreThenOpen(webHeads = true, nativeBubbles = true)

    assertThat(stored[webHeadsKey]).isTrue()
    assertThat(stored[nativeBubblesKey]).isFalse()
    assertThat(stored[unrelatedKey]).isEqualTo("com.android.chrome")
    assertThat(BrowsingMode.resolve(stored[webHeadsKey]!!, stored[nativeBubblesKey]!!)).isEqualTo(BrowsingMode.WebHeads)
    assertThat(SettingsRules.selectedMode(SettingsValues.from(stored, context))).isEqualTo(BrowsingMode.WebHeads)
  }

  @Test
  fun aRestoredSetWithOneModeKeepsThePersonsChosenMode() {
    validStates.forEach { (mode, switches) ->
      val (webHeads, nativeBubbles) = switches
      val stored = restoreThenOpen(webHeads, nativeBubbles)

      assertThat(stored[webHeadsKey]).isEqualTo(webHeads)
      assertThat(stored[nativeBubblesKey]).isEqualTo(nativeBubbles)
      assertThat(stored[unrelatedKey]).isEqualTo("com.android.chrome")
      assertThat(SettingsRules.selectedMode(SettingsValues.from(stored, context))).isEqualTo(mode)
    }
  }
}
