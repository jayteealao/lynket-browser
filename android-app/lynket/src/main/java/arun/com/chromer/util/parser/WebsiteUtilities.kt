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

// Phase 7: Converted from Java to Kotlin

package arun.com.chromer.util.parser

import androidx.annotation.WorkerThread
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Created by Arunkumar on 16/02/17.
 * An utility class to fetch a website as [String].
 * Adapted from https://github.com/karussell/snacktory by Peter Karich
 */
internal object WebsiteUtilities {

  private const val ACCEPT = "application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5"

  // We will spoof as an iPad so that websites properly expose their shortcut icon. Even Google.com
  // does not provide bigger icons when we go as Android.
  private const val USER_AGENT = "Mozilla/5.0 (iPad; CPU OS 6_0 like Mac OS X) AppleWebKit/536.26 (KHTML, like Gecko) Version/6.0 Mobile/10A5376e Safari/8536.25"

  @WorkerThread
  @Throws(IOException::class)
  fun htmlString(url: String): String {
    val urlConnection = createUrlConnection(url, 10000)
    urlConnection.instanceFollowRedirects = true
    val encoding = urlConnection.contentEncoding
    val inputStream: InputStream = when {
      encoding != null && encoding.equals("gzip", ignoreCase = true) -> {
        GZIPInputStream(urlConnection.inputStream)
      }
      encoding != null && encoding.equals("deflate", ignoreCase = true) -> {
        InflaterInputStream(urlConnection.inputStream, Inflater(true))
      }
      else -> {
        urlConnection.inputStream
      }
    }
    val enc = Converter.extractEncoding(urlConnection.contentType)
    val result = Converter(url).grabStringFromInputStream(inputStream, enc)
    urlConnection.disconnect()
    return result
  }

  @WorkerThread
  @Throws(IOException::class)
  fun headString(url: String): String {
    val urlConnection = createUrlConnection(url, 10000)
    urlConnection.instanceFollowRedirects = true
    val encoding = urlConnection.contentEncoding
    val inputStream: InputStream = when {
      encoding != null && encoding.equals("gzip", ignoreCase = true) -> {
        GZIPInputStream(urlConnection.inputStream)
      }
      encoding != null && encoding.equals("deflate", ignoreCase = true) -> {
        InflaterInputStream(urlConnection.inputStream, Inflater(true))
      }
      else -> {
        urlConnection.inputStream
      }
    }
    val enc = Converter.extractEncoding(urlConnection.contentType)
    val result = Converter(url).grabHeadTag(inputStream, enc)
    urlConnection.disconnect()
    try {
      inputStream.close()
    } catch (ignored: Exception) {
    }
    return result
  }

  fun unShortenUrl(url: String): String {
    val maxRedirects = 3
    var unShortenedUrl = url
    var currentUrl = url
    for (i in 0 until maxRedirects) {
      currentUrl = getRedirectUrl(currentUrl)
      Timber.d("Redirect: %s", currentUrl)
      if (currentUrl.equals(unShortenedUrl, ignoreCase = true)) {
        return unShortenedUrl
      }
      unShortenedUrl = currentUrl
    }
    return unShortenedUrl
  }

  private fun getRedirectUrl(url: String): String {
    var conn: HttpURLConnection? = null
    try {
      conn = createUrlConnection(url, 10000)
      conn.instanceFollowRedirects = false
      conn.requestMethod = "HEAD"
      conn.connect()
      val responseCode = conn.responseCode
      if (responseCode >= 300 && responseCode < 400) {
        return useDomainOfFirstArg4Second(url, conn.getHeaderField("Location"))
      } else if (responseCode >= 200 && responseCode < 300) {
        return url
      }
    } catch (ex: Exception) {
      return url
    } finally {
      conn?.disconnect()
    }
    return url
  }

  private fun useDomainOfFirstArg4Second(urlForDomain: String, path: String): String {
    var modifiedPath = path
    if (modifiedPath.startsWith("http"))
      return modifiedPath

    if ("favicon.ico" == modifiedPath)
      modifiedPath = "/favicon.ico"

    if (modifiedPath.startsWith("//")) {
      // wikipedia special case, see tests
      return if (urlForDomain.startsWith("https:"))
        "https:$modifiedPath"
      else
        "http:$modifiedPath"
    } else if (modifiedPath.startsWith("/")) {
      return "http://" + extractHost(urlForDomain) + modifiedPath
    } else if (modifiedPath.startsWith("../")) {
      var modifiedUrlForDomain = urlForDomain
      val slashIndex = modifiedUrlForDomain.lastIndexOf("/")
      if (slashIndex > 0 && slashIndex + 1 < modifiedUrlForDomain.length)
        modifiedUrlForDomain = modifiedUrlForDomain.substring(0, slashIndex + 1)
      return modifiedUrlForDomain + modifiedPath
    }
    return modifiedPath
  }

  private fun extractHost(url: String): String {
    return extractDomain(url, false)
  }

  private fun extractDomain(url: String, aggressive: Boolean): String {
    var modifiedUrl = url
    if (modifiedUrl.startsWith("http://"))
      modifiedUrl = modifiedUrl.substring("http://".length)
    else if (modifiedUrl.startsWith("https://"))
      modifiedUrl = modifiedUrl.substring("https://".length)

    if (aggressive) {
      if (modifiedUrl.startsWith("www."))
        modifiedUrl = modifiedUrl.substring("www.".length)

      // strip mobile from start
      if (modifiedUrl.startsWith("m."))
        modifiedUrl = modifiedUrl.substring("m.".length)
    }

    val slashIndex = modifiedUrl.indexOf("/")
    if (slashIndex > 0)
      modifiedUrl = modifiedUrl.substring(0, slashIndex)

    return modifiedUrl
  }

  /**
   * Provides a [HttpURLConnection] instance for the given url and timeout
   *
   * @param urlAsStr Url to create a connection for.
   * @param timeout  Timeout
   * @return [HttpURLConnection] instance.
   * @throws IOException
   */
  @Throws(IOException::class)
  private fun createUrlConnection(urlAsStr: String, timeout: Int): HttpURLConnection {
    val url = URL(urlAsStr)
    //using proxy may increase latency
    val urlConnection = url.openConnection() as HttpURLConnection
    urlConnection.setRequestProperty("User-Agent", USER_AGENT)
    urlConnection.setRequestProperty("Accept", ACCEPT)
    // suggest respond to be gzipped or deflated (which is just another compression)
    // http://stackoverflow.com/q/3932117
    urlConnection.setRequestProperty("Accept-Encoding", "gzip, deflate")
    urlConnection.connectTimeout = timeout
    urlConnection.readTimeout = timeout
    return urlConnection
  }

  private class Converter(private val url: String? = null) {

    private var maxBytes = 1000000 / 2
    private var encoding: String? = null

    fun setMaxBytes(maxBytes: Int): Converter {
      this.maxBytes = maxBytes
      return this
    }

    fun getEncoding(): String {
      return encoding?.lowercase() ?: ""
    }

    fun grabStringFromInputStream(inputStream: InputStream): String {
      return grabStringFromInputStream(inputStream, maxBytes, encoding)
    }

    fun grabStringFromInputStream(inputStream: InputStream, encoding: String?): String {
      return grabStringFromInputStream(inputStream, maxBytes, encoding)
    }

    /**
     * reads bytes off the string and returns a string
     *
     * @param is
     * @param maxBytes The max bytes that we want to read from the input stream
     * @return String
     */
    fun grabStringFromInputStream(inputStream: InputStream, maxBytes: Int, encoding: String?): String {
      this.encoding = encoding
      // Http 1.1. standard is iso-8859-1 not utf8 :(
      // but we force utf-8 as youtube assumes it ;)
      if (this.encoding.isNullOrEmpty())
        this.encoding = UTF8

      try {
        BufferedInputStream(inputStream, K2).use { input ->
          val output = ByteArrayOutputStream()

          // detect encoding with the help of meta tag
          try {
            input.mark(K2 * 2)
            var tmpEncoding = detectCharset("charset=", output, input, this.encoding!!)
            if (tmpEncoding != null) {
              this.encoding = tmpEncoding
            } else {
              Timber.d("no charset found in first stage")
              // detect with the help of xml beginning ala encoding="charset"
              tmpEncoding = detectCharset("encoding=", output, input, this.encoding!!)
              if (tmpEncoding != null) {
                this.encoding = tmpEncoding
              } else {
                Timber.d("no charset found in second stage")
              }
            }
            if (!Charset.isSupported(this.encoding!!))
              throw UnsupportedEncodingException(this.encoding)
          } catch (e: UnsupportedEncodingException) {
            Timber.e(e, "Using default encoding:%s encoding %s", encoding, url)
            this.encoding = UTF8
          }

          // SocketException: Connection reset
          // IOException: missing CR    => problem on server (probably some xml character thing?)
          // IOException: Premature EOF => socket unexpectly closed from server
          var bytesRead = output.size()
          val arr = ByteArray(K2)
          while (true) {
            if (bytesRead >= maxBytes) {
              Timber.w("Maxbyte of %d exceeded! Maybe html is now broken but try it nevertheless. Url: %s ", maxBytes, url)
              break
            }

            val n = input.read(arr)
            if (n < 0)
              break
            bytesRead += n
            output.write(arr, 0, n)
          }

          return output.toString(this.encoding!!)
        }
      } catch (e: IOException) {
        Timber.e(e, " url: %s", url)
      }
      return ""
    }

    fun grabHeadTag(inputStream: InputStream, encoding: String?): String {
      this.encoding = encoding
      // Http 1.1. standard is iso-8859-1 not utf8 :(
      // but we force utf-8 as youtube assumes it ;)
      if (this.encoding.isNullOrEmpty())
        this.encoding = UTF8

      val headTagContents = StringBuilder()

      try {
        InputStreamReader(inputStream, encoding).use { inputStreamReader ->
          BufferedReader(inputStreamReader).use { bufferedReader ->
            var temp: String?
            var insideHeadTag = false
            while (bufferedReader.readLine().also { temp = it } != null) {
              if (temp!!.contains("<head")) {
                insideHeadTag = true
              }
              if (insideHeadTag) {
                headTagContents.append(temp)
              }
              if (temp!!.contains("</head>")) {
                // Exit
                break
              }
            }
          }
        }
      } catch (e: IOException) {
        Timber.e(e)
      }
      return headTagContents.toString()
    }

    /**
     * This method detects the charset even if the first call only returns some
     * bytes. It will read until 4K bytes are reached and then try to determine
     * the encoding
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun detectCharset(
      key: String,
      bos: ByteArrayOutputStream,
      input: BufferedInputStream,
      enc: String
    ): String? {
      // Grab better encoding from stream
      val arr = ByteArray(K2)
      var nSum = 0
      while (nSum < K2) {
        val n = input.read(arr)
        if (n < 0)
          break

        nSum += n
        bos.write(arr, 0, n)
      }

      val str = bos.toString(enc)
      val encIndex = str.indexOf(key)
      val clength = key.length
      if (encIndex > 0) {
        val startChar = str[encIndex + clength]
        val lastEncIndex: Int = when {
          startChar == '\'' ->
            // if we have charset='something'
            str.indexOf("'", encIndex + clength + 1)
          startChar == '"' ->
            // if we have charset="something"
            str.indexOf("\"", encIndex + clength + 1)
          else -> {
            // if we have "text/html; charset=utf-8"
            var first = str.indexOf("\"", encIndex + clength)
            if (first < 0)
              first = Int.MAX_VALUE

            // or "text/html; charset=utf-8 "
            var sec = str.indexOf(" ", encIndex + clength)
            if (sec < 0)
              sec = Int.MAX_VALUE
            var minIndex = Math.min(first, sec)

            // or "text/html; charset=utf-8 '
            val third = str.indexOf("'", encIndex + clength)
            if (third > 0)
              minIndex = Math.min(minIndex, third)
            minIndex
          }
        }

        // re-read byte array with different encoding
        // assume that the encoding string cannot be greater than 40 chars
        if (lastEncIndex > encIndex + clength && lastEncIndex < encIndex + clength + 40) {
          val tmpEnc = encodingCleanup(str.substring(encIndex + clength, lastEncIndex))
          try {
            input.reset()
            bos.reset()
            return tmpEnc
          } catch (ex: IOException) {
            Timber.w(enc, "Couldn't reset stream to re-read with new encoding")
          }
        }
      }
      return null
    }

    companion object {
      const val UTF8 = "UTF-8"
      const val ISO = "ISO-8859-1"
      const val K2 = 2048

      /**
       * Tries to extract type of encoding for the given content type.
       *
       * @param contentType Content type gotten from [HttpURLConnection.getContentType]
       * @return
       */
      fun extractEncoding(contentType: String?): String {
        val values: Array<String> = if (contentType != null)
          contentType.split(";").toTypedArray()
        else
          emptyArray()
        var charset = ""

        for (value in values) {
          val trimmedValue = value.trim().lowercase()
          if (trimmedValue.startsWith("charset="))
            charset = trimmedValue.substring("charset=".length)
        }
        // http1.1 says ISO-8859-1 is the default charset
        if (charset.isEmpty())
          charset = ISO
        return charset
      }

      fun encodingCleanup(str: String): String {
        val sb = StringBuilder()
        var startedWithCorrectString = false
        for (i in str.indices) {
          val c = str[i]
          if (Character.isDigit(c) || Character.isLetter(c) || c == '-' || c == '_') {
            startedWithCorrectString = true
            sb.append(c)
            continue
          }

          if (startedWithCorrectString)
            break
        }
        return sb.toString().trim()
      }
    }
  }
}
