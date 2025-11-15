/*
 * Phase 7: Converted from Java to Kotlin
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

package arun.com.chromer.browsing.customtabs.bottombar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import arun.com.chromer.R
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.ColorUtil
import arun.com.chromer.util.Utils
import java.util.Random

/**
 * Created by Arun on 06/11/2016.
 */
object BottomBarManager {
    private var shareImageDrawable: IconicsDrawable? = null
    private var newTabDrawable: IconicsDrawable? = null
    private var minimizeDrawable: IconicsDrawable? = null
    private var articleDrawable: IconicsDrawable? = null
    private var tabsDrawable: IconicsDrawable? = null

    fun createBottomBarRemoteViews(context: Context, toolbarColor: Int): RemoteViews {
        val iconColor = ColorUtil.getForegroundWhiteOrBlack(toolbarColor)

        val remoteViews = RemoteViews(context.packageName, R.layout.widget_bottom_bar_layout)
        remoteViews.setInt(R.id.bottom_bar_root, "setBackgroundColor", toolbarColor)

        if (!Utils.ANDROID_LOLLIPOP) {
            remoteViews.setViewVisibility(R.id.bottom_bar_minimize_tab, View.GONE)
            remoteViews.setViewVisibility(R.id.bottom_bar_tabs, View.GONE)
        }

        if (shareImageDrawable == null) {
            shareImageDrawable = IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_share_variant)
                .sizeDp(24)
        }
        if (newTabDrawable == null) {
            newTabDrawable = IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_plus)
                .sizeDp(24)
        }
        if (minimizeDrawable == null) {
            minimizeDrawable = IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_arrow_down)
                .sizeDp(24)
        }

        if (articleDrawable == null) {
            articleDrawable = IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_file_document)
                .sizeDp(24)
        }

        if (tabsDrawable == null) {
            tabsDrawable = IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_view_agenda)
                .sizeDp(24)
        }

        val shareImage = shareImageDrawable!!.color(iconColor).toBitmap()
        val openInNewTabImage = newTabDrawable!!.color(iconColor).toBitmap()
        val minimize = minimizeDrawable!!.color(iconColor).toBitmap()
        val article = articleDrawable!!.color(iconColor).toBitmap()
        val tab = tabsDrawable!!.color(iconColor).toBitmap()

        remoteViews.setBitmap(R.id.bottom_bar_open_in_new_tab_img, "setImageBitmap", openInNewTabImage)
        remoteViews.setTextColor(R.id.bottom_bar_open_in_new_tab_text, iconColor)
        remoteViews.setBitmap(R.id.bottom_bar_share_img, "setImageBitmap", shareImage)
        remoteViews.setTextColor(R.id.bottom_bar_share_text, iconColor)
        remoteViews.setBitmap(R.id.bottom_bar_tabs_img, "setImageBitmap", tab)
        remoteViews.setTextColor(R.id.bottom_bar_tabs_text, iconColor)
        remoteViews.setBitmap(R.id.bottom_bar_minimize_img, "setImageBitmap", minimize)
        remoteViews.setTextColor(R.id.bottom_bar_minimize_text, iconColor)
        remoteViews.setBitmap(R.id.bottom_bar_article_view_img, "setImageBitmap", article)
        remoteViews.setTextColor(R.id.bottom_bar_article_view_text, iconColor)
        return remoteViews
    }

    fun getClickableIDs(): IntArray {
        return intArrayOf(
            R.id.bottom_bar_open_in_new_tab,
            R.id.bottom_bar_share,
            R.id.bottom_bar_tabs,
            R.id.bottom_bar_minimize_tab,
            R.id.bottom_bar_article_view
        )
    }

    /**
     * @return The PendingIntent that will be triggered when the user clicks on the Views listed by
     * [BottomBarManager.getClickableIDs].
     */
    fun getOnClickPendingIntent(context: Context, url: String): PendingIntent {
        val broadcastIntent = Intent(context, BottomBarReceiver::class.java)
        broadcastIntent.putExtra(Constants.EXTRA_KEY_ORIGINAL_URL, url)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, Random().nextInt(), broadcastIntent, flags)
    }
}
