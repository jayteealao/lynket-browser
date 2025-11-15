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

// Phase 8.7: Converted from RxJava to Kotlin Flows/Coroutines

package arun.com.chromer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Represents the result of an operation.
 * Useful for wrapping API calls and database operations.
 */
sealed class Result<T> {
  data class Success<T>(val data: T?) : Result<T>()
  class Loading<T> : Result<T>()
  class Idle<T> : Result<T>()
  data class Failure<T>(val throwable: Throwable) : Result<T>()

  companion object {
    /**
     * Transforms a Flow to emit Result states (Loading, Success, or Failure).
     * Usage: sourceFlow.asResult()
     */
    fun <T> Flow<T>.asResult(): Flow<Result<T>> {
      return this
        .map { Success(it) as Result<T> }
        .onStart { emit(Loading()) }
        .catch { emit(Failure(it)) }
    }
  }
}
