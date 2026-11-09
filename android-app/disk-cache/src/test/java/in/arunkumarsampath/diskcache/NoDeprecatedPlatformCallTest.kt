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

package `in`.arunkumarsampath.diskcache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The cache sources read parcels and the app version only through the AndroidX compat helpers:
 * no untyped Parcel read, no PackageInfo.versionCode, no deprecation suppression and no debt
 * marker is left in them.
 */
class NoDeprecatedPlatformCallTest {

  private val rules = listOf(
    // Built from parts so this file does not carry the marker it looks for.
    Regex(Regex.escape(listOf("sdlc", "debt").joinToString("-"))) to "debt marker",
    Regex("""@Suppress\(\s*"DEPRECATION"\s*\)""") to "deprecation suppression",
    Regex("""\.versionCode\b""") to "PackageInfo.versionCode",
    Regex("""(?<!ParcelCompat\.)\b(readParcelable|readList)\(""") to "untyped Parcel read",
  )

  private fun hits(line: String): List<String> =
    rules.filter { (regex, _) -> regex.containsMatchIn(line) }.map { it.second }

  @Test
  fun theCacheSourcesCallNoDeprecatedParcelOrPackageInfoApi() {
    val root = File("src/main/java")
    val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    assertTrue("no sources under ${root.absolutePath}", sources.isNotEmpty())

    val found = sources.flatMap { file ->
      file.readLines().mapIndexedNotNull { index, line ->
        hits(line).takeIf { it.isNotEmpty() }?.let { "${file.path}:${index + 1} $it: ${line.trim()}" }
      }
    }

    assertEquals("", found.joinToString("\n"))
  }

  @Test
  fun theMatcherFindsTheCallsItGuardsAgainst() {
    assertEquals(listOf("untyped Parcel read"), hits("parcel.readParcelable(classLoader)"))
    assertEquals(listOf("untyped Parcel read"), hits("parcel.readList(out, loader)"))
    assertEquals(listOf("PackageInfo.versionCode"), hits("result = pInfo.versionCode"))
    assertEquals(listOf("deprecation suppression"), hits("""@Suppress("DEPRECATION")"""))
    assertEquals(emptyList<String>(), hits("ParcelCompat.readParcelable(parcel, classLoader, itemClass)"))
    assertEquals(emptyList<String>(), hits("PackageInfoCompat.getLongVersionCode(pInfo).toInt()"))
  }
}
