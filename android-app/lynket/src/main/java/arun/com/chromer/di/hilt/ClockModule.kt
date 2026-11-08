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

package arun.com.chromer.di.hilt

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * The one source of "now" in the app. Every class that needs the current time takes this [Clock]
 * instead of reading the system time itself, so a test can replace it with a fixed clock.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

  @Provides
  @Singleton
  fun clock(): Clock = Clock.System
}
