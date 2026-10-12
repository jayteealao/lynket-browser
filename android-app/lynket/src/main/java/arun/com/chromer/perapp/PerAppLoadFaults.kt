/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.perapp

import android.content.Context
import arun.com.chromer.BuildConfig
import java.io.File

/**
 * Debug builds only: a slower app list, so a device check can see the loading skeleton.
 * `adb shell run-as arun.com.chromer.debug sh -c 'mkdir -p files/debug-faults && echo 4000 > files/debug-faults/per-app-slow-load'`
 * delays each load by the number of milliseconds in the file (3000 when the file is empty).
 */
object PerAppLoadFaults {
  const val SLOW_LOAD_FILE = "per-app-slow-load"
  private const val DEFAULT_DELAY_MS = 3_000L

  /** The forced delay in milliseconds, 0 in release builds or without the file. */
  fun delayMs(context: Context): Long {
    if (!BuildConfig.DEBUG) return 0
    val file = File(File(context.filesDir, "debug-faults"), SLOW_LOAD_FILE)
    if (!file.exists()) return 0
    return runCatching { file.readText().trim().toLong() }.getOrDefault(DEFAULT_DELAY_MS).coerceIn(0, 30_000)
  }
}
