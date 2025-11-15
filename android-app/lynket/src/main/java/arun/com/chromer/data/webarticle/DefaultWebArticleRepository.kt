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

package arun.com.chromer.data.webarticle

import arun.com.chromer.data.common.qualifiers.Disk
import arun.com.chromer.data.common.qualifiers.Network
import arun.com.chromer.data.webarticle.model.WebArticle
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7: Converted from Java to Kotlin
 *
 * Website repository implementation for managing and providing website data.
 * Uses network and cache stores with fallback strategy.
 */
@Singleton
class DefaultWebArticleRepository @Inject constructor(
    @Network private val articleNetworkStore: WebArticleStore,
    @Disk private val articleCacheStore: WebArticleStore
) : WebArticleRepository {

    override fun getWebArticle(url: String): Observable<WebArticle> {
        return articleCacheStore.getWebArticle(url)
            .flatMap { webArticle ->
                if (webArticle == null) {
                    Timber.d("Cache miss for %s", url)
                    articleNetworkStore.getWebArticle(url)
                        .flatMap { networkWebArticle ->
                            if (networkWebArticle != null) {
                                articleCacheStore.saveWebArticle(networkWebArticle)
                            } else {
                                Observable.just(null)
                            }
                        }
                } else {
                    Timber.d("Cache hit for %s", url)
                    Observable.just(webArticle)
                }
            }
    }
}
