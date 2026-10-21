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
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import arun.com.chromer.browsing.customtabs.dynamictoolbar.AppColorExtractorJob
import arun.com.chromer.data.apps.store.AppDiskStore
import arun.com.chromer.data.preferences.TestStore
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.MAIN_THREAD_WAIT_TAG
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * The synchronous app-colour read on the link path returns the stored colour from the in-memory
 * rules without a main-thread wait, and a missing colour still starts the colour extraction once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DefaultAppRepositoryColorTest {
  private val application: Application get() = RuntimeEnvironment.getApplication()
  private val jobScheduler: JobScheduler
    get() = application.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

  private lateinit var store: TestStore
  private lateinit var diskStore: AppDiskStore
  private lateinit var repository: DefaultAppRepository

  @Before
  fun setUp() {
    store = TestStore()
    diskStore = AppDiskStore(application, store.dataStore, store.scope)
    // The system store is not used by the colour getters.
    repository = DefaultAppRepository(application, diskStore, diskStore)
    // Every enqueue still runs for real, so the scheduler shadow holds the job; the mock counts calls.
    mockkStatic(JobIntentService::class)
    every {
      JobIntentService.enqueueWork(any<Context>(), any<Class<*>>(), any(), any())
    } answers { callOriginal() }
  }

  @After
  fun tearDown() {
    unmockkStatic(JobIntentService::class)
    runBlocking { store.close() }
  }

  @Test
  fun aStoredColourIsReadOnTheMainThreadWithoutAWait() {
    runBlocking { repository.setPackageColor(PKG, COLOR) }
    ShadowLog.clear()

    val colour = repository.getPackageColorSync(PKG)

    assertThat(colour).isEqualTo(COLOR)
    assertThat(ShadowLog.getLogsForTag(MAIN_THREAD_WAIT_TAG)).isEmpty()
    assertThat(shadowOf(jobScheduler).getPendingJob(AppColorExtractorJob.JOB_ID)).isNull()
    verify(exactly = 0) {
      JobIntentService.enqueueWork(any<Context>(), any<Class<*>>(), any(), any())
    }
  }

  @Test
  fun aMissingColourReturnsNoColourAndStartsTheExtractionOnce() {
    runBlocking { diskStore.snapshot.first { it != null } }
    ShadowLog.clear()

    val colour = repository.getPackageColorSync(OTHER_PKG)

    assertThat(colour).isEqualTo(Constants.NO_COLOR)
    assertThat(ShadowLog.getLogsForTag(MAIN_THREAD_WAIT_TAG)).isEmpty()
    val job = shadowOf(jobScheduler).getPendingJob(AppColorExtractorJob.JOB_ID)
    assertThat(job).isNotNull()
    assertThat(job!!.service.className).isEqualTo(AppColorExtractorJob::class.java.name)
    verify(exactly = 1) {
      JobIntentService.enqueueWork(
        any<Context>(),
        AppColorExtractorJob::class.java,
        AppColorExtractorJob.JOB_ID,
        match<Intent> { it.getStringExtra(Constants.EXTRA_PACKAGE_NAME) == OTHER_PKG },
      )
    }
  }

  @Test
  fun theSyncReadReturnsTheSameStoredColourAsTheSuspendRead() {
    runBlocking { repository.setPackageColor(PKG, COLOR) }

    val sync = repository.getPackageColorSync(PKG)
    val suspended = runBlocking { repository.getPackageColor(PKG) }

    assertThat(sync).isEqualTo(suspended)
    assertThat(sync).isEqualTo(COLOR)
  }

  private companion object {
    const val PKG = "com.example.reader"
    const val OTHER_PKG = "com.example.unknown"
    const val COLOR = -16738680
  }
}
