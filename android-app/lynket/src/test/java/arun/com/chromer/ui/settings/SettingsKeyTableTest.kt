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

package arun.com.chromer.ui.settings

import arun.com.chromer.data.preferences.SettingsSchema
import arun.com.chromer.data.preferences.resourceBytes
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * No setting goes missing: every key of the old preference screens (kept as test resources) and
 * every stored key has a row in the key table, on the screen and in the order of the old screens.
 */
class SettingsKeyTableTest {

  private val legacyFiles = listOf(
    "article_preferences", "behavior_preferences", "bottombar_preferences", "browse_faster_options",
    "personalization_preferences", "webhead_options", "webhead_preferences",
  )

  /** Keys of the old screens that are not settings: the screen root. */
  private val notSettings = setOf("settings")

  private fun legacyKeys(file: String): List<String> =
    Regex("android:key=\"([^\"]+)\"").findAll(String(resourceBytes("legacy-preferences/$file.xml"))).map { it.groupValues[1] }.toList()

  @Test
  fun everyKeyOfTheOldScreensHasARow() {
    val keys = legacyFiles.flatMap(::legacyKeys).toSet() - notSettings
    assertThat(keys).isNotEmpty()
    keys.forEach { key -> assertWithMessage(key).that(SettingsKeyTable[key]).isNotNull() }
  }

  @Test
  fun everyStoredKeyHasARow() {
    val stored = SettingsSchema.keys.map { it.name }
    stored.forEach { key -> assertWithMessage(key).that(SettingsKeyTable[key]).isNotNull() }
  }

  @Test
  fun everyRowWithAControlIsAStoredKeyExceptTheLinks() {
    SettingsKeyTable.entries
      .filter { it.control != ControlKind.Link }
      .forEach { assertWithMessage(it.key).that(SettingsSchema[it.key]).isNotNull() }
  }

  @Test
  fun keysWithoutAControlNameTheirReader() {
    SettingsKeyTable.entries.filter { it.control == ControlKind.None }.forEach { entry ->
      assertWithMessage(entry.key).that(entry.usedBy).isNotEmpty()
    }
  }

  @Test
  fun lookAndFeelKeepsTheOldOrderBelowTheThemeGroup() {
    val order = SettingsKeyTable.forScreen(SettingsScreenId.LookAndFeel).map { it.key }
    val personalization = legacyKeys("personalization_preferences") - notSettings
    val webHeads = legacyKeys("webhead_preferences") - notSettings
    val article = legacyKeys("article_preferences") - notSettings
    assertThat(order.take(2)).containsExactly(SettingsKeyTable.THEME_MODE, SettingsKeyTable.USE_WALLPAPER_COLOURS).inOrder()
    assertThat(order.drop(2)).containsExactlyElementsIn(personalization + webHeads + article).inOrder()
  }

  @Test
  fun browsingOptionsKeepTheOldOrder() {
    val order = SettingsKeyTable.forScreen(SettingsScreenId.BrowsingOptions).map { it.key }
    val legacy = listOf(
      SettingsKeyTable.PREFERRED_PACKAGE, SettingsKeyTable.SECONDARY_BROWSER, SettingsKeyTable.FAV_SHARE,
    ) + legacyKeys("behavior_preferences") + legacyKeys("webhead_options") + legacyKeys("bottombar_preferences")
    assertThat(order).containsExactlyElementsIn(legacy - notSettings).inOrder()
  }

  @Test
  fun browsingModeKeepsTheModesThenLoadFaster() {
    val order = SettingsKeyTable.forScreen(SettingsScreenId.BrowsingMode).map { it.key }
    assertThat(order).containsExactly(
      SettingsKeyTable.WEB_HEAD_ENABLED, SettingsKeyTable.NATIVE_BUBBLES, *(legacyKeys("browse_faster_options") - notSettings).toTypedArray(),
    ).inOrder()
  }

  @Test
  fun dependentRowsPointAtARowOfTheTable() {
    SettingsKeyTable.entries.mapNotNull { it.dependsOn }.forEach { assertWithMessage(it).that(SettingsKeyTable[it]).isNotNull() }
  }
}
