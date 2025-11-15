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

// Phase 7: Converted to Kotlin
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
import rx.Observable
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

    override fun getWebsite(url: String): Observable<Website> {
        return Observable.fromCallable {
            try {
                webSiteDiskCache?.get(url.trim())
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }.doOnNext { webSite ->
            if (webSite == null) {
                Timber.d("Cache miss for: %s", url)
            } else {
                Timber.d("Cache hit for : %s", url)
            }
        }
    }

    override fun clearCache(): Observable<Void> {
        return Observable.fromCallable {
            webSiteDiskCache?.clear()
            null
        }
    }

    override fun saveWebsite(website: Website): Observable<Website> {
        return Observable.fromCallable {
            try {
                webSiteDiskCache?.set(website.url, website)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }.doOnNext { webSite1 ->
            if (webSite1 != null) {
                Timber.d("Put %s to cache", webSite1.url)
            }
        }
    }

    override fun getWebsiteColor(url: String): Observable<WebColor> {
        return Observable.fromCallable {
            try {
                getBook().read<WebColor>(Uri.parse(url).host)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }.map { webColor ->
            webColor ?: WebColor(Uri.parse(url).host, NO_COLOR)
        }
    }

    override fun saveWebsiteColor(host: String, @ColorInt color: Int): Observable<WebColor> {
        return Observable.fromCallable {
            try {
                getBook().write(host, WebColor(host, color)).read<WebColor>(host)
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
        private const val DISK_CACHE_SIZE = 1024 * 1024 * 30
        private const val THEME_COLOR_BOOK = "THEME_COLOR_BOOK"
    }
}
