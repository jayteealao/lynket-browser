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

package arun.com.chromer.search.provider

import android.net.Uri
import android.util.Patterns.WEB_URL
import androidx.core.net.toUri
import arun.com.chromer.settings.RxPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SearchProvider(
  val name: String,
  val iconUri: Uri,
  val searchUrlPrefix: String
) {
  fun getSearchUrl(text: String): String {
    return if (WEB_URL.matcher(text).matches()) {
      when {
        !text.lowercase(Locale.getDefault()).matches("^\\w+://.*".toRegex()) -> "http://$text"
        else -> text
      }
    } else searchUrlPrefix + text.replace(" ", "+")
  }
}

@Singleton
class SearchProviders
@Inject
constructor(
  rxPreferences: RxPreferences
) {

  // Application-level scope for this singleton
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val availableProviders: List<SearchProvider> = listOf(
    GOOGLE_SEARCH_PROVIDER,
    SearchProvider(
      name = DUCKDUCKGO,
      iconUri = "https://lh3.googleusercontent.com/8GiPaoaCopqI1AiBajxwx91ndKDeeAI-p2w7hDZlG7yi6KoXJ5bzWA0VteFpTAB5uhM=s192-rw".toUri(),
      searchUrlPrefix = "https://duckduckgo.com/?q="
    ),
    SearchProvider(
      name = BING,
      iconUri = "https://lh3.googleusercontent.com/0aRIOVqPu3KKUh6FFSmo1jkQMIeTqgGvHNo4mHl_NUzJxGGd2m0jaUoRdhGcgaa-ug=s192-rw".toUri(),
      searchUrlPrefix = "https://www.bing.com/search?q="
    ),
    SearchProvider(
      name = QWANT,
      iconUri = "https://lh3.googleusercontent.com/gZM93E0coPblwJysaGbAVgTRXPld0ZDRtrbmclDqWWrPJLKIjyVB9XKqOX8OM9_3GJI=s192-rw".toUri(),
      searchUrlPrefix = "https://www.qwant.com/?q="
    ),
    SearchProvider(
      name = ECOSIA,
      iconUri = "https://cdn-static.ecosia.org/assets/images/png/apple-touch-icon.png".toUri(),
      searchUrlPrefix = "https://www.ecosia.org/search?q="
    )
  )

  val selectedProvider: Flow<SearchProvider> = rxPreferences
    .searchEngine
    .observe()
    .map { selectedEngine ->
      availableProviders.find { it.name == selectedEngine } ?: GOOGLE_SEARCH_PROVIDER
    }
    .flowOn(Dispatchers.Default)
    .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

  companion object {
    const val GOOGLE = "Google"
    const val DUCKDUCKGO = "Duck Duck Go"
    const val BING = "Bing"
    const val QWANT = "Qwant"
    const val ECOSIA = "Ecosia"

    val GOOGLE_SEARCH_PROVIDER = SearchProvider(
      name = GOOGLE,
      iconUri = "https://cdn3.iconfinder.com/data/icons/google-suits-1/32/1_google_search_logo_engine_service_suits-256.png".toUri(),
      searchUrlPrefix = "https://www.google.com/search?q="
    )
  }
}
