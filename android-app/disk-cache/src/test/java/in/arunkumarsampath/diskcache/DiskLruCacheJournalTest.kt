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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The Kotlin cache must read caches written by earlier releases and write the same journal format,
 * so cached websites and articles survive an app update.
 */
class DiskLruCacheJournalTest {

  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun readsAJournalWrittenByEarlierReleases() {
    val dir = tmp.newFolder("old")
    File(dir, "journal").writeText(
      "libcore.io.DiskLruCache\n1\n100\n1\n\n" +
        "DIRTY abc\nCLEAN abc 5\nREAD abc\n" +
        "DIRTY gone\nCLEAN gone 3\nREMOVE gone\n",
      Charsets.US_ASCII
    )
    File(dir, "abc.0").writeText("hello", Charsets.UTF_8)

    val cache = DiskLruCache.open(dir, 100, 1, 1024 * 1024)

    assertEquals("hello", cache["abc"]!!.getString(0))
    assertNull(cache["gone"])
    assertEquals(5L, cache.size())
    cache.close()
  }

  @Test
  fun writesTheSameJournalFormat() {
    val dir = tmp.newFolder("new")
    val cache = DiskLruCache.open(dir, 100, 1, 1024 * 1024)
    val editor = cache.edit("key")!!
    editor.set(0, "value")
    editor.commit()
    assertTrue(cache.remove("key"))
    cache.close()

    assertEquals(
      "libcore.io.DiskLruCache\n1\n100\n1\n\nDIRTY key\nCLEAN key 5\nREMOVE key\n",
      File(dir, "journal").readText(Charsets.US_ASCII)
    )
    assertFalse(File(dir, "key.0").exists())
  }

  @Test
  fun dropsTheCacheWhenTheAppVersionChanges() {
    val dir = tmp.newFolder("version")
    DiskLruCache.open(dir, 100, 1, 1024 * 1024).apply {
      edit("key")!!.apply { set(0, "value"); commit() }
      close()
    }

    val reopened = DiskLruCache.open(dir, 101, 1, 1024 * 1024)

    assertNull(reopened["key"])
    reopened.close()
  }

  @Test
  fun readsCrLfLinesAndATruncatedLastLine() {
    val dir = tmp.newFolder("crlf")
    File(dir, "journal").writeText(
      "libcore.io.DiskLruCache\r\n1\r\n100\r\n1\r\n\r\nCLEAN abc 2\r\nREAD ab",
      Charsets.US_ASCII
    )
    File(dir, "abc.0").writeText("hi", Charsets.UTF_8)

    val cache = DiskLruCache.open(dir, 100, 1, 1024 * 1024)

    assertEquals("hi", cache["abc"]!!.getString(0))
    cache.close()
  }
}
