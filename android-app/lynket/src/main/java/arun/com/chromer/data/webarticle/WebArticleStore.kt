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

package arun.com.chromer.data.webarticle

import arun.com.chromer.data.webarticle.model.WebArticle

/**
 * Phase 7: Converted from Java to Kotlin
 *
 * Store interface for web articles with get and save operations.
 */
interface WebArticleStore {
    suspend fun getWebArticle(url: String): WebArticle?
    suspend fun saveWebArticle(webSite: WebArticle): WebArticle
}
