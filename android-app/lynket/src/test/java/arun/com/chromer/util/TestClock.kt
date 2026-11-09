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

package arun.com.chromer.util

import kotlin.time.Clock
import kotlin.time.Instant

/** A [Clock] that tells the time a test sets, and never moves on its own. */
class TestClock(var now: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
  constructor(millis: Long) : this(Instant.fromEpochMilliseconds(millis))

  override fun now(): Instant = now
}

/** Sets the time of this clock to [ms] milliseconds since the epoch. */
fun TestClock.setMillis(ms: Long) {
  now = Instant.fromEpochMilliseconds(ms)
}
