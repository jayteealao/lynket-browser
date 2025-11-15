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

// Phase 8.7: Converted from RxJava to Kotlin Flows/Coroutines

package arun.com.chromer.search.suggestion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Simple Google Suggestions API implementation using HttpURLConnection.
 * Replaces the deprecated in.arunkumarsampath.suggestions library.
 * Converted to use Kotlin Coroutines instead of RxJava.
 */
object GoogleSuggestionsApi {

  private const val GOOGLE_SUGGEST_URL = "https://suggestqueries.google.com/complete/search"

  /**
   * Fetches suggestions from Google for the given query.
   * This is a suspend function that performs network I/O on Dispatchers.IO.
   *
   * @param query The search query
   * @param maxResults Maximum number of suggestions to return (default 5)
   * @return List of suggestion strings
   */
  suspend fun getSuggestions(query: String, maxResults: Int = 5): List<String> =
    withContext(Dispatchers.IO) {
      if (query.isBlank()) {
        return@withContext emptyList()
      }

      val encodedQuery = URLEncoder.encode(query, "UTF-8")
      val urlString = "$GOOGLE_SUGGEST_URL?client=firefox&q=$encodedQuery"

      var connection: HttpURLConnection? = null
      try {
        val url = URL(urlString)
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
          return@withContext emptyList()
        }

        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val response = reader.use { it.readText() }

        // Parse JSON response - format is: ["query", ["suggestion1", "suggestion2", ...]]
        val jsonArray = JSONArray(response)
        if (jsonArray.length() < 2) {
          return@withContext emptyList()
        }

        val suggestions = jsonArray.getJSONArray(1)
        val results = mutableListOf<String>()

        for (i in 0 until minOf(suggestions.length(), maxResults)) {
          results.add(suggestions.getString(i))
        }

        results
      } catch (e: Exception) {
        emptyList()
      } finally {
        connection?.disconnect()
      }
    }

  /**
   * Flow transformer that fetches Google suggestions for each query string.
   * Usage: queryFlow.suggestionsFlow(maxResults)
   *
   * @param maxResults Maximum number of suggestions to return
   * @return Flow transformer that converts query strings to suggestion lists
   */
  fun Flow<String>.suggestionsFlow(maxResults: Int = 5): Flow<List<String>> {
    return this.map { query ->
      getSuggestions(query, maxResults)
    }.catch {
      emit(emptyList())
    }
  }
}
