// Phase 8: Converted from RxJava to Kotlin Coroutines
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

package arun.com.chromer.data.history

import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import arun.com.chromer.data.website.model.Website
import kotlinx.coroutines.flow.Flow

/**
 * Created by Arunkumar on 03-03-2017.
 */
interface HistoryRepository {
  suspend fun get(website: Website): Website?

  suspend fun insert(website: Website): Website

  suspend fun update(website: Website): Website

  suspend fun delete(website: Website): Website

  suspend fun exists(website: Website): Boolean

  suspend fun deleteAll(): Int

  fun recents(): Flow<List<Website>>

  suspend fun search(text: String): List<Website>

  /**
   * Load given range specified by [limit] and [offset]
   */
  fun loadHistoryRange(limit: Int, offset: Int): List<Website>

  fun pagedHistory(): LiveData<PagedList<Website>>

  fun changes(): Flow<Int>
}
