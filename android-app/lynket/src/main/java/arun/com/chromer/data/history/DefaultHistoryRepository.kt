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

import arun.com.chromer.data.website.model.Website
import arun.com.chromer.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
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
  private val preferencesRepository: UserPreferencesRepository
) : HistoryRepository {

  /** The History off switch (stored as incognito_mode_pref). */
  private suspend fun historyOff(): Boolean = preferencesRepository.userPreferencesFlow.first().incognitoMode

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
    return if (historyOff()) website else historyStore.insert(website)
  }

  override suspend fun search(text: String) = historyStore.search(text)
}
