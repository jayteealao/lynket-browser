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

package arun.com.chromer.data.website.model

import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.DiffUtil
import arun.com.chromer.data.history.model.HistoryTable
import arun.com.chromer.data.webarticle.model.WebArticle
import arun.com.chromer.shared.Constants.NO_COLOR
import com.chimbori.crux.articles.Article
import java.util.Objects

/**
 * Created by Arun on 05/09/2016.
 */
class Website() : Parcelable {
    var title: String? = null
    var url: String = ""
    var faviconUrl: String? = null
    var canonicalUrl: String? = null
    var themeColor: String? = null
    var ampUrl: String? = null
    var bookmarked: Boolean = false
    var createdAt: Long = 0
    var count: Int = 0

    constructor(url: String) : this() {
        this.url = url
    }

    constructor(
        title: String?,
        url: String,
        faviconUrl: String?,
        canonicalUrl: String?,
        themeColor: String?,
        ampUrl: String?,
        bookmarked: Boolean,
        createdAt: Long,
        count: Int
    ) : this() {
        this.title = title
        this.url = url
        this.faviconUrl = faviconUrl
        this.canonicalUrl = canonicalUrl
        this.themeColor = themeColor
        this.ampUrl = ampUrl
        this.bookmarked = bookmarked
        this.createdAt = createdAt
        this.count = count
    }

    protected constructor(parcel: Parcel) : this() {
        title = parcel.readString()
        url = parcel.readString() ?: ""
        faviconUrl = parcel.readString()
        canonicalUrl = parcel.readString()
        themeColor = parcel.readString()
        ampUrl = parcel.readString()
        bookmarked = parcel.readByte() != 0.toByte()
        createdAt = parcel.readLong()
        count = parcel.readInt()
    }

    fun hasAmp(): Boolean {
        return !TextUtils.isEmpty(ampUrl)
    }

    fun preferredUrl(): String {
        return url
    }

    fun preferredUri(): Uri {
        return Uri.parse(preferredUrl())
    }

    fun safeLabel(): String {
        return if (title != null && title!!.isNotEmpty()) title!! else preferredUrl()
    }

    @ColorInt
    fun themeColor(): Int {
        return try {
            Color.parseColor(themeColor)
        } catch (e: Exception) {
            NO_COLOR
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val website = other as Website

        if (bookmarked != website.bookmarked) return false
        if (createdAt != website.createdAt) return false
        if (count != website.count) return false
        if (!Objects.equals(title, website.title)) return false
        if (url != website.url) return false
        if (!Objects.equals(faviconUrl, website.faviconUrl)) return false
        if (!Objects.equals(canonicalUrl, website.canonicalUrl)) return false
        if (!Objects.equals(themeColor, website.themeColor)) return false
        return Objects.equals(ampUrl, website.ampUrl)
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + url.hashCode()
        result = 31 * result + (faviconUrl?.hashCode() ?: 0)
        result = 31 * result + (canonicalUrl?.hashCode() ?: 0)
        result = 31 * result + (themeColor?.hashCode() ?: 0)
        result = 31 * result + (ampUrl?.hashCode() ?: 0)
        result = 31 * result + if (bookmarked) 1 else 0
        result = 31 * result + (createdAt xor (createdAt ushr 32)).toInt()
        result = 31 * result + count
        return result
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeString(url)
        dest.writeString(faviconUrl)
        dest.writeString(canonicalUrl)
        dest.writeString(themeColor)
        dest.writeString(ampUrl)
        dest.writeByte(if (bookmarked) 1 else 0)
        dest.writeLong(createdAt)
        dest.writeInt(count)
    }

    override fun toString(): String {
        return "Website{" +
                "title='$title', " +
                "url='$url', " +
                "faviconUrl='$faviconUrl', " +
                "canonicalUrl='$canonicalUrl', " +
                "themeColor='$themeColor', " +
                "ampUrl='$ampUrl', " +
                "bookmarked=$bookmarked, " +
                "createdAt=$createdAt, " +
                "count=$count" +
                "}"
    }

    fun matches(url: String): Boolean {
        return url.equals(this.url, ignoreCase = true) ||
                url.equals(this.ampUrl, ignoreCase = true) ||
                url.equals(preferredUrl(), ignoreCase = true)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Website> = object : Parcelable.Creator<Website> {
            override fun createFromParcel(parcel: Parcel): Website {
                return Website(parcel)
            }

            override fun newArray(size: Int): Array<Website?> {
                return arrayOfNulls(size)
            }
        }

        @JvmField
        val DIFFER: DiffUtil.ItemCallback<Website> = object : DiffUtil.ItemCallback<Website>() {
            override fun areItemsTheSame(oldItem: Website, newItem: Website): Boolean {
                return oldItem.url == newItem.url
            }

            override fun areContentsTheSame(oldItem: Website, newItem: Website): Boolean {
                return oldItem == newItem
            }
        }

        @JvmStatic
        fun Ampify(from: Website): Website {
            val website = Website()
            website.title = from.title
            val ampUrl = if (from.hasAmp()) from.ampUrl else from.url
            website.url = ampUrl ?: ""
            website.ampUrl = ampUrl
            website.canonicalUrl = ampUrl
            website.faviconUrl = from.faviconUrl
            website.themeColor = from.themeColor
            website.bookmarked = from.bookmarked
            website.count = from.count
            website.createdAt = from.createdAt
            return website
        }

        @JvmStatic
        fun fromArticle(article: Article): Website {
            val website = Website()
            website.title = article.title
            website.url = article.url
            website.canonicalUrl = if (!TextUtils.isEmpty(article.canonicalUrl)) article.canonicalUrl else article.url
            website.faviconUrl = article.faviconUrl
            website.themeColor = article.themeColor
            website.ampUrl = if (!TextUtils.isEmpty(article.ampUrl)) article.ampUrl else ""
            return website
        }

        @JvmStatic
        fun fromArticle(article: WebArticle): Website {
            val website = Website()
            website.title = article.title
            website.url = article.url
            website.canonicalUrl = if (!TextUtils.isEmpty(article.canonicalUrl)) article.canonicalUrl else article.url
            website.faviconUrl = article.faviconUrl
            website.themeColor = article.themeColor
            website.ampUrl = if (!TextUtils.isEmpty(article.ampUrl)) article.ampUrl else ""
            return website
        }

        @JvmStatic
        fun fromCursor(cursor: Cursor): Website {
            val website = Website()
            website.title = cursor.getString(cursor.getColumnIndex(HistoryTable.COLUMN_TITLE))
            website.url = cursor.getString(cursor.getColumnIndex(HistoryTable.COLUMN_URL))
            website.faviconUrl = cursor.getString(cursor.getColumnIndex(HistoryTable.COLUMN_FAVICON))
            website.canonicalUrl = cursor.getString(cursor.getColumnIndex(HistoryTable.COLUMN_CANONICAL))
            website.themeColor = cursor.getString(cursor.getColumnIndex(HistoryTable.COLUMN_COLOR))
            website.ampUrl = cursor.getString(cursor.getColumnIndex(HistoryTable.COLUMN_AMP))
            website.bookmarked = cursor.getInt(cursor.getColumnIndex(HistoryTable.COLUMN_BOOKMARKED)) == 1
            website.createdAt = cursor.getLong(cursor.getColumnIndex(HistoryTable.COLUMN_CREATED_AT))
            website.count = cursor.getInt(cursor.getColumnIndex(HistoryTable.COLUMN_VISITED))
            return website
        }
    }
}
