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
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import arun.com.chromer.ui.settings.BrowsingMode
import arun.com.chromer.ui.settings.SettingsKeyTable.NATIVE_BUBBLES
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_ENABLED
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
