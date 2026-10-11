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

package arun.com.chromer.data.apps

import android.app.Application
import androidx.datastore.preferences.core.emptyPreferences
import arun.com.chromer.data.apps.store.AppDiskStore
import arun.com.chromer.data.apps.store.PaperPerAppMigration
import arun.com.chromer.data.preferences.RecordingTree
import arun.com.chromer.data.preferences.resourceBytes
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.File
import kotlin.random.Random

/**
 * The per-app migration on the PaperDB records that store release 2.1.3 wrote on a device
 * (`paper-2.1.3/APPS/`, copied from the upgrade seed): Calendar blacklisted, Clock incognito.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PaperPerAppMigrationTest {
  private val context: Application get() = RuntimeEnvironment.getApplication()
  private val tree = RecordingTree()
  private val migration by lazy { PaperPerAppMigration(context, clock = { 2000L }) }
  private val book get() = File(context.filesDir, PaperPerAppMigration.BOOK)

  @Before
  fun setUp() {
    Timber.plant(tree)
  }

  @After
  fun tearDown() {
    Timber.uproot(tree)
  }

  private fun installRecords(vararg extra: Pair<String, ByteArray>) {
    book.mkdirs()
    for (pkg in listOf(CALENDAR, CLOCK)) {
      File(book, "$pkg.pt").writeBytes(resourceBytes("paper-2.1.3/APPS/$pkg.pt"))
    }
    extra.forEach { (name, bytes) -> File(book, name).writeBytes(bytes) }
  }

  @Test
  fun theRulesOfRelease213MoveToTheNewStore() = runBlocking {
    installRecords()

    assertThat(migration.shouldMigrate(emptyPreferences())).isTrue()
    val migrated = migration.migrate(emptyPreferences())

    assertThat(migrated[AppDiskStore.blacklistedKey(CALENDAR)]).isTrue()
    assertThat(migrated[AppDiskStore.incognitoKey(CALENDAR)]).isNull()
    assertThat(migrated[AppDiskStore.incognitoKey(CLOCK)]).isTrue()
    assertThat(migrated[AppDiskStore.blacklistedKey(CLOCK)]).isNull()
    assertThat(migration.shouldMigrate(migrated)).isFalse()
    assertThat(File(book, "$CALENDAR.pt").readBytes()).isEqualTo(resourceBytes("paper-2.1.3/APPS/$CALENDAR.pt"))
  }

  @Test
  fun aRecordThatDoesNotReadIsLoggedAndSkipped() = runBlocking {
    installRecords("com.example.broken.pt" to Random(7).nextBytes(64))

    val migrated = migration.migrate(emptyPreferences())

    assertThat(tree.errors.joinToString()).contains("com.example.broken")
    assertThat(migrated[AppDiskStore.blacklistedKey(CALENDAR)]).isTrue()
    assertThat(migrated[AppDiskStore.incognitoKey(CLOCK)]).isTrue()
    val backup = File(migration.migrationDir, "APPS-2000")
    assertThat(backup.list()!!.toList()).containsAtLeast(
      "$CALENDAR.pt", "$CLOCK.pt", "com.example.broken.pt",
    )
    assertThat(File(book, "com.example.broken.pt").exists()).isTrue()
    assertThat(File(migration.migrationDir, PaperPerAppMigration.REPORT).readText())
      .contains("com.example.broken\tfailed")
  }

  @Test
  fun withoutTheOldBookNothingRuns() = runBlocking {
    assertThat(book.exists()).isFalse()
    assertThat(migration.shouldMigrate(emptyPreferences())).isFalse()
  }

  private companion object {
    const val CALENDAR = "com.google.android.calendar"
    const val CLOCK = "com.google.android.deskclock"
  }
}
