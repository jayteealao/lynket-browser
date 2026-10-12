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

package arun.com.chromer.ui.history

import androidx.compose.ui.platform.AndroidUiDispatcher
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.coroutines.ContinuationInterceptor

/**
 * Paged lists collect on Compose's shared main dispatcher, created once per Robolectric sandbox.
 * When an earlier test in the same sandbox ends with work scheduled on it, Robolectric clears the
 * main looper but the dispatcher still believes a dispatch is on its way, so nothing it is given
 * afterwards ever runs and the list stays empty. This drops that leftover state before each test.
 */
class UiDispatcherResetRule : TestWatcher() {
  override fun starting(description: Description) {
    val dispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor] as AndroidUiDispatcher
    val type = AndroidUiDispatcher::class.java
    val lock = type.getDeclaredField("lock").apply { isAccessible = true }.get(dispatcher)
    synchronized(lock) {
      (type.getDeclaredField("toRunTrampolined").apply { isAccessible = true }.get(dispatcher) as MutableCollection<*>).clear()
      (type.getDeclaredField("toRunOnFrame").apply { isAccessible = true }.get(dispatcher) as MutableList<*>).clear()
      type.getDeclaredField("scheduledTrampolineDispatch").apply { isAccessible = true }.setBoolean(dispatcher, false)
      type.getDeclaredField("scheduledFrameDispatch").apply { isAccessible = true }.setBoolean(dispatcher, false)
    }
  }
}
