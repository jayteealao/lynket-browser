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

package arun.com.chromer.ui.navigation

import androidx.annotation.CheckResult
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The one-argument form of `androidx.lifecycle.compose.dropUnlessResumed`: the returned action
 * runs [block] only while [lifecycleOwner] is resumed, and drops the call otherwise.
 *
 * The navigation display holds every screen below resumed while a screen transition runs, so a
 * second tap during a transition (on the same control or on another one) is dropped and pushes no
 * second screen. Once the transition settles, taps work again.
 */
@CheckResult
@Composable
fun <T> dropUnlessResumedWith(
  lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
  block: (T) -> Unit,
): (T) -> Unit = { value ->
  if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(value)
}
