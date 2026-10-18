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

// Phase 7: Converted to Kotlin
package arun.com.chromer.data.webarticle.model

import android.graphics.Color
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.ColorInt
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.parser.extraction.ExtractedArticle
import arun.com.chromer.util.parser.extraction.legacyText
import org.jsoup.Jsoup
import org.jsoup.select.Elements

/**
 * Parcelable clone of [ExtractedArticle]. Not fully complete though, there
 * is a limitation of not being able to parcel [Elements] from JSoup. This implementation has
 * partial work around for this by storing the said [Elements] as plain string and then during
 * unmarshalling we get the original elements list by running Jsoup over it. This process is considerably
 * faster than parsing the whole HTML content of the page.
 */
class WebArticle() : Parcelable {
    var url: String = ""
    var originalUrl: String = ""
    var title: String = ""
    var description: String = ""
    var siteName: String = ""
    var themeColor: String = ""
    var ampUrl: String = ""
    var canonicalUrl: String = ""
    var imageUrl: String = ""
    var videoUrl: String = ""
    var feedUrl: String = ""
    var faviconUrl: String = ""
    var keywords: MutableList<String> = ArrayList()
    var elements: Elements? = null

    constructor(url: String) : this() {
        this.url = url
    }

    private constructor(parcel: Parcel) : this() {
        url = parcel.readString() ?: ""
        originalUrl = parcel.readString() ?: ""
        title = parcel.readString() ?: ""
        description = parcel.readString() ?: ""
        siteName = parcel.readString() ?: ""
        themeColor = parcel.readString() ?: ""
        ampUrl = parcel.readString() ?: ""
        canonicalUrl = parcel.readString() ?: ""
        imageUrl = parcel.readString() ?: ""
        videoUrl = parcel.readString() ?: ""
        feedUrl = parcel.readString() ?: ""
        faviconUrl = parcel.readString() ?: ""

        val html = parcel.readString() ?: ""
        val rawElements = Jsoup.parse(html).body().children()
        val iterator = rawElements.iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            // The reader has always measured emptiness with jsoup 1.9.2's text rule, which keeps
            // soft hyphens; a current jsoup drops them and would hide those paragraphs.
            if (element.legacyText().isEmpty()) {
                iterator.remove()
            }
        }
        elements = rawElements

        keywords = parcel.createStringArrayList() ?: ArrayList()
    }

    fun preferredUrl(): String {
        return if (!canonicalUrl.isNullOrEmpty()) canonicalUrl else url
    }

    fun safeLabel(): String {
        return if (!title.isNullOrEmpty()) title else preferredUrl()
    }

    @ColorInt
    fun themeColor(): Int {
        return try {
            Color.parseColor(themeColor)
        } catch (e: Exception) {
            Constants.NO_COLOR
        }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(url)
        dest.writeString(originalUrl)
        dest.writeString(title)
        dest.writeString(description)
        dest.writeString(siteName)
        dest.writeString(themeColor)
        dest.writeString(ampUrl)
        dest.writeString(canonicalUrl)
        dest.writeString(imageUrl)
        dest.writeString(videoUrl)
        dest.writeString(feedUrl)
        dest.writeString(faviconUrl)
        dest.writeString(elements.toString())
        dest.writeStringList(keywords)
    }

    companion object CREATOR : Parcelable.Creator<WebArticle> {
        override fun createFromParcel(parcel: Parcel): WebArticle {
            return WebArticle(parcel)
        }

        override fun newArray(size: Int): Array<WebArticle?> {
            return arrayOfNulls(size)
        }

        /**
         * The extractor leaves fields it did not find null; each becomes "", so a page without, for
         * example, an AMP or feed address still makes an article instead of failing the extraction.
         */
        fun fromArticle(article: ExtractedArticle): WebArticle {
            val webArticle = WebArticle()
            webArticle.url = article.url
            webArticle.originalUrl = article.originalUrl
            webArticle.title = article.title ?: ""
            webArticle.description = article.description ?: ""
            webArticle.siteName = article.siteName ?: ""
            webArticle.themeColor = article.themeColor ?: ""
            webArticle.ampUrl = article.ampUrl ?: ""
            webArticle.canonicalUrl = article.canonicalUrl ?: ""
            webArticle.imageUrl = article.imageUrl ?: ""
            webArticle.videoUrl = article.videoUrl ?: ""
            webArticle.feedUrl = article.feedUrl ?: ""
            webArticle.faviconUrl = article.faviconUrl ?: ""
            webArticle.elements = article.document?.children()
            webArticle.keywords = ArrayList()
            article.keywords?.let { webArticle.keywords.addAll(it) }
            return webArticle
        }
    }
}
