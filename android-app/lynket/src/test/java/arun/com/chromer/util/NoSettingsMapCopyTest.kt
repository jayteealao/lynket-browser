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

package arun.com.chromer.util

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * A read of one setting looks up its typed key and copies no map: app code calls `asMap()` only
 * where it walks every stored key on purpose, one line in each of three files.
 */
class NoSettingsMapCopyTest {

  private val roots = listOf(File("src/main/java"), File("src/debug/java"))

  @Test
  fun onlyTheWalksOfEveryStoredKeyCopyTheMap() {
    val files = roots.flatMap { root ->
      root.walk().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.map { root to it }.toList()
    }
    assertWithMessage("source files scanned under %s", roots.map { it.absolutePath }).that(files.size).isGreaterThan(100)

    val copies = files.flatMap { (root, file) ->
      val path = file.relativeTo(root).invariantSeparatorsPath
      file.readLines().mapIndexedNotNull { index, line -> if ("asMap()" in line) Use(path, index + 1, line.trim()) else null }
    }

    assertWithMessage("map copies outside the walks of every stored key; read one setting through its typed key")
      .that(copies.filter { it.path !in ALLOWED }.map { it.toString() })
      .isEmpty()
    ALLOWED.forEach { path ->
      assertWithMessage("map copies in %s", path).that(copies.count { it.path == path }).isEqualTo(1)
    }

    val rawReaders = files.flatMap { (root, file) ->
      file.readLines().mapIndexedNotNull { index, line ->
        if ("fun Preferences.raw" in line) "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}" else null
      }
    }
    assertWithMessage("readers that copy the map to find one setting").that(rawReaders).isEmpty()
  }

  private data class Use(val path: String, val line: Int, val text: String) {
    override fun toString() = "$path:$line: $text"
  }

  private companion object {
    val ALLOWED = setOf(
      // The one-time read of the old settings file walks every stored key.
      "arun/com/chromer/data/preferences/LegacySettingsMigration.kt",
      // The per-app rule list walks every stored rule.
      "arun/com/chromer/data/apps/store/AppDiskStore.kt",
      // The debug dump lists the stored keys outside the settings schema.
      "arun/com/chromer/debug/StoreDumpReceiver.kt",
    )
  }
}
