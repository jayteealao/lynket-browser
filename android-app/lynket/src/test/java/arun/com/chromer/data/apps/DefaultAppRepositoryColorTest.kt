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
import arun.com.chromer.browsing.customtabs.dynamictoolbar.AppColorExtractor
import arun.com.chromer.data.apps.store.AppDiskStore
import arun.com.chromer.data.preferences.TestStore
import arun.com.chromer.shared.Constants.NO_COLOR
import arun.com.chromer.util.MAIN_THREAD_WAIT_TAG
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * The app-colour reads on the link path return the stored colour at once, without a main-thread
 * wait. A missing colour starts one extraction on the app's own scope, which stores the colour it
 * finds; a package whose extraction is pending, found no colour or failed starts no second one in the
 * same process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DefaultAppRepositoryColorTest {
  private val application: Application get() = RuntimeEnvironment.getApplication()

  private lateinit var store: TestStore
  private lateinit var diskStore: AppDiskStore
  private lateinit var testScope: TestScope
  private lateinit var extractor: AppColorExtractor
  private lateinit var repository: DefaultAppRepository

  @Before
  fun setUp() {
    store = TestStore()
    diskStore = AppDiskStore(application, store.dataStore, store.scope)
    testScope = TestScope()
    extractor = mockk()
    every { extractor.extract(any()) } returns NO_COLOR
    // The system store is not used by the colour getters.
    repository = DefaultAppRepository(
      diskStore,
      diskStore,
      testScope,
      StandardTestDispatcher(testScope.testScheduler),
      extractor,
    )
    runBlocking { diskStore.snapshot.first { it != null } }
  }

  @After
  fun tearDown() {
    testScope.cancel()
    runBlocking { store.close() }
  }

  @Test
  fun aStoredColourIsReadOnTheMainThreadWithoutAWaitAndStartsNoExtraction() {
    runBlocking { repository.setPackageColor(PKG, COLOR) }
    ShadowLog.clear()

    val colour = repository.getPackageColorSync(PKG)
    testScope.testScheduler.advanceUntilIdle()

    assertThat(colour).isEqualTo(COLOR)
    assertThat(ShadowLog.getLogsForTag(MAIN_THREAD_WAIT_TAG)).isEmpty()
    verify(exactly = 0) { extractor.extract(any()) }
  }

  @Test
  fun aMissingColourReturnsNoColourAtOnceThenTheExtractedColourIsStoredAndReadByBothReads() {
    every { extractor.extract(OTHER_PKG) } returns COLOR
    ShadowLog.clear()

    val first = repository.getPackageColorSync(OTHER_PKG)

    assertThat(first).isEqualTo(NO_COLOR)
    assertThat(ShadowLog.getLogsForTag(MAIN_THREAD_WAIT_TAG)).isEmpty()
    verify(exactly = 0) { extractor.extract(any()) }

    awaitStoredColour(OTHER_PKG, COLOR)

    verify(exactly = 1) { extractor.extract(OTHER_PKG) }
    assertThat(repository.getPackageColorSync(OTHER_PKG)).isEqualTo(COLOR)
    assertThat(runBlocking { repository.getPackageColor(OTHER_PKG) }).isEqualTo(COLOR)
    testScope.testScheduler.advanceUntilIdle()
    verify(exactly = 1) { extractor.extract(OTHER_PKG) }
  }

  @Test
  fun fiveLinksFromAnAppWithNoColourRunOneExtractionAndStoreNothing() {
    repeat(5) {
      assertThat(repository.getPackageColorSync(OTHER_PKG)).isEqualTo(NO_COLOR)
      testScope.testScheduler.advanceUntilIdle()
    }
    assertThat(runBlocking { repository.getPackageColor(OTHER_PKG) }).isEqualTo(NO_COLOR)
    testScope.testScheduler.advanceUntilIdle()

    verify(exactly = 1) { extractor.extract(OTHER_PKG) }
    assertThat(diskStore.getPackageColorSync(OTHER_PKG)).isEqualTo(NO_COLOR)
  }

  @Test
  fun fiveLinksWhileTheFirstExtractionIsPendingRunItOnce() {
    repeat(5) { repository.getPackageColorSync(OTHER_PKG) }
    verify(exactly = 0) { extractor.extract(any()) }

    testScope.testScheduler.advanceUntilIdle()

    verify(exactly = 1) { extractor.extract(OTHER_PKG) }
  }

  @Test
  fun twoPackagesEachRunOneExtraction() {
    repeat(3) {
      repository.getPackageColorSync(PKG)
      repository.getPackageColorSync(OTHER_PKG)
    }
    testScope.testScheduler.advanceUntilIdle()

    verify(exactly = 1) { extractor.extract(PKG) }
    verify(exactly = 1) { extractor.extract(OTHER_PKG) }
  }

  @Test
  fun aFailedExtractionKeepsTheScopeRunningStoresNothingAndIsNotRetried() {
    every { extractor.extract(OTHER_PKG) } throws IllegalStateException("icon unreadable")

    repository.getPackageColorSync(OTHER_PKG)
    testScope.testScheduler.advanceUntilIdle()
    repository.getPackageColorSync(OTHER_PKG)
    testScope.testScheduler.advanceUntilIdle()

    verify(exactly = 1) { extractor.extract(OTHER_PKG) }
    assertThat(testScope.coroutineContext[Job]!!.isActive).isTrue()
    assertThat(diskStore.getPackageColorSync(OTHER_PKG)).isEqualTo(NO_COLOR)

    // The scope still runs the extraction of another package.
    every { extractor.extract(PKG) } returns COLOR
    repository.getPackageColorSync(PKG)
    awaitStoredColour(PKG, COLOR)
  }

  @Test
  fun theSyncReadReturnsTheSameStoredColourAsTheSuspendRead() {
    runBlocking { repository.setPackageColor(PKG, COLOR) }

    val sync = repository.getPackageColorSync(PKG)
    val suspended = runBlocking { repository.getPackageColor(PKG) }

    assertThat(sync).isEqualTo(suspended)
    assertThat(sync).isEqualTo(COLOR)
  }

  /**
   * Runs the extraction and waits for the store: the write goes through the store's own thread and
   * resumes on the test scheduler, so the scheduler is advanced while waiting.
   */
  private fun awaitStoredColour(packageName: String, colour: Int) {
    runBlocking {
      withTimeout(5_000) {
        while (diskStore.getPackageColorSync(packageName) != colour) {
          testScope.testScheduler.advanceUntilIdle()
          kotlinx.coroutines.delay(10)
        }
      }
    }
    testScope.testScheduler.advanceUntilIdle()
  }

  private companion object {
    const val PKG = "com.example.reader"
    const val OTHER_PKG = "com.example.unknown"
    const val COLOR = -16738680
  }
}
