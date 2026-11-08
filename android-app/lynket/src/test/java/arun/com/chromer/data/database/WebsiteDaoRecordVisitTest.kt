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

package arun.com.chromer.data.database

import android.content.Context
import androidx.room3.Room
import arun.com.chromer.data.database.dao.WebsiteDao
import arun.com.chromer.data.database.entity.WebsiteEntity
import arun.com.chromer.util.TestClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.minutes

/** A visit is one statement: a stored row keeps its id and details, a new URL gets one row. */
@RunWith(RobolectricTestRunner::class)
class WebsiteDaoRecordVisitTest {

  private val context: Context = RuntimeEnvironment.getApplication()
  private val driver = StatementCountingDriver()
  private val database = Room.inMemoryDatabaseBuilder<ChromerDatabase>(context)
    .setDriver(driver)
    .build()
  private val dao = database.websiteDao()
  private val clock = TestClock(1_700_000_000_000L)

  @After
  fun close() = database.close()

  private suspend fun WebsiteDao.visit(url: String, title: String? = null) = recordVisit(
    url = url,
    title = title,
    faviconUrl = "https://new.example/icon.png",
    canonicalUrl = "https://new.example/canonical",
    themeColor = "#000000",
    ampUrl = "https://new.example/amp",
    bookmarked = false,
    now = clock.now().toEpochMilliseconds()
  )

  @Test
  fun aStoredRowKeepsItsIdAndDetailsAndCountsOneMoreVisitInOneStatement() = runTest {
    val stored = WebsiteEntity(
      url = "https://a.example/",
      title = "Stored title",
      faviconUrl = "https://a.example/favicon.ico",
      canonicalUrl = "https://a.example/canonical",
      themeColor = "#3F51B5",
      ampUrl = "https://a.example/amp",
      bookmarked = true,
      createdAt = 1_000L,
      visitCount = 3
    )
    val id = dao.insert(stored)
    clock.now = clock.now().plus(5.minutes)
    driver.clear()

    dao.visit("https://a.example/", title = "A newer title")

    val statements = driver.historyStatements()
    val row = dao.getByUrl("https://a.example/")!!
    assertThat(statements).hasSize(1)
    assertThat(statements.single()).startsWith("INSERT OR REPLACE")
    assertThat(row).isEqualTo(stored.copy(id = id, createdAt = clock.now().toEpochMilliseconds(), visitCount = 4))
    assertThat(dao.getCount()).isEqualTo(1)
  }

  @Test
  fun aNewUrlGetsOneRowWithOneVisitInOneStatement() = runTest {
    assertThat(dao.getCount()).isEqualTo(0) // opens the database, so its schema statements are not counted
    driver.clear()

    dao.visit("https://new.example/", title = "New page")

    val statements = driver.historyStatements()
    val row = dao.getByUrl("https://new.example/")!!
    assertThat(statements).hasSize(1)
    assertThat(statements.single()).startsWith("INSERT OR REPLACE")
    assertThat(row.visitCount).isEqualTo(1)
    assertThat(row.createdAt).isEqualTo(clock.now().toEpochMilliseconds())
    assertThat(row.title).isEqualTo("New page")
    assertThat(row.faviconUrl).isEqualTo("https://new.example/icon.png")
    assertThat(row.canonicalUrl).isEqualTo("https://new.example/canonical")
    assertThat(row.themeColor).isEqualTo("#000000")
    assertThat(row.ampUrl).isEqualTo("https://new.example/amp")
    assertThat(row.bookmarked).isFalse()
    assertThat(dao.getCount()).isEqualTo(1)
  }

  @Test
  fun visitsToOneNewUrlAtOnceGiveOneRowWithEveryVisit() = runTest {
    (1..8).map { async(Dispatchers.Default) { dao.visit("https://once.example/") } }.awaitAll()

    assertThat(dao.getCount()).isEqualTo(1)
    assertThat(dao.getByUrl("https://once.example/")!!.visitCount).isEqualTo(8)
  }

  @Test
  fun aVisitToAnotherUrlLeavesTheFirstRowIdAndGivesANewLargerId() = runTest {
    dao.visit("https://a.example/")
    val idA = dao.getByUrl("https://a.example/")!!.id

    dao.visit("https://b.example/")
    dao.visit("https://a.example/")

    val idB = dao.getByUrl("https://b.example/")!!.id
    assertThat(dao.getByUrl("https://a.example/")!!.id).isEqualTo(idA)
    assertThat(idB).isGreaterThan(idA)
  }
}
