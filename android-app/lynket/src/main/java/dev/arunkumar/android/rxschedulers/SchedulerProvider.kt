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
package dev.arunkumar.android.rxschedulers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub class for backward compatibility during RxJava removal.
 * This class provides coroutine dispatchers instead of RxJava schedulers.
 * TODO: Convert remaining RxJava code to coroutines and remove this class.
 */
@Singleton
class SchedulerProvider @Inject constructor() {
  val ui: CoroutineDispatcher = Dispatchers.Main
  val io: CoroutineDispatcher = Dispatchers.IO
  val computation: CoroutineDispatcher = Dispatchers.Default
  val pool: CoroutineDispatcher = Dispatchers.IO
}
