// Phase 8: Converted from RxJava to Kotlin Flows/Coroutines
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

package arun.com.chromer.data.website.stores

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.Pair
import androidx.annotation.ColorInt
import androidx.palette.graphics.Palette
import arun.com.chromer.data.website.model.WebColor
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.ColorUtil
import arun.com.chromer.util.Utils
import arun.com.chromer.util.glide.GlideApp
import arun.com.chromer.util.parser.RxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.rx2.asFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network store which freshly parses website data for a given URL.
 */
@Singleton
class WebsiteNetworkStore @Inject constructor(
    application: Application
) : WebsiteStore {

    private val context: Context = application.applicationContext

    override fun getWebsite(url: String): Flow<Website?> = flow {
        try {
            // RxParser is now a suspend function
            val urlArticlePair = RxParser.parseUrl(url)
            if (urlArticlePair.second != null) {
                val extractedWebsite = Website.fromArticle(urlArticlePair.second)
                // We preserve the original url, otherwise breaks cache.
                extractedWebsite.url = urlArticlePair.first
                emit(extractedWebsite)
            } else {
                emit(Website(urlArticlePair.first))
            }
        } catch (e: Exception) {
            Timber.e(e)
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun clearCache() {
        // No-op for network store
    }

    override suspend fun saveWebsite(website: Website): Website? {
        // No-op for network store
        return null
    }

    override suspend fun getWebsiteColor(url: String): WebColor {
        // No-op for network store - returns default
        return WebColor("", Constants.NO_COLOR)
    }

    override suspend fun saveWebsiteColor(host: String, @ColorInt color: Int): WebColor {
        // No-op for network store - returns default
        return WebColor(host, Constants.NO_COLOR)
    }

    override fun getWebsiteIconAndColor(website: Website): Pair<Bitmap, Int> {
        if (TextUtils.isEmpty(website.faviconUrl)) {
            return Pair(null, Constants.NO_COLOR)
        }
        var color = Constants.NO_COLOR
        var icon: Bitmap? = null
        try {
            icon = GlideApp.with(context).asBitmap().load(website.faviconUrl).submit().get()
            val palette = Palette.from(icon).generate()
            color = ColorUtil.getBestColorFromPalette(palette)
        } catch (e: Exception) {
            Timber.e(e)
        }
        return Pair(icon, color)
    }

    override fun getWebsiteRoundIconAndColor(website: Website): Pair<Drawable, Int> {
        if (TextUtils.isEmpty(website.faviconUrl)) {
            return Pair(null, Constants.NO_COLOR)
        }
        var color = Constants.NO_COLOR
        var icon: Bitmap? = null
        try {
            icon = GlideApp.with(context).asBitmap().circleCrop().load(website.faviconUrl).submit().get()
            val palette = Palette.from(icon).clearFilters().generate()
            color = ColorUtil.getBestColorFromPalette(palette)
        } catch (e: Exception) {
            Timber.e(e)
        }
        return if (Utils.isValidFavicon(icon)) {
            Pair(BitmapDrawable(context.resources, icon), color)
        } else {
            Pair(null, color)
        }
    }

    override fun getWebsiteIconWithPlaceholderAndColor(website: Website): Pair<Bitmap, Int> {
        var color = Constants.NO_COLOR
        var icon: Bitmap? = null
        try {
            icon = GlideApp.with(context).asBitmap().load(website).submit().get()
            val palette = Palette.from(icon).generate()
            color = ColorUtil.getBestColorFromPalette(palette)
        } catch (e: Exception) {
            Timber.e(e)
        }
        return Pair(icon, color)
    }
}
