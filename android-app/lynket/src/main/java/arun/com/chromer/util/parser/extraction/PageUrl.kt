/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), class urls.CruxURL and
 * Redirectors. Copyright Chimbori. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: translated to Kotlin.
 */
/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
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

package arun.com.chromer.util.parser.extraction

import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * A web address, checked by its file name for whether it is likely an article or a video, image,
 * archive or other file. [resolveRedirects] unwraps the interstitial redirect pages of Facebook and
 * Google to the address they point to.
 */
class PageUrl private constructor(private var uri: URI) {
  private val fileName: String

  init {
    val path: String? = uri.path
    fileName = if (path != null && path.isNotEmpty()) path.substring(path.lastIndexOf('/') + 1) else ""
  }

  val isAdImage: Boolean
    get() = countMatches(uri.toString(), "ad") >= 2

  val isLikelyArticle: Boolean
    get() = !isLikelyBinaryDocument &&
      !isLikelyExecutable &&
      !isLikelyArchive &&
      !isLikelyImage &&
      !isLikelyVideo &&
      !isLikelyAudio

  private val isLikelyVideo: Boolean
    get() = fileName.endsWith(".mpeg") || fileName.endsWith(".mpg") || fileName.endsWith(".avi") ||
      fileName.endsWith(".mov") || fileName.endsWith(".mpg4") || fileName.endsWith(".mp4") ||
      fileName.endsWith(".flv") || fileName.endsWith(".wmv")

  private val isLikelyAudio: Boolean
    get() = fileName.endsWith(".mp3") || fileName.endsWith(".ogg") || fileName.endsWith(".m3u") ||
      fileName.endsWith(".wav")

  private val isLikelyBinaryDocument: Boolean
    get() = fileName.endsWith(".pdf") || fileName.endsWith(".ppt") || fileName.endsWith(".doc") ||
      fileName.endsWith(".swf") || fileName.endsWith(".rtf") || fileName.endsWith(".xls")

  private val isLikelyArchive: Boolean
    get() = fileName.endsWith(".gz") || fileName.endsWith(".tgz") || fileName.endsWith(".zip") ||
      fileName.endsWith(".rar") || fileName.endsWith(".deb") || fileName.endsWith(".rpm") ||
      fileName.endsWith(".7z")

  private val isLikelyExecutable: Boolean
    get() = fileName.endsWith(".exe") || fileName.endsWith(".bin") || fileName.endsWith(".bat") ||
      fileName.endsWith(".dmg")

  private val isLikelyImage: Boolean
    get() = fileName.endsWith(".png") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif") ||
      fileName.endsWith(".jpg") || fileName.endsWith(".bmp") || fileName.endsWith(".ico") ||
      fileName.endsWith(".eps")

  /**
   * Replaces a Facebook or Google redirect address by its target. The file name is not updated, as
   * before. A redirect without a query throws, which callers already handle.
   */
  fun resolveRedirects(): PageUrl {
    for (redirect in REDIRECT_PATTERNS) {
      if (redirect.matches(uri)) uri = redirect.resolveHandlingException(uri)
    }
    return this
  }

  override fun toString(): String = uri.toString()

  private abstract class RedirectPattern {
    abstract fun matches(uri: URI): Boolean

    @Throws(URISyntaxException::class)
    abstract fun resolve(uri: URI): URI

    fun resolveHandlingException(uri: URI): URI = try {
      resolve(uri)
    } catch (e: URISyntaxException) {
      uri
    }
  }

  companion object {
    /** Parses [url]; a URI the strict parser rejects is parsed leniently, and a missing scheme becomes http. */
    @JvmStatic
    fun parse(url: String?): PageUrl? {
      if (url == null || url.isEmpty()) return null

      var address: String = url
      var javaNetUri: URI? = try {
        URI(address)
      } catch (e: URISyntaxException) {
        // java.net.URI rejects characters that real addresses use, so try a lenient parse.
        try {
          LenientUrl.toUriLenient(URL(address))
        } catch (e1: URISyntaxException) {
          null
        } catch (e1: MalformedURLException) {
          null
        }
      }

      if (javaNetUri != null && javaNetUri.scheme.isNullOrEmpty()) {
        try {
          address = "http://$address"
          javaNetUri = URI(address)
        } catch (e: URISyntaxException) {
          // Keep the address without a scheme.
        }
      }

      return javaNetUri?.let(::PageUrl)
    }

    private val REDIRECT_PATTERNS = arrayOf(
      object : RedirectPattern() { // Facebook.
        override fun matches(uri: URI): Boolean {
          val host: String? = uri.host
          return host != null && host.endsWith(".facebook.com") && "/l.php" == uri.path
        }

        override fun resolve(uri: URI): URI = URI(getQueryParameters(uri)["u"])
      },
      object : RedirectPattern() { // Google.
        override fun matches(uri: URI): Boolean {
          val host: String? = uri.host
          return host != null && host.endsWith(".google.com") && "/url" == uri.path
        }

        override fun resolve(uri: URI): URI = URI(getQueryParameters(uri)["q"])
      },
    )
  }
}
