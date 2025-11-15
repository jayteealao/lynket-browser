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

package arun.com.chromer.data.common

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.ColorInt
import arun.com.chromer.shared.Constants
import java.util.Comparator

/**
 * Created by Arun on 24/01/2016.
 */
class App() : Parcelable {
    var appName: String = ""
    var packageName: String = ""
    var blackListed: Boolean = false
    var incognito: Boolean = false

    @ColorInt
    var color: Int = Constants.NO_COLOR

    constructor(
        appName: String,
        packageName: String,
        blackListed: Boolean,
        incognito: Boolean,
        color: Int
    ) : this() {
        this.appName = appName
        this.packageName = packageName
        this.blackListed = blackListed
        this.incognito = incognito
        this.color = color
    }

    private constructor(parcel: Parcel) : this() {
        appName = parcel.readString() ?: ""
        packageName = parcel.readString() ?: ""
        blackListed = parcel.readByte() != 0.toByte()
        incognito = parcel.readByte() != 0.toByte()
        color = parcel.readInt()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(appName)
        dest.writeString(packageName)
        dest.writeByte(if (blackListed) 1 else 0)
        dest.writeByte(if (incognito) 1 else 0)
        dest.writeInt(color)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val app = other as App

        if (blackListed != app.blackListed) return false
        if (incognito != app.incognito) return false
        if (color != app.color) return false
        if (appName != app.appName) return false
        return packageName == app.packageName
    }

    override fun hashCode(): Int {
        var result = appName.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + if (blackListed) 1 else 0
        result = 31 * result + if (incognito) 1 else 0
        result = 31 * result + color
        return result
    }

    override fun toString(): String {
        return "App{" +
                "appName='$appName', " +
                "packageName='$packageName', " +
                "blackListed=$blackListed, " +
                "incognito=$incognito, " +
                "color=$color" +
                "}"
    }

    class PerAppListComparator : Comparator<App> {
        override fun compare(lhs: App?, rhs: App?): Int {
            return blackListIncognitoAwareComparison(lhs, rhs)
        }
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<App> {
            override fun createFromParcel(parcel: Parcel): App {
                return App(parcel)
            }

            override fun newArray(size: Int): Array<App?> {
                return arrayOfNulls(size)
            }
        }

        private fun blackListIncognitoAwareComparison(lhs: App?, rhs: App?): Int {
            val lhsName = lhs?.appName
            val rhsName = rhs?.appName

            val lhsValueSet = lhs != null && (lhs.blackListed || lhs.incognito)
            val rhsValueSet = rhs != null && (rhs.blackListed || rhs.incognito)

            if (lhsValueSet xor rhsValueSet) return if (lhsValueSet) -1 else 1
            if ((lhsName == null) xor (rhsName == null)) return if (lhs == null) -1 else 1
            if (lhsName == null && rhsName == null) return 0
            return lhsName!!.compareTo(rhsName!!, ignoreCase = true)
        }
    }
}
