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

import androidx.paging.PagingData
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by arunk on 03-03-2017.
 */
@Singleton
class DefaultHistoryRepository
@Inject
internal constructor(
  private val historyStore: HistoryStore,
  private val preferences: Preferences
) : HistoryRepository {

  override suspend fun get(website: Website): Website? {
    val saved = historyStore.get(website)
    if (saved == null) {
      Timber.d("History miss for: %s", website.url)
    } else {
      Timber.d("History hit for : %s", website.url)
    }
    return saved
  }

  override suspend fun insert(website: Website): Website {
    return if (preferences.historyDisabled()) {
      website
    } else {
      val result = historyStore.insert(website)
      if (result != null) {
        Timber.d("Added %s to history", result.url)
      } else {
        Timber.e("%s Did not add to history", website.url)
      }
      result ?: website
    }
  }

  override suspend fun update(website: Website): Website {
    return if (preferences.historyDisabled()) {
      website
    } else {
      val saved = historyStore.update(website)
      if (saved != null) {
        Timber.d("Updated %s in history table", saved.url)
      }
      saved ?: website
    }
  }

  override fun pagedHistory(): Flow<PagingData<Website>> = historyStore.pagedHistory()

  override suspend fun delete(website: Website) = historyStore.delete(website)

  override suspend fun exists(website: Website) = historyStore.exists(website)

  override suspend fun deleteAll() = historyStore.deleteAll()

  override fun recents() = historyStore.recents()

  override suspend fun search(text: String) = historyStore.search(text)
}
