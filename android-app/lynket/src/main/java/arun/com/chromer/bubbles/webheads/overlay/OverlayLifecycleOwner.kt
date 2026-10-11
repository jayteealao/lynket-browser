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

package arun.com.chromer.bubbles.webheads.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Lifecycle, saved state and view model store for Compose views in a service's overlay windows.
 * An activity gives Compose these three owners; a service does not, so the service creates one
 * of these, [create]s it in `onCreate` and [destroy]s it in `onDestroy`.
 */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
  private val registry = LifecycleRegistry(this)
  private val savedStateController = SavedStateRegistryController.create(this)

  override val lifecycle: Lifecycle get() = registry
  override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
  override val viewModelStore: ViewModelStore = ViewModelStore()

  fun create() {
    savedStateController.performAttach()
    savedStateController.performRestore(null)
    registry.currentState = Lifecycle.State.RESUMED
  }

  fun destroy() {
    if (registry.currentState == Lifecycle.State.INITIALIZED) return
    registry.currentState = Lifecycle.State.DESTROYED
    viewModelStore.clear()
  }
}
