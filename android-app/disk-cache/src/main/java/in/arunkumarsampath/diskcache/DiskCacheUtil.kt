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

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.Reader
import java.nio.charset.Charset

internal object DiskCacheUtil {
  val US_ASCII: Charset = Charsets.US_ASCII
  val UTF_8: Charset = Charsets.UTF_8

  @Throws(IOException::class)
  fun readFully(reader: Reader): String = reader.use { it.readText() }

  /**
   * Deletes the contents of [dir]. Throws an IOException if any file could not be deleted, or if
   * [dir] is not a readable directory.
   */
  @Throws(IOException::class)
  fun deleteContents(dir: File) {
    val files = dir.listFiles() ?: throw IOException("not a readable directory: $dir")
    for (file in files) {
      if (file.isDirectory) {
        deleteContents(file)
      }
      if (!file.delete()) {
        throw IOException("failed to delete file: $file")
      }
    }
  }

  fun closeQuietly(closeable: Closeable?) {
    if (closeable != null) {
      try {
        closeable.close()
      } catch (rethrown: RuntimeException) {
        throw rethrown
      } catch (ignored: Exception) {
      }
    }
  }
}
