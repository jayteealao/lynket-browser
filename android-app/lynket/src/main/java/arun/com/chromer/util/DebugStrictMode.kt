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

import android.content.Context
import android.os.StrictMode
import arun.com.chromer.BuildConfig
import arun.com.chromer.bubbles.webheads.WebHeadStartFaults
import java.io.File

/**
 * StrictMode disk checks on the main thread, for debug builds and only on request:
 * `adb shell run-as arun.com.chromer.debug touch files/debug-faults/strict-mode`, then restart
 * the app. Violations go to logcat under the `StrictMode` tag.
 */
object DebugStrictMode {
  const val FLAG_FILE = "strict-mode"

  fun installIfRequested(context: Context) {
    if (!BuildConfig.DEBUG) return
    if (!File(File(context.filesDir, WebHeadStartFaults.FAULT_DIR), FLAG_FILE).exists()) return
    StrictMode.setThreadPolicy(
      StrictMode.ThreadPolicy.Builder()
        .detectDiskReads()
        .detectDiskWrites()
        .penaltyLog()
        .build(),
    )
  }
}
