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

package arun.com.chromer.data.history

import android.content.Context
import arun.com.chromer.data.database.ChromerDatabase
import arun.com.chromer.data.database.HistoryDatabase
import arun.com.chromer.data.database.entity.WebsiteEntity
import arun.com.chromer.data.database.legacy.HistoryTable
import arun.com.chromer.data.database.legacy.LegacyHistoryImport
import arun.com.chromer.data.preferences.RecordingTree
import arun.com.chromer.data.preferences.resourceBytes
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import kotlin.random.Random

/** The history file of release 2.1.3 comes into the Room database once, merged by URL. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyHistoryImportTest {

  private val context: Context = RuntimeEnvironment.getApplication()
  private val tree = RecordingTree()
  private val opened = mutableListOf<ChromerDatabase>()

  @Before
  fun plant() = Timber.plant(tree)

  @After
  fun tearDown() {
    Timber.uproot(tree)
    opened.forEach { it.close() }
  }

  private fun historyDatabase() =
    HistoryDatabase(context, LegacyHistoryImport(context)) { ChromerDatabase.create(context).also { opened += it } }

  @Test
  fun theReleaseFileComesInWithItsTimesAndVisitsAndIsKept() = runTest {
    val fixture = resourceBytes("history-2.1.3/History")
    val legacy = context.getDatabasePath(HistoryTable.TABLE_NAME).apply { parentFile?.mkdirs(); writeBytes(fixture) }

    val dao = historyDatabase().dao()

    val rows = dao.getAllFlow().first().associateBy { it.url }
    assertThat(rows.keys).containsExactly(
      "https://example.com/",
      "https://example.org/docs/page.html",
      "https://www.wikipedia.org/",
      "https://example.com/search?q=lynket&lang=en",
      "https://example.com/caf%C3%A9/über-straße?q=a%20b&x=1#top"
    )
    assertThat(rows.getValue("https://example.com/").createdAt).isEqualTo(1_791_722_452_635)
    assertThat(rows.getValue("https://example.com/").visitCount).isEqualTo(3)
    assertThat(rows.getValue("https://example.com/").title).isEqualTo("Example Domain")
    assertThat(rows.getValue("https://www.wikipedia.org/").createdAt).isEqualTo(1_791_722_360_793)
    assertThat(rows.getValue("https://www.wikipedia.org/").visitCount).isEqualTo(1)

    assertThat(legacy.exists()).isFalse()
    val kept = migrationFolder(context).listFiles { f -> f.name.matches(Regex("History-\\d+")) }!!
    assertThat(kept).hasLength(1)
    assertThat(kept[0].readBytes()).isEqualTo(fixture)
    assertThat(migrationFolder(context).resolve(LegacyHistoryImport.REPORT).readLines()[1])
      .isEqualTo("5\t5\t0\t5\tok")
    assertThat(tree.errors).isEmpty()
  }

  @Test
  fun oneRowPerUrlWithTheNewestTimeAndTheLargerCount() = runTest {
    ChromerDatabase.create(context).also { opened += it }.websiteDao()
      .insert(WebsiteEntity(url = "https://a.example/", title = "A stored", createdAt = 1_000, visitCount = 5))
    opened.removeAt(0).close()
    writeLegacyHistory(
      context,
      listOf(
        LegacyRow("https://a.example/", "A legacy", created = 2_000, visited = 2),
        LegacyRow("https://b.example/", "B old", created = 3_000, visited = 4),
        LegacyRow("https://b.example/", null, created = 4_000, visited = 1, bookmarked = true)
      )
    )

    val rows = historyDatabase().dao().getAllFlow().first().associateBy { it.url }

    assertThat(rows).hasSize(2)
    with(rows.getValue("https://a.example/")) {
      assertThat(createdAt).isEqualTo(2_000)
      assertThat(visitCount).isEqualTo(5)
      assertThat(title).isEqualTo("A legacy")
    }
    with(rows.getValue("https://b.example/")) {
      assertThat(createdAt).isEqualTo(4_000)
      assertThat(visitCount).isEqualTo(4)
      assertThat(title).isEqualTo("B old")
      assertThat(bookmarked).isTrue()
    }
  }

  @Test
  fun runningTheImportTwiceChangesNothing() = runTest {
    val legacyRows = listOf(
      LegacyRow("https://a.example/", "A", created = 2_000, visited = 2),
      LegacyRow("https://b.example/", "B", created = 3_000, visited = 0)
    )
    val db = ChromerDatabase.create(context).also { opened += it }
    val import = LegacyHistoryImport(context)

    writeLegacyHistory(context, legacyRows)
    import.runOnce(db)
    val first = db.websiteDao().getAllFlow().first()
    writeLegacyHistory(context, legacyRows)
    import.runOnce(db)
    val second = db.websiteDao().getAllFlow().first()

    assertThat(second).isEqualTo(first)
    assertThat(first.single { it.url == "https://b.example/" }.visitCount).isEqualTo(1)
  }

  @Test
  fun anUnreadableFileIsKeptAsideAndHistoryStillWorks() = runTest {
    val garbage = Random(7).nextBytes(8192)
    context.getDatabasePath(HistoryTable.TABLE_NAME).apply { parentFile?.mkdirs(); writeBytes(garbage) }

    val dao = historyDatabase().dao()

    assertThat(dao.getCount()).isEqualTo(0)
    dao.insert(WebsiteEntity(url = "https://after.example/", createdAt = 5_000))
    assertThat(dao.getCount()).isEqualTo(1)
    assertThat(context.getDatabasePath(HistoryTable.TABLE_NAME).exists()).isFalse()
    val kept = migrationFolder(context).listFiles { f -> f.name.startsWith("History-unreadable-") }!!
    assertThat(kept).hasLength(1)
    assertThat(kept[0].readBytes()).isEqualTo(garbage)
    assertThat(migrationFolder(context).resolve(LegacyHistoryImport.REPORT).readText()).contains("unreadable")
    assertThat(tree.errors).isNotEmpty()
  }
}
