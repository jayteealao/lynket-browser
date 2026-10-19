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

package arun.com.chromer.util

import android.os.Looper
import android.util.Log
import arun.com.chromer.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Log tag of [blockingWait]; `adb logcat -s MainThreadWait:W` shows each wait with its caller. */
const val MAIN_THREAD_WAIT_TAG = "MainThreadWait"

/**
 * [runBlocking] that, in debug builds, logs a warning with the caller's stack when the main
 * thread waits. StrictMode does not report this case when [block] does its disk work on another
 * dispatcher. Logs through [Log], so a wait during `Application.onCreate` shows before Timber is
 * set up.
 */
fun <T> blockingWait(
  site: String,
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend CoroutineScope.() -> T,
): T {
  if (BuildConfig.DEBUG && Looper.getMainLooper().isCurrentThread) {
    Log.w(MAIN_THREAD_WAIT_TAG, "Main thread waits: $site", Throwable(site))
  }
  return runBlocking(context, block)
}
