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
import android.net.Uri
import android.util.Pair
import arun.com.chromer.data.common.qualifiers.Disk
import arun.com.chromer.data.common.qualifiers.Network
import arun.com.chromer.data.history.HistoryRepository
import arun.com.chromer.data.website.model.WebColor
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.data.website.stores.WebsiteStore
import arun.com.chromer.shared.Constants.NO_COLOR
import arun.com.chromer.util.blockingWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Website repository implementation for managing and providing website data.
 */
@Singleton
class DefaultWebsiteRepository
@Inject
internal constructor(
  @param:Disk private val cacheStore: WebsiteStore,
  @param:Network private val webNetworkStore: WebsiteStore,
  private val historyRepository: HistoryRepository
) : WebsiteRepository {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun getWebsite(url: String): Flow<Website> = lookUpWebsite(url, incognito = false).map { it.website }

  override fun lookUpWebsite(url: String, incognito: Boolean): Flow<WebsiteLookup> = flow {
    // Try cache first
    val cacheResult = cacheStore.getWebsite(url).filterNotNull().firstOrNull()
    if (cacheResult != null) {
      if (!incognito) scope.launch { historyRepository.insert(cacheResult) }
      emit(WebsiteLookup(cacheResult, fetchFailed = false))
      return@flow
    }

    // Try history
    val historyResult = historyRepository.get(Website(url))
    if (historyResult != null) {
      if (!incognito) scope.launch { historyRepository.insert(historyResult) }
      emit(WebsiteLookup(historyResult, fetchFailed = false))
      return@flow
    }

    // Try remote
    val remoteResult = webNetworkStore.getWebsite(url).filterNotNull().firstOrNull()
    if (remoteResult != null) {
      if (!incognito) {
        scope.launch {
          cacheStore.saveWebsite(remoteResult)
          historyRepository.insert(remoteResult)
        }
      }
      emit(WebsiteLookup(remoteResult, fetchFailed = false))
      return@flow
    }

    // Nothing found: the page could not be fetched.
    emit(WebsiteLookup(Website(url), fetchFailed = true))
  }.catch { throwable ->
    Timber.e(throwable)
    emit(WebsiteLookup(Website(url), fetchFailed = true))
  }.flowOn(Dispatchers.IO)

  override fun getWebsiteWithoutHistory(url: String): Flow<Website> =
    lookup(url) { webSite -> scope.launch { cacheStore.saveWebsite(webSite) } }

  override fun getWebsiteWithoutSaving(url: String): Flow<Website> =
    lookUpWebsite(url, incognito = true).map { it.website }

  /**
   * Reads the website from the cache, then history, then the network, falling back to a bare
   * [Website]. Only [onFetched] may store anything, and it sees only a network result.
   */
  private fun lookup(url: String, onFetched: (Website) -> Unit): Flow<Website> = flow {
    val website = cacheStore.getWebsite(url).filterNotNull().firstOrNull()
      ?: historyRepository.get(Website(url))
      ?: webNetworkStore.getWebsite(url).filterNotNull().firstOrNull()?.also(onFetched)
      ?: Website(url)
    emit(website)
  }.catch { throwable ->
    Timber.e(throwable)
    emit(Website(url))
  }.flowOn(Dispatchers.IO)

  override fun getWebsiteColorSync(url: String): Int {
    return blockingWait("WebsiteRepository.getWebsiteColorSync") {
      val webColor = cacheStore.getWebsiteColor(url)
      if (webColor.color == NO_COLOR) {
        scope.launch {
          saveWebColor(url)
        }
      }
      webColor.color
    }
  }

  override suspend fun saveWebColor(url: String): WebColor {
    val webSite = getWebsiteWithoutHistory(url).first()
    val host = Uri.parse(webSite.url).host ?: ""

    return if (webSite.themeColor() != NO_COLOR) {
      val color = webSite.themeColor()
      cacheStore.saveWebsiteColor(host, color)
    } else {
      val color = getWebsiteIconAndColor(webSite).second
      if (color != NO_COLOR) {
        cacheStore.saveWebsiteColor(host, color)
      } else {
        WebColor("", NO_COLOR)
      }
    }
  }

  override suspend fun clearCache() {
    cacheStore.clearCache()
  }

  override fun getWebsiteIconAndColor(website: Website): Pair<Bitmap, Int> {
    return webNetworkStore.getWebsiteIconAndColor(website)
  }

  override fun getWebsiteRoundIconAndColor(website: Website): Pair<Drawable, Int> {
    return webNetworkStore.getWebsiteRoundIconAndColor(website)
  }

  override fun getWebsiteIconWithPlaceholderAndColor(website: Website): Pair<Bitmap, Int> {
    return webNetworkStore.getWebsiteIconWithPlaceholderAndColor(website)
  }
}
