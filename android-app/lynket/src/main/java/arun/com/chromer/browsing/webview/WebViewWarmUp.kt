/*
 * Lynket
 *
 * Copyright (C) 2026 Arunkumar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.browsing.webview

import android.app.Application
import android.content.Context
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Starts the system page engine; a seam so tests can replace the platform call. */
interface WebViewStarter {
  fun startUp(
    context: Context,
    config: WebViewStartUpConfig,
    receiver: WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException>,
  )
}

/** The platform start of the page engine, through the stable androidx.webkit start-up call. */
class PlatformWebViewStarter @Inject constructor() : WebViewStarter {
  override fun startUp(
    context: Context,
    config: WebViewStartUpConfig,
    receiver: WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException>,
  ) {
    WebViewCompat.startUpWebView(context, config, receiver)
  }
}

/**
 * Starts the page engine early, once per process, so a page that opens in a web view or a bubble
 * shows sooner. It runs only the parts of the start that can run in the background, so it never
 * blocks the screen; it loads no page and takes no address. When it fails, the page opens as
 * before.
 */
@Singleton
class WebViewWarmUp @Inject constructor(
  private val application: Application,
  private val starter: WebViewStarter,
) {
  private val started = AtomicBoolean(false)

  /** Whether [warmUp] already ran in this process; a later call does nothing. */
  val isStarted: Boolean
    get() = started.get()

  private val backgroundExecutor = Executor { task ->
    Dispatchers.IO.asExecutor().execute {
      runCatching { task.run() }.onFailure { Timber.w(it, "WebView warm-up failed") }
    }
  }

  private val receiver = object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
    override fun onResult(result: WebViewStartUpResult) {
      Timber.d("WebView warm-up done, main thread %s ms", result.totalTimeInUiThreadMillis ?: "unknown")
    }

    override fun onError(error: WebViewStartupException) {
      Timber.w(error, "WebView warm-up failed")
    }
  }

  fun warmUp() {
    if (!started.compareAndSet(false, true)) return
    try {
      val config = WebViewStartUpConfig.Builder(backgroundExecutor)
        .setShouldRunUiThreadStartUpTasks(false)
        .build()
      starter.startUp(application, config, receiver)
    } catch (e: Exception) {
      Timber.w(e, "WebView warm-up failed")
    }
  }
}
