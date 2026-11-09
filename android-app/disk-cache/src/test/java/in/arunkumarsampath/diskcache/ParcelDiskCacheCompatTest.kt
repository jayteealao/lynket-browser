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

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Parcels and lists cached by earlier releases read back equal on the oldest supported Android
 * version, on Android 13 and on the current target, and a new app version still empties the cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 36])
class ParcelDiskCacheCompatTest {

  private val context: Context = RuntimeEnvironment.getApplication()
  private val name = "compat-" + UUID.randomUUID()
  private val dir = File(context.externalCacheDir ?: context.cacheDir, name)
  private val opened = mutableListOf<ParcelDiskCache<TestItem>>()

  private val item = TestItem("Lynket", "https://example.com/a/b", TestTag("news"))
  private val items = listOf(
    TestItem("One", "https://example.com/1", TestTag("first")),
    TestItem("Two", "https://example.com/2", null),
    TestItem("Three", "https://example.com/3", TestTag("third")),
  )

  @After
  fun tearDown() {
    opened.forEach { it.close() }
    dir.deleteRecursively()
  }

  private fun folderVersion() = ParcelDiskCache.getVersionCode(context) + Build.VERSION.SDK_INT

  private fun openCache(): ParcelDiskCache<TestItem> =
    ParcelDiskCache.open(context, TestItem::class.java, name, MAX_SIZE)
      .also { opened += it }

  private fun readList(cache: ParcelDiskCache<TestItem>, key: String): List<TestItem> =
    cache.getList(key)

  private fun setVersionCode(code: Int) {
    val info = shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName)
    if (Build.VERSION.SDK_INT >= 28) {
      info.setLongVersionCode(code.toLong())
    } else {
      // The int field is the only version code before API 28; set it without naming it in code.
      PackageInfo::class.java.getField("versionCode").setInt(info, code)
    }
  }

  @Test
  fun aParcelCachedByAnEarlierReleaseReadsBackEqual() {
    EarlierReleaseCache.writeParcelableEntry(dir, folderVersion(), "https://example.com/a/b", item)

    val read = openCache().get("https://example.com/a/b")

    assertEquals(item, read)
    assertEquals(TestTag("news"), read!!.tag)
  }

  @Test
  fun aListCachedByAnEarlierReleaseReadsBackEqualInOrder() {
    EarlierReleaseCache.writeListEntry(dir, folderVersion(), "list", items)

    assertEquals(items, readList(openCache(), "list"))
  }

  @Test
  fun valuesSetThroughTheCacheReadBack() {
    val cache = openCache()
    cache.set("one", item)
    cache.set("many", items)

    assertEquals(item, cache.get("one"))
    assertEquals(items, readList(cache, "many"))
    assertNull(cache.get("missing"))
    assertEquals(emptyList<TestItem>(), readList(cache, "missing"))
  }

  /**
   * A read of the other kind of entry throws [IllegalAccessError], an [Error] that the cache's
   * `catch (Exception)` does not take, as earlier releases did. Neither app cache stores a list.
   */
  @Test
  fun aListKeyReadAsAParcelAndAParcelKeyReadAsAListThrowAsBefore() {
    val cache = openCache()
    cache.set("one", item)
    cache.set("many", items)

    assertThrows(IllegalAccessError::class.java) { cache.get("many") }
    assertThrows(IllegalAccessError::class.java) { readList(cache, "one") }
    assertEquals(item, cache.get("one"))
    assertEquals(items, readList(cache, "many"))
  }

  @Test
  fun existsRemoveClearAndClose() {
    val cache = openCache()
    cache.set("one", item)
    cache.set("two", item)

    assertTrue(cache.exists("one"))
    assertFalse(cache.exists("missing"))
    assertTrue(cache.remove("one"))
    assertFalse(cache.exists("one"))
    assertNull(cache.get("one"))
    assertFalse(cache.remove("one"))

    cache.clear()
    assertNull(openCache().get("two"))

    val closing = openCache()
    closing.set("three", item)
    closing.close()
    assertEquals(item, openCache().get("three"))
  }

  @Test
  fun theSameVersionKeepsEntriesAndANewVersionEmptiesTheCache() {
    setVersionCode(7)
    val first = openCache()
    first.set("kept", item)
    first.close()

    assertEquals(item, openCache().get("kept"))
    val journal = File(dir, "journal").readLines(Charsets.US_ASCII)
    assertEquals((7 + Build.VERSION.SDK_INT).toString(), journal[2])
    opened.forEach { it.close() }

    setVersionCode(8)
    assertNull(openCache().get("kept"))
    assertEquals(
      (8 + Build.VERSION.SDK_INT).toString(),
      File(dir, "journal").readLines(Charsets.US_ASCII)[2],
    )
  }

  @Test
  fun theVersionCodeIsTheAppVersionCode() {
    setVersionCode(7)

    assertEquals(7, ParcelDiskCache.getVersionCode(context))
  }

  private companion object {
    const val MAX_SIZE = 1024L * 1024L
  }
}
