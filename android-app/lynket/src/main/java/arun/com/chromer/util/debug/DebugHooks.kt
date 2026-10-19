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

package arun.com.chromer.util.debug

import android.content.Context
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Optional

/**
 * Switches the device checks turn on in debug builds: StrictMode, a refused web head start and a
 * slow per-app list. The debug build binds a file-based implementation; every other build has
 * none, so [of] gives [None] and release code never reads the switch files.
 */
interface DebugHooks {
  /** Turns on StrictMode disk checks when the check asked for them. */
  fun installStrictModeIfRequested(context: Context) {}

  /** True when the web head service start must be refused. */
  fun webHeadStartRefused(context: Context): Boolean = false

  /** A forced delay of the per-app list in milliseconds, 0 for none. */
  fun perAppLoadDelayMs(context: Context): Long = 0

  /** No switch is on. */
  object None : DebugHooks

  companion object {
    fun of(bound: Optional<DebugHooks>): DebugHooks = bound.orElse(None)

    /** The hooks before Hilt has injected the application (for the first lines of its start). */
    fun from(context: Context): DebugHooks =
      of(EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).debugHooks())
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface Access {
    fun debugHooks(): Optional<DebugHooks>
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugHooksModule {
  @BindsOptionalOf
  abstract fun debugHooks(): DebugHooks
}
