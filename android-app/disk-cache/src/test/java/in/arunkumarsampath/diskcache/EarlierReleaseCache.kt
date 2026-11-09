/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package `in`.arunkumarsampath.diskcache

import android.os.Parcel
import android.os.Parcelable
import java.io.File

/**
 * Writes cache entries byte for byte as earlier releases of the cache wrote them: a type marker
 * string, then the parcelable or the list, marshalled into the first value of a [DiskLruCache]
 * entry whose file key is the lowercased [ParcelDiskCache.cacheKey]. It does not call the class
 * under test, so a change of its write path cannot hide a change of the format.
 */
object EarlierReleaseCache {

  private const val MAX_SIZE = 1024L * 1024L

  fun writeParcelableEntry(dir: File, version: Int, key: String, value: Parcelable) {
    val parcel = Parcel.obtain()
    parcel.writeString("parcelable")
    parcel.writeParcelable(value, 0)
    write(dir, version, key, parcel)
  }

  fun writeListEntry(dir: File, version: Int, key: String, values: List<Parcelable>) {
    val parcel = Parcel.obtain()
    parcel.writeString("list")
    parcel.writeList(values)
    write(dir, version, key, parcel)
  }

  private fun write(dir: File, version: Int, key: String, parcel: Parcel) {
    val bytes = try {
      parcel.marshall()
    } finally {
      parcel.recycle()
    }
    val cache = DiskLruCache.open(dir, version, 1, MAX_SIZE)
    try {
      val editor = cache.edit(ParcelDiskCache.cacheKey(key).lowercase())!!
      editor.newOutputStream(0).use { it.write(bytes) }
      editor.commit()
    } finally {
      cache.close()
    }
  }
}
