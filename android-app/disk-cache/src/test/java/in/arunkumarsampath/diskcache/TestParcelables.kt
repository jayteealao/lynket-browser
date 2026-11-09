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
import androidx.core.os.ParcelCompat

/** A cached item with a nested parcelable, read back with the item's own class loader. */
data class TestItem(val title: String, val url: String, val tag: TestTag?) : Parcelable {

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeString(title)
    dest.writeString(url)
    dest.writeParcelable(tag, flags)
  }

  override fun describeContents(): Int = 0

  companion object {
    @JvmField
    val CREATOR = object : Parcelable.Creator<TestItem> {
      override fun createFromParcel(source: Parcel): TestItem = TestItem(
        title = source.readString()!!,
        url = source.readString()!!,
        tag = ParcelCompat.readParcelable(source, TestTag::class.java.classLoader, TestTag::class.java),
      )

      override fun newArray(size: Int): Array<TestItem?> = arrayOfNulls(size)
    }
  }
}

/** The nested parcelable of [TestItem]. */
data class TestTag(val name: String) : Parcelable {

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeString(name)
  }

  override fun describeContents(): Int = 0

  companion object {
    @JvmField
    val CREATOR = object : Parcelable.Creator<TestTag> {
      override fun createFromParcel(source: Parcel): TestTag = TestTag(source.readString()!!)

      override fun newArray(size: Int): Array<TestTag?> = arrayOfNulls(size)
    }
  }
}

/** A parcelable of another type, stored where a [TestItem] is expected. */
data class OtherItem(val id: Int) : Parcelable {

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeInt(id)
  }

  override fun describeContents(): Int = 0

  companion object {
    @JvmField
    val CREATOR = object : Parcelable.Creator<OtherItem> {
      override fun createFromParcel(source: Parcel): OtherItem = OtherItem(source.readInt())

      override fun newArray(size: Int): Array<OtherItem?> = arrayOfNulls(size)
    }
  }
}
