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

import arun.com.chromer.data.webarticle.WebArticleStore
import arun.com.chromer.data.webarticle.model.WebArticle
import arun.com.chromer.util.parser.RxParser
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network store which freshly parses website data for a given URL.
 */
@Singleton
class WebArticleNetworkStore @Inject constructor() : WebArticleStore {

    override fun getWebArticle(url: String): Observable<WebArticle> {
        return RxParser.parseArticle(url)
            .flatMap { urlArticlePair ->
                if (urlArticlePair.second != null) {
                    Observable.just(WebArticle.fromArticle(urlArticlePair.second))
                } else {
                    Observable.just(null)
                }
            }
            .map { webArticle ->
                if (webArticle != null) {
                    // Clean up all the empty strings.
                    val rawElements = webArticle.elements
                    if (rawElements != null) {
                        val iterator = rawElements.iterator()
                        while (iterator.hasNext()) {
                            val element = iterator.next()
                            if (element.text().isEmpty()) {
                                iterator.remove()
                            }
                        }
                    }
                }
                webArticle
            }
    }

    override fun saveWebArticle(webSite: WebArticle): Observable<WebArticle> {
        return Observable.empty()
    }
}
