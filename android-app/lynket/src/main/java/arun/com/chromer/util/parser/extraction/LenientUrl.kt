/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), class urls.LenientURLParser
 * and UriCodec, which adapt Android's lenient URI parser. Copyright (C) 2015 The Android Open Source
 * Project, and Chimbori. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: translated to Kotlin; only the encoding
 * that the lenient parse uses is kept.
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

import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetEncoder
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction
import kotlin.math.ceil

/**
 * Makes a [URI] from a [URL] that `java.net.URI` rejects, by escaping the characters a URI does not
 * allow (spaces, for example) part by part, and leaving escapes already in the address alone.
 */
internal object LenientUrl {
  private const val UNRESERVED = "_-!.~'()*"
  private const val PUNCTUATION = ",;:$&+="

  private val AUTHORITY_ENCODER = PartEncoder("@[]")

  /** For java.net.URL, which combines the file and the query. */
  private val FILE_AND_QUERY_ENCODER = PartEncoder("/@?")

  /** For the query, the fragment and the scheme-specific part. */
  private val ALL_LEGAL_ENCODER = PartEncoder("?/[]@")

  fun toUriLenient(url: URL): URI = URI(toExternalForm(url))

  private fun toExternalForm(url: URL): String {
    val result = StringBuilder()
    result.append(url.protocol)
    result.append(':')
    val authority: String? = url.authority
    if (authority != null) {
      result.append("//")
      AUTHORITY_ENCODER.appendPartiallyEncoded(result, authority)
    }
    val fileAndQuery: String? = url.file
    if (fileAndQuery != null) {
      FILE_AND_QUERY_ENCODER.appendPartiallyEncoded(result, fileAndQuery)
    }
    val ref: String? = url.ref
    if (ref != null) {
      result.append('#')
      ALL_LEGAL_ENCODER.appendPartiallyEncoded(result, ref)
    }
    return result.toString()
  }

  /**
   * Keeps letters, digits, the RFC 2396 unreserved characters, punctuation, [extraLegalCharacters]
   * and non-ASCII characters that are not spaces or controls; escapes the rest as UTF-8.
   */
  private class PartEncoder(private val extraLegalCharacters: String) {

    private fun isRetained(c: Char): Boolean =
      UNRESERVED.indexOf(c) != -1 ||
        PUNCTUATION.indexOf(c) != -1 ||
        extraLegalCharacters.indexOf(c) != -1 ||
        (c.code > 127 && !Character.isSpaceChar(c) && !Character.isISOControl(c))

    private fun isWhitelistedOrRetained(c: Char): Boolean =
      ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9') || isRetained(c)

    /** Escapes [s] into [builder]; a `%` in the input is passed through, not escaped again. */
    fun appendPartiallyEncoded(builder: StringBuilder, s: String) {
      val encoder = Charsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
      val cBuffer = CharBuffer.allocate(s.length)
      for (c in s) {
        if (c == '%') {
          // In case there are characters waiting to be encoded.
          flushEncodingCharBuffer(builder, encoder, cBuffer)
          builder.append('%')
          continue
        }
        if (c == ' ' && isRetained(' ')) {
          flushEncodingCharBuffer(builder, encoder, cBuffer)
          builder.append('+')
          continue
        }
        if (isWhitelistedOrRetained(c)) {
          flushEncodingCharBuffer(builder, encoder, cBuffer)
          builder.append(c)
          continue
        }
        // Put the character in the queue for encoding.
        cBuffer.put(c)
      }
      flushEncodingCharBuffer(builder, encoder, cBuffer)
    }

    private fun flushEncodingCharBuffer(builder: StringBuilder, encoder: CharsetEncoder, cBuffer: CharBuffer) {
      if (cBuffer.position() == 0) return
      // We are reading from the buffer now.
      cBuffer.flip()
      val byteBuffer = ByteBuffer.allocate(cBuffer.remaining() * ceil(encoder.maxBytesPerChar().toDouble()).toInt())
      byteBuffer.position(0)
      val result = encoder.encode(cBuffer, byteBuffer, true)
      // The encoder returns underflow and leaves no input when every character was encoded.
      if (result != CoderResult.UNDERFLOW) {
        throw IllegalArgumentException(
          "Error encoding, unexpected result [$result] using encoder for [${encoder.charset().name()}]"
        )
      }
      if (cBuffer.hasRemaining()) {
        throw IllegalArgumentException(
          "Encoder for [${encoder.charset().name()}] failed with underflow with remaining input [$cBuffer]"
        )
      }
      // Flush in case the encoder keeps internal state.
      encoder.flush(byteBuffer)
      encoder.reset()
      byteBuffer.flip()
      // Write the encoded bytes.
      while (byteBuffer.hasRemaining()) {
        val b = byteBuffer.get().toInt()
        builder.append('%')
        builder.append(intToHexDigit((b and 0xf0) ushr 4))
        builder.append(intToHexDigit(b and 0x0f))
      }
      // Use the character buffer to write again.
      cBuffer.flip()
      cBuffer.limit(cBuffer.capacity())
    }

    private fun intToHexDigit(b: Int): Char = if (b < 10) ('0' + b) else ('A' + b - 10)
  }
}
