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
import arun.com.chromer.data.common.BookStore
import arun.com.chromer.data.website.model.WebColor
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import arun.com.chromer.shared.Constants.NO_COLOR
import `in`.arunkumarsampath.diskcache.ParcelDiskCache
import io.paperdb.Book
import io.paperdb.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache store to get/put [Website] objects to disk cache.
 */
@Singleton
class WebsiteDiskStore @Inject constructor(
    context: Application
) : WebsiteStore, BookStore {

    // Cache to store our data.
    private var webSiteDiskCache: ParcelDiskCache<Website>? = null

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

    override fun getBook(): Book = Paper.book(THEME_COLOR_BOOK)

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
            val webColor = try {
                getBook().read<WebColor>(host)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }

            webColor ?: WebColor(host, NO_COLOR)
        }
    }

    override suspend fun saveWebsiteColor(host: String, @ColorInt color: Int): WebColor {
        return withContext(Dispatchers.IO) {
            try {
                val webColor = WebColor(host, color)
                getBook().write(host, webColor)
                getBook().read<WebColor>(host) ?: webColor
            } catch (e: Exception) {
                Timber.e(e)
                WebColor(host, NO_COLOR)
            }
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
        private const val THEME_COLOR_BOOK = "THEME_COLOR_BOOK"
    }
}
