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

package `in`.arunkumarsampath.diskcache

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Reads lines that end with `"\n"` or `"\r\n"` from an [InputStream]. Only US-ASCII is supported.
 *
 * @throws IllegalArgumentException if [capacity] is negative or the charset is not supported.
 */
internal class StrictLineReader(
  private val input: InputStream,
  capacity: Int,
  private val charset: Charset,
) : Closeable {

  constructor(input: InputStream, charset: Charset) : this(input, 8192, charset)

  /*
   * Buffered data is stored in buf. As long as no exception occurs, 0 <= pos <= end and the data
   * in the range [pos, end) is buffered for reading. At end of input, if there is an unterminated
   * line, we set end == -1, otherwise end == pos. If the underlying InputStream throws an
   * IOException, end may remain as either pos or -1.
   */
  private var buf: ByteArray?
  private var pos = 0
  private var end = 0

  init {
    require(capacity >= 0) { "capacity <= 0" }
    require(charset == DiskCacheUtil.US_ASCII) { "Unsupported encoding" }
    buf = ByteArray(capacity)
  }

  /** Closes the reader by closing the underlying [InputStream] and marking this reader as closed. */
  @Throws(IOException::class)
  override fun close() {
    synchronized(input) {
      if (buf != null) {
        buf = null
        input.close()
      }
    }
  }

  /**
   * Reads the next line. A line ends with `"\n"` or `"\r\n"`; the end of line marker is not
   * included in the result.
   *
   * @throws EOFException for the end of source stream.
   */
  @Throws(IOException::class)
  fun readLine(): String {
    synchronized(input) {
      val buffer = buf ?: throw IOException("LineReader is closed")

      // Read more data if we are at the end of the buffered data.
      // Though it's an error to read after an exception, we will let fillBuf() throw again if that
      // happens; thus we need to handle end == -1 as well as end == pos.
      if (pos >= end) {
        fillBuf()
      }
      // Try to find LF in the buffered data and return the line if successful.
      var i = pos
      while (i != end) {
        if (buffer[i] == LF) {
          val lineEnd = if (i != pos && buffer[i - 1] == CR) i - 1 else i
          val res = String(buffer, pos, lineEnd - pos, charset)
          pos = i + 1
          return res
        }
        ++i
      }

      // Let's anticipate up to 80 characters on top of those already read.
      val out = object : ByteArrayOutputStream(end - pos + 80) {
        override fun toString(): String {
          val length = if (count > 0 && buf[count - 1] == CR) count - 1 else count
          return String(buf, 0, length, charset)
        }
      }

      while (true) {
        out.write(buffer, pos, end - pos)
        // Mark unterminated line in case fillBuf throws EOFException or IOException.
        end = -1
        fillBuf()
        // Try to find LF in the buffered data and return the line if successful.
        var j = pos
        while (j != end) {
          if (buffer[j] == LF) {
            if (j != pos) {
              out.write(buffer, pos, j - pos)
            }
            pos = j + 1
            return out.toString()
          }
          ++j
        }
      }
    }
  }

  fun hasUnterminatedLine(): Boolean = end == -1

  /**
   * Reads new input data into the buffer. Call only with pos == end or end == -1, depending on the
   * desired outcome if the function throws.
   */
  @Throws(IOException::class)
  private fun fillBuf() {
    val buffer = buf ?: throw IOException("LineReader is closed")
    val result = input.read(buffer, 0, buffer.size)
    if (result == -1) {
      throw EOFException()
    }
    pos = 0
    end = result
  }

  private companion object {
    const val CR: Byte = 13 // '\r'
    const val LF: Byte = 10 // '\n'
  }
}
