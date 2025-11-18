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

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Pair
import androidx.annotation.ColorInt
import arun.com.chromer.data.website.model.WebColor
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import arun.com.chromer.shared.Constants.NO_COLOR
import `in`.arunkumarsampath.diskcache.ParcelDiskCache
// Phase 3: PaperDB removed - using in-memory cache for colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache store to get/put [Website] objects to disk cache.
 * Phase 3: Migrated from PaperDB to in-memory cache for theme colors
 */
@Singleton
class WebsiteDiskStore @Inject constructor(
    context: Application
) : WebsiteStore {

    // Cache to store our data.
    private var webSiteDiskCache: ParcelDiskCache<Website>? = null

    // Phase 3: In-memory cache for theme colors (replaces PaperDB)
    private val colorCache = ConcurrentHashMap<String, WebColor>()

    init {
        try {
            webSiteDiskCache = ParcelDiskCache.open(
                context,
                Website::class.java.classLoader,
                "WebSiteCache",
                DISK_CACHE_SIZE
            )
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    override fun getWebsite(url: String): Flow<Website?> = flow {
        val webSite = try {
            webSiteDiskCache?.get(url.trim())
        } catch (e: Exception) {
            Timber.e(e)
            null
        }

        if (webSite == null) {
            Timber.d("Cache miss for: %s", url)
        } else {
            Timber.d("Cache hit for : %s", url)
        }

        emit(webSite)
    }.flowOn(Dispatchers.IO)

    override suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            webSiteDiskCache?.clear()
        }
    }

    override suspend fun saveWebsite(website: Website): Website? {
        return withContext(Dispatchers.IO) {
            val result = try {
                webSiteDiskCache?.set(website.url, website)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }

            if (result != null) {
                Timber.d("Put %s to cache", result.url)
            }

            result
        }
    }

    override suspend fun getWebsiteColor(url: String): WebColor {
        return withContext(Dispatchers.IO) {
            val host = Uri.parse(url).host ?: ""
            // Phase 3: Using in-memory cache instead of PaperDB
            colorCache[host] ?: WebColor(host, NO_COLOR)
        }
    }

    override suspend fun saveWebsiteColor(host: String, @ColorInt color: Int): WebColor {
        return withContext(Dispatchers.IO) {
            // Phase 3: Using in-memory cache instead of PaperDB
            val webColor = WebColor(host, color)
            colorCache[host] = webColor
            Timber.d("Saved color %d for host %s", color, host)
            webColor
        }
    }

    override fun getWebsiteIconAndColor(website: Website): Pair<Bitmap, Int> {
        return EMPTY_ICON_COLOR_PAIR
    }

    override fun getWebsiteRoundIconAndColor(website: Website): Pair<Drawable, Int> {
        return EMPTY_DRAWABLE_PAIR
    }

    override fun getWebsiteIconWithPlaceholderAndColor(website: Website): Pair<Bitmap, Int> {
        return EMPTY_ICON_COLOR_PAIR
    }

    companion object {
        val EMPTY_ICON_COLOR_PAIR: Pair<Bitmap, Int> = Pair(null, Constants.NO_COLOR)
        val EMPTY_DRAWABLE_PAIR: Pair<Drawable, Int> = Pair(null, Constants.NO_COLOR)
        // Cache size, currently set at 30 MB.
        private const val DISK_CACHE_SIZE = 1024 * 1024 * 30L
    }
}
