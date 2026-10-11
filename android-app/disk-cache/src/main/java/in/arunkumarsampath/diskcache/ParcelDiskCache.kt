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

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Cache implementation to store [Parcelable] objects.
 *
 * Implementation modified from https://gist.github.com/VladSumtsov/c4af1f4b8fe5099ca809
 *
 * @param T Parcelable type.
 */
class ParcelDiskCache<T : Parcelable> private constructor(
  context: Context,
  private val classLoader: ClassLoader?,
  name: String,
  maxSize: Long,
) : DiskCache<T> {

  private val storeExecutor: Executor = Executors.newSingleThreadExecutor()
  private val cache: DiskLruCache
  private var saveInUI = true

  init {
    val cacheDir = context.externalCacheDir ?: context.cacheDir
    val dir = File(cacheDir, name)
    val version = getVersionCode(context) + Build.VERSION.SDK_INT
    cache = DiskLruCache.open(dir, version, 1, maxSize)
  }

  override fun set(key: String, value: T): T {
    val validKey = validateKey(key)
    val parcel = Parcel.obtain()
    parcel.writeString(PARCELABLE)
    parcel.writeParcelable(value, 0)
    store(parcel, validKey)
    return value
  }

  fun set(key: String, values: List<T>) {
    val validKey = validateKey(key)
    val parcel = Parcel.obtain()
    parcel.writeString(LIST)
    parcel.writeList(values)
    store(parcel, validKey)
  }

  private fun store(parcel: Parcel, key: String) {
    if (saveInUI) {
      saveValue(cache, parcel, key)
    } else {
      storeExecutor.execute { saveValue(cache, parcel, key) }
    }
  }

  override fun get(key: String): T? {
    val parcel = getParcel(validateKey(key)) ?: return null
    try {
      val type = parcel.readString()
      if (type != null && type != PARCELABLE) {
        throw IllegalAccessError("Parcel doesn't contain parcelable data")
      }
      return readParcelable(parcel)
    } catch (e: Exception) {
      e.printStackTrace()
    } finally {
      parcel.recycle()
    }
    return null
  }

  // sdlc-debt: the typed readParcelable(ClassLoader, Class) needs API 33 and a Class<T> that this
  // generic cache does not hold; the deprecated call is the one older releases used to write and read
  // the same bytes. Upgrade path: pass the item class to open().
  @Suppress("DEPRECATION")
  private fun readParcelable(parcel: Parcel): T? = parcel.readParcelable(classLoader)

  private fun getParcel(key: String): Parcel? {
    var value: ByteArray? = null
    var snapshot: DiskLruCache.Snapshot? = null
    try {
      snapshot = cache[key] ?: return null
      value = getBytesFromStream(snapshot.getInputStream(0))
    } catch (e: IOException) {
      e.printStackTrace()
    } finally {
      snapshot?.close()
    }
    val parcel = Parcel.obtain()
    if (value != null) {
      parcel.unmarshall(value, 0, value.size)
      parcel.setDataPosition(0)
    }
    return parcel
  }

  private fun validateKey(key: String): String {
    val keyMatcher = getPattern(VALIDATE_KEY_REGEX).matcher(key)
    val newKey = StringBuilder()
    while (keyMatcher.find()) {
      val group = keyMatcher.group()
      if (newKey.length + group.length > MAX_KEY_SYMBOLS) {
        break
      }
      newKey.append(group)
    }
    return newKey.toString().lowercase()
  }

  fun getPattern(bodyRegex: String): Pattern {
    val flags = Pattern.MULTILINE or Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    return Pattern.compile(bodyRegex, flags)
  }

  @JvmOverloads
  fun getList(key: String, itemClass: Class<*>? = null): List<T> {
    val res = ArrayList<T>()
    val parcel = getParcel(validateKey(key)) ?: return res
    try {
      val type = parcel.readString() ?: return res
      if (type == PARCELABLE) {
        throw IllegalAccessError("Get not a list data with get method")
      }
      if (type != LIST) {
        throw IllegalAccessError("Parcel doesn't contain list data")
      }
      readList(parcel, res, itemClass?.classLoader ?: ArrayList::class.java.classLoader)
    } catch (e: Exception) {
      e.printStackTrace()
    } finally {
      parcel.recycle()
    }
    return res
  }

  // sdlc-debt: same reason as readParcelable above; the typed readList overload needs API 33.
  @Suppress("DEPRECATION")
  private fun readList(parcel: Parcel, out: MutableList<T>, loader: ClassLoader?) {
    parcel.readList(out, loader)
  }

  override fun remove(key: String): Boolean {
    try {
      return cache.remove(validateKey(key))
    } catch (e: IOException) {
      e.printStackTrace()
    }
    return false
  }

  override fun getAll(): List<T?> = getAll(null)

  fun getAll(prefix: String?): List<T?> {
    val files = cache.directory.listFiles() ?: return ArrayList(1)
    val list = ArrayList<T?>(files.size)
    for (file in files) {
      val fileName = file.name
      val dot = fileName.indexOf(".")
      if (dot > 0 && (prefix.isNullOrEmpty() || fileName.startsWith(prefix))) {
        list.add(get(fileName.substring(0, dot)))
      }
    }
    return list
  }

  override fun clear() {
    try {
      cache.delete()
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  override fun exists(key: String): Boolean {
    var snapshot: DiskLruCache.Snapshot? = null
    try {
      snapshot = cache[validateKey(key)]
      return snapshot != null && snapshot.getLength(0) > 0
    } catch (e: IOException) {
      e.printStackTrace()
    } finally {
      snapshot?.close()
    }
    return false
  }

  override fun close() {
    try {
      cache.close()
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  fun shouldSaveInUI() {
    saveInUI = true
  }

  companion object {
    private const val LIST = "list"
    private const val PARCELABLE = "parcelable"
    private const val VALIDATE_KEY_REGEX = "[a-z0-9_-]{1,5}"
    private const val MAX_KEY_SYMBOLS = 120

    @JvmStatic
    @Throws(IOException::class)
    fun <T : Parcelable> open(
      context: Context,
      classLoader: ClassLoader?,
      name: String,
      maxSize: Long,
    ): ParcelDiskCache<T> = ParcelDiskCache(context, classLoader, name, maxSize)

    private fun saveValue(cache: DiskLruCache, value: Parcel, key: String) {
      val lowerKey = key.lowercase()
      try {
        val skey = lowerKey.intern()
        synchronized(skey) {
          val editor = cache.edit(lowerKey) ?: return
          writeBytesToStream(editor.newOutputStream(0), value.marshall())
          editor.commit()
        }
      } catch (e: IOException) {
        e.printStackTrace()
      } finally {
        value.recycle()
      }
    }

    @Throws(IOException::class)
    private fun getBytesFromStream(input: InputStream): ByteArray {
      val buffer = ByteArrayOutputStream()
      try {
        input.copyTo(buffer, 1024)
        buffer.flush()
        return buffer.toByteArray()
      } finally {
        input.close()
        buffer.close()
      }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeBytesToStream(outputStream: OutputStream, bytes: ByteArray) {
      outputStream.write(bytes)
      outputStream.flush()
      outputStream.close()
    }

    // sdlc-debt: PackageInfo.versionCode is the only version API on API 24-27 (minSdk 24), and the
    // cache folder version must stay versionCode + SDK_INT so caches written by older releases open.
    // Upgrade path: PackageInfoCompat.getLongVersionCode once androidx.core is a dependency here.
    @Suppress("DEPRECATION")
    @JvmStatic
    fun getVersionCode(context: Context): Int {
      var result = 0
      try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        result = pInfo.versionCode
      } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
      }
      return result
    }
  }
}
