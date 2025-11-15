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

// Phase 7: Converted to Kotlin
package arun.com.chromer.data.webarticle.stores

import android.app.Application
import arun.com.chromer.data.webarticle.WebArticleStore
import arun.com.chromer.data.webarticle.model.WebArticle
import `in`.arunkumarsampath.diskcache.ParcelDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache store to get/put [WebArticle] objects to disk cache.
 */
@Singleton
class WebArticleCacheStore @Inject constructor(
    application: Application
) : WebArticleStore {

    // Disk LRU cache to store articles
    private var webSiteDiskCache: ParcelDiskCache<WebArticle>? = null

    init {
        try {
            webSiteDiskCache = ParcelDiskCache.open(
                application,
                WebArticle::class.java.classLoader,
                WebArticle::class.java.name,
                DISK_CACHE_SIZE
            )
        } catch (ignored: IOException) {
            Timber.e(ignored)
        }
    }

    override suspend fun getWebArticle(url: String): WebArticle? = withContext(Dispatchers.IO) {
        try {
            webSiteDiskCache?.get(url.trim())
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun saveWebArticle(webSite: WebArticle): WebArticle = withContext(Dispatchers.IO) {
        try {
            webSiteDiskCache?.set(webSite.url, webSite)
            webSite
        } catch (e: Exception) {
            webSite
        }
    }

    companion object {
        private const val TAG = "WebArticleCacheStore"
        // Cache size, currently set at 30 MB.
        private const val DISK_CACHE_SIZE = 1024 * 1024 * 30
    }
}
