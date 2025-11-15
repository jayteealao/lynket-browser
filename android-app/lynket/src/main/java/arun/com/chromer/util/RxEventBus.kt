// Phase 8: Converted from RxJava to Kotlin Flow
/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance

/**
 * A simple event bus built with Kotlin Flow (converted from RxJava)
 *
 * This class provides backward compatibility for code still using RxEventBus.
 * New code should use the modern EventBus from arun.com.chromer.util.events package.
 */
class RxEventBus {

  private val _events = MutableSharedFlow<Any>(
    replay = 0, // Don't replay old events to new subscribers
    extraBufferCapacity = 64, // Buffer up to 64 events
    onBufferOverflow = BufferOverflow.DROP_OLDEST // Drop oldest if buffer full
  )

  /**
   * Posts an object (usually an Event) to the bus
   *
   * Note: This is non-blocking. For suspend version, use emit()
   */
  fun post(event: Any) {
    _events.tryEmit(event)
  }

  /**
   * Suspend version of post for use in coroutines
   */
  suspend fun emit(event: Any) {
    _events.emit(event)
  }

  /**
   * Flow that will emit everything posted to the event bus.
   */
  fun events(): Flow<Any> = _events.asSharedFlow()

  /**
   * Flow that only emits events of a specific class.
   * Use this if you only want to subscribe to one type of events.
   */
  inline fun <reified T> filteredEvents(): Flow<T> = events().filterIsInstance<T>()
}
