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

package arun.com.chromer.browsing.customtabs.bottombar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.compose.ui.graphics.toArgb
import arun.com.chromer.R
import arun.com.chromer.browsing.customtabs.callbackBroadcast
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.ui.theme.chromerColorScheme
import arun.com.chromer.ui.theme.themeSettings
import java.util.Random

/**
 * The Custom Tab bottom bar (board custom-tab--default): five actions in the old order, New tab,
 * Share, Tabs, Minimize and Article, each an icon over its label, on the app's neutral surface.
 */
object BottomBarManager {

  /** Colours of the bar in the current theme: the surface and the icon and label colour. */
  data class BarColours(@ColorInt val background: Int, @ColorInt val content: Int)

  fun barColours(context: Context): BarColours {
    val settings = Preferences.get(context).themeSettings()
    val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
    val dark = when (settings.themeMode) {
      ThemeMode.Auto -> systemDark
      ThemeMode.Light -> false
      ThemeMode.Dark, ThemeMode.Black -> true
    }
    val scheme = chromerColorScheme(
      context,
      dark = dark,
      black = settings.themeMode == ThemeMode.Black,
      useWallpaperColours = settings.useWallpaperColours,
      primaryColour = settings.primaryColour,
    )
    return BarColours(scheme.surfaceContainer.toArgb(), scheme.onSurfaceVariant.toArgb())
  }

  @ColorInt
  fun bottomBarColor(context: Context): Int = barColours(context).background

  /** The actions in their old order: container id, icon view, label view, icon. */
  private data class Action(@IdRes val id: Int, @IdRes val image: Int, @IdRes val text: Int, @DrawableRes val icon: Int)

  private val actions = listOf(
    Action(R.id.bottom_bar_open_in_new_tab, R.id.bottom_bar_open_in_new_tab_img, R.id.bottom_bar_open_in_new_tab_text, R.drawable.ic_plus_24dp),
    Action(R.id.bottom_bar_share, R.id.bottom_bar_share_img, R.id.bottom_bar_share_text, R.drawable.ic_share_24dp),
    Action(R.id.bottom_bar_tabs, R.id.bottom_bar_tabs_img, R.id.bottom_bar_tabs_text, R.drawable.ic_tabs_24dp),
    Action(R.id.bottom_bar_minimize_tab, R.id.bottom_bar_minimize_img, R.id.bottom_bar_minimize_text, R.drawable.ic_bottom_bar_minimize),
    Action(R.id.bottom_bar_article_view, R.id.bottom_bar_article_view_img, R.id.bottom_bar_article_view_text, R.drawable.ic_article_24dp),
  )

  fun createBottomBarRemoteViews(context: Context): RemoteViews {
    val colours = barColours(context)
    val remoteViews = RemoteViews(context.packageName, R.layout.widget_bottom_bar_layout)
    remoteViews.setInt(R.id.bottom_bar_root, "setBackgroundColor", colours.background)
    actions.forEach { action ->
      val icon = Icon.createWithResource(context, action.icon).setTint(colours.content)
      remoteViews.setImageViewIcon(action.image, icon)
      remoteViews.setTextColor(action.text, colours.content)
    }
    return remoteViews
  }

  fun getClickableIDs(): IntArray = actions.map { it.id }.toIntArray()

  /**
   * The PendingIntent the provider sends when one of [getClickableIDs] is tapped. It is mutable and
   * names [BottomBarReceiver], so the provider can add the page address and the clicked view id.
   */
  fun getOnClickPendingIntent(context: Context, url: String): PendingIntent {
    val broadcastIntent = Intent(context, BottomBarReceiver::class.java)
      .putExtra(Constants.EXTRA_KEY_ORIGINAL_URL, url)
    return callbackBroadcast(context, Random().nextInt(), broadcastIntent)
  }
}
