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

package arun.com.chromer.bubbles.webheads

import android.content.Context
import arun.com.chromer.BuildConfig
import java.io.File

/**
 * A debug-only switch that makes the web head service refuse to start, so device checks can see
 * the fallback to a tab. It is on while `files/debug-faults/webhead-start-fail` exists; release
 * builds never read it.
 */
object WebHeadStartFaults {
  const val FAULT_DIR = "debug-faults"
  const val START_FAIL_FILE = "webhead-start-fail"

  fun isForced(context: Context): Boolean =
    BuildConfig.DEBUG && File(File(context.filesDir, FAULT_DIR), START_FAIL_FILE).exists()
}
