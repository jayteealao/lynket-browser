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

interface DiskCache<T> {

  /** Sets the value to [value]. */
  fun set(key: String, value: T): T

  /**
   * Returns a value by [key], or null if it doesn't exist or is not currently readable.
   * If a value is returned, it is moved to the head of the LRU queue.
   */
  fun get(key: String): T?

  /**
   * Drops the entry for [key] if it exists and can be removed. Entries actively being edited
   * cannot be removed.
   *
   * @return true if an entry was removed.
   */
  fun remove(key: String): Boolean

  /** Returns all values from the cache directory if all files are the same type. */
  fun getAll(): List<T?>

  /** Deletes all files from the cache directory. */
  fun clear()

  /** Returns true if there is a file for [key] in the cache folder. */
  fun exists(key: String): Boolean

  /** Closes this cache. Stored values will remain on the filesystem. */
  fun close()
}
