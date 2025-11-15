// Phase 8: Converted from RxJava to Kotlin Flows/Coroutines
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

package arun.com.chromer.data.website

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Pair
import arun.com.chromer.data.website.model.WebColor
import arun.com.chromer.data.website.model.Website
import kotlinx.coroutines.flow.Flow

/**
 * Interface definition of Website repository which is responsible for providing
 * [Website] instances containing useful website information.
 * Will use a combination of disk cache and network parsing to provide requested website's data.
 */
interface WebsiteRepository {
  fun getWebsite(url: String): Flow<Website>

  fun getWebsiteReadOnly(url: String): Flow<Website>

  fun getWebsiteColorSync(url: String): Int

  suspend fun saveWebColor(url: String): WebColor

  suspend fun clearCache()

  fun getWebsiteIconAndColor(website: Website): Pair<Bitmap, Int>

  fun getWebsiteRoundIconAndColor(website: Website): Pair<Drawable, Int>

  fun getWebsiteIconWithPlaceholderAndColor(website: Website): Pair<Bitmap, Int>
}
