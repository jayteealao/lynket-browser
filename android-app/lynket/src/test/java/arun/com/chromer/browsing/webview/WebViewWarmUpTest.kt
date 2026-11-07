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

package arun.com.chromer.browsing.webview

import android.app.Application
import android.content.Context
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The early start of the page engine runs once, only its background parts, and no failure of it
 * reaches the caller or the thread that runs it.
 */
class WebViewWarmUpTest {
  private class RecordingStarter : WebViewStarter {
    val configs = mutableListOf<WebViewStartUpConfig>()
    val receivers = mutableListOf<WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException>>()
    var throwOnStart = false

    override fun startUp(
      context: Context,
      config: WebViewStartUpConfig,
      receiver: WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException>,
    ) {
      configs += config
      receivers += receiver
      if (throwOnStart) throw IllegalStateException("no WebView provider")
    }
  }

  private val starter = RecordingStarter()
  private val warmUp = WebViewWarmUp(mockk<Application>(relaxed = true), starter)
  private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

  @After
  fun restoreHandler() {
    Thread.setDefaultUncaughtExceptionHandler(previousHandler)
  }

  @Test
  fun theFirstCallStartsTheEngineWithoutTheMainThreadTasks() {
    warmUp.warmUp()

    assertThat(starter.configs).hasSize(1)
    assertThat(starter.configs.single().shouldRunUiThreadStartUpTasks()).isFalse()
  }

  @Test
  fun laterCallsStartNothingMore() {
    warmUp.warmUp()
    warmUp.warmUp()
    warmUp.warmUp()

    assertThat(starter.configs).hasSize(1)
  }

  @Test
  fun aStarterThatThrowsDoesNotReachTheCaller() {
    starter.throwOnStart = true

    warmUp.warmUp()

    assertThat(starter.configs).hasSize(1)
  }

  @Test
  fun aBackgroundTaskThatThrowsDoesNotReachTheThreadsHandler() {
    val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
    Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
    warmUp.warmUp()
    val executor = starter.configs.single().backgroundExecutor
    val ran = CountDownLatch(1)
    val after = CountDownLatch(1)

    executor.execute {
      ran.countDown()
      throw IllegalStateException("WebView provider failed to load")
    }
    assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue()
    // A second task runs after the first one ended, on the same pool, so the first one's
    // exception had its chance to reach the handler.
    executor.execute { after.countDown() }
    assertThat(after.await(5, TimeUnit.SECONDS)).isTrue()
    Thread.sleep(200)

    assertThat(uncaught).isEmpty()
  }

  @Test
  fun anErrorOrAResultWithNoDetailsIsOnlyLogged() {
    warmUp.warmUp()
    val receiver = starter.receivers.single()
    val result = mockk<WebViewStartUpResult> {
      every { totalTimeInUiThreadMillis } returns null
      every { maxTimePerTaskInUiThreadMillis } returns null
      every { uiThreadBlockingStartUpLocations } returns null
    }

    receiver.onError(WebViewStartupException("x"))
    receiver.onResult(result)
  }
}
