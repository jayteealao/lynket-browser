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

/**
 * History as the link path and the search suggestions use it: look a page up, record a visit and
 * search. The History screen and the home read and change history through
 * [ModernHistoryRepository].
 */
interface HistoryRepository {
  suspend fun get(website: Website): Website?

  /** Records a visit: a new row, or one more visit and a new visit time for a stored URL. */
  suspend fun insert(website: Website): Website

  suspend fun search(text: String): List<Website>
}
