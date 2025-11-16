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

package arun.com.chromer.browsing.article

import android.net.Uri
import androidx.annotation.MainThread
import arun.com.chromer.data.webarticle.WebArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preloads web articles in the background using Kotlin Coroutines.
 */
@Singleton
class ArticlePreloader @Inject constructor(
    private val webArticleRepository: WebArticleRepository
) {
    // Use a supervisor job so failures don't cancel other preload operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Preload article with callback-based API for backward compatibility.
     *
     * @param uri The URI of the article to preload
     * @param listener Callback invoked on the main thread when preload completes
     */
    fun preloadArticle(uri: Uri, listener: ArticlePreloadListener?) {
        scope.launch {
            val success = try {
                webArticleRepository.getWebArticle(uri.toString())
                true
            } catch (e: Exception) {
                false
            }

            // Invoke listener on main thread
            withContext(Dispatchers.Main) {
                listener?.onComplete(success)
            }
        }
    }

    /**
     * Preload article using suspend function API.
     *
     * @param uri The URI of the article to preload
     * @return true if preload was successful, false otherwise
     */
    suspend fun preloadArticleSuspend(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            webArticleRepository.getWebArticle(uri.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Listener for article preload completion.
     * Marked as 'fun interface' to enable SAM conversion for lambda usage.
     */
    fun interface ArticlePreloadListener {
        /**
         * Called when preload process has been completed. Can rely on [success] to know
         * whether it was success or not.
         *
         * @param success true if success, else false.
         */
        @MainThread
        fun onComplete(success: Boolean)
    }
}
