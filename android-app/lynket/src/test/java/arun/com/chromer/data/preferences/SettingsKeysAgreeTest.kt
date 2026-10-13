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

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import arun.com.chromer.settings.Preferences
import arun.com.chromer.ui.settings.ControlKind
import arun.com.chromer.ui.settings.SettingsKeyTable
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier

/**
 * The setting names and defaults are spelled in more than one place: the settings schema, the
 * synchronous settings class, the key table of the settings screens and the observed settings.
 * A name or a default that drifts in one of them fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsKeysAgreeTest {

  private val context: Context = RuntimeEnvironment.getApplication()

  private fun stringConstants(type: Class<*>): Map<String, String> =
    type.declaredFields
      .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
      .associate { field -> field.isAccessible = true; field.name to field.get(null) as String }

  @Test
  fun everyNameOfTheSettingsClassIsInTheSchema() {
    // Values stored under a key, not key names.
    val notNames = setOf("MINIMIZE_BEHAVIOR_PREFERENCE_KEY")
    val names = stringConstants(Preferences::class.java) - notNames
    assertThat(names).isNotEmpty()
    names.forEach { (constant, name) -> assertWithMessage(constant).that(SettingsSchema[name]).isNotNull() }
  }

  @Test
  fun everyNameOfTheKeyTableIsInTheSchemaOrALink() {
    val names = stringConstants(SettingsKeyTable::class.java)
    assertThat(names).isNotEmpty()
    names.forEach { (constant, name) ->
      val isLink = SettingsKeyTable[name]?.control == ControlKind.Link
      assertWithMessage(constant).that(isLink || SettingsSchema[name] != null).isTrue()
    }
  }

  @Test
  fun theObservedSettingsDefaultToTheSchemaDefaults() {
    val filled = mutablePreferencesOf()
    SettingsSchema.keys.forEach { key ->
      key.default(context)?.let { filled[keyFor(key.name, it)] = it }
    }
    // The two colours default to a colour resource; the observed settings say 0 for "not stored"
    // and every colour reader goes through the settings class, which uses the schema default.
    val notStoredColours = { p: UserPreferencesRepository.UserPreferences -> p.copy(toolbarColor = 0, webHeadsColor = 0) }
    assertThat(notStoredColours(mapPreferences(filled))).isEqualTo(mapPreferences(emptyPreferences()))
  }
}
