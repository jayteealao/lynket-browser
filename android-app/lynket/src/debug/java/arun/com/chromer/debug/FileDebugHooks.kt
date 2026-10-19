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

package arun.com.chromer.debug

import android.content.Context
import android.os.StrictMode
import arun.com.chromer.util.debug.DebugHooks
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The debug build's switches, each on while its file exists in `files/debug-faults/`:
 *
 * - `strict-mode`: StrictMode disk checks on the main thread from the next start, logged under
 *   the `StrictMode` tag (`adb shell run-as arun.com.chromer.debug touch files/debug-faults/strict-mode`).
 * - `webhead-start-fail`: the web head service start is refused, so a check sees the fallback to a tab.
 * - `per-app-slow-load`: each per-app list load waits the number of milliseconds in the file
 *   (3000 when it is empty), so a check sees the loading skeleton.
 */
@Singleton
class FileDebugHooks @Inject constructor() : DebugHooks {

  override fun installStrictModeIfRequested(context: Context) {
    if (!switchFile(context, STRICT_MODE_FILE).exists()) return
    StrictMode.setThreadPolicy(
      StrictMode.ThreadPolicy.Builder()
        .detectDiskReads()
        .detectDiskWrites()
        .penaltyLog()
        .build(),
    )
  }

  override fun webHeadStartRefused(context: Context): Boolean =
    switchFile(context, START_FAIL_FILE).exists()

  override fun perAppLoadDelayMs(context: Context): Long {
    val file = switchFile(context, SLOW_LOAD_FILE)
    if (!file.exists()) return 0
    return runCatching { file.readText().trim().toLong() }.getOrDefault(DEFAULT_DELAY_MS).coerceIn(0, 30_000)
  }

  companion object {
    const val FAULT_DIR = "debug-faults"
    const val STRICT_MODE_FILE = "strict-mode"
    const val START_FAIL_FILE = "webhead-start-fail"
    const val SLOW_LOAD_FILE = "per-app-slow-load"
    private const val DEFAULT_DELAY_MS = 3_000L

    fun switchFile(context: Context, name: String) = File(File(context.filesDir, FAULT_DIR), name)
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FileDebugHooksModule {
  @Binds
  abstract fun debugHooks(hooks: FileDebugHooks): DebugHooks
}
