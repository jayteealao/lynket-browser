// Phase 8: Evaluated - Keep for MaterialSearchView compatibility
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

package arun.com.chromer.util.epoxy

import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Flow-based extension functions for Epoxy controllers.
 *
 * Converted from RxJava to Kotlin Flow in Phase 8.9.
 * MaterialSearchView is the primary consumer of these extensions.
 */

/**
 * Creates a Flow that emits lists of EpoxyModels when the controller intercepts them.
 * Used by MaterialSearchView for observing suggestion model changes.
 */
fun EpoxyController.intercepts(): Flow<List<EpoxyModel<*>>> = callbackFlow {
  val interceptor = EpoxyController.Interceptor { models ->
    trySend(models)
  }.also(::addInterceptor)
  awaitClose { removeInterceptor(interceptor) }
}
