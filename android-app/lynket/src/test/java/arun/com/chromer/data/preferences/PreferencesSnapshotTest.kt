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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** The hot copy of the settings store: edits from many callers, and a read that fails first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreferencesSnapshotTest {

  @Test
  fun concurrentEditsAllShowInTheHotCopy() = runBlocking {
    val store = TestStore()
    val snapshot = PreferencesSnapshot(store.dataStore, store.scope)
    val keys = (0 until 24).map { booleanPreferencesKey("key_$it") }

    keys.map { key ->
      async(Dispatchers.Default) { snapshot.edit { it[key] = true } }
    }.awaitAll()

    val current = snapshot.current()
    keys.forEach { assertThat(current[it]).isTrue() }
    store.close()
  }

  @Test
  fun aFailedFirstReadDoesNotPublishDefaultsAndTheCopyFollowsTheStoreOnceItReads() = runBlocking {
    val key = booleanPreferencesKey("web_heads")
    val reads = AtomicInteger()
    val failingOnce = object : DataStore<Preferences> {
      override val data: Flow<Preferences> = flow {
        if (reads.getAndIncrement() == 0) throw IOException("disk full")
        emit(preferencesOf(key to true))
      }

      override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(preferencesOf(key to true))
    }
    val job = Job()
    val snapshot = PreferencesSnapshot(failingOnce, CoroutineScope(job + Dispatchers.IO))

    val published = withTimeout(10_000) { snapshot.flow.filterNotNull().first() }

    assertThat(published[key]).isTrue()
    assertThat(reads.get()).isAtLeast(2)
    job.cancelAndJoin()
  }
}
