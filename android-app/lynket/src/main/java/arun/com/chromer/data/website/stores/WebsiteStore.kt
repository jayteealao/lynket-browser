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

package arun.com.chromer.data.website.stores

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Pair
import androidx.annotation.ColorInt
import arun.com.chromer.data.website.model.WebColor
import arun.com.chromer.data.website.model.Website
import kotlinx.coroutines.flow.Flow

/**
 * Store interface for website data and metadata.
 */
interface WebsiteStore {
    fun getWebsite(url: String): Flow<Website?>

    suspend fun clearCache()

    suspend fun saveWebsite(website: Website): Website?

    suspend fun getWebsiteColor(url: String): WebColor

    suspend fun saveWebsiteColor(host: String, @ColorInt color: Int): WebColor

    fun getWebsiteIconAndColor(website: Website): Pair<Bitmap, Int>

    fun getWebsiteRoundIconAndColor(website: Website): Pair<Drawable, Int>

    fun getWebsiteIconWithPlaceholderAndColor(website: Website): Pair<Bitmap, Int>
}
