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

package arun.com.chromer.browsing.menu

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import arun.com.chromer.R
import arun.com.chromer.browsing.BrowsingActivity
import arun.com.chromer.browsing.article.ArticleActivity
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.browsing.customtabs.callbacks.CopyToClipboardReceiver
import arun.com.chromer.browsing.customtabs.callbacks.FavShareBroadcastReceiver
import arun.com.chromer.browsing.customtabs.callbacks.SecondaryBrowserReceiver
import arun.com.chromer.browsing.openwith.OpenIntentWithActivity
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.history.HistoryActivity
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shortcuts.HomeScreenShortcutCreatorActivity
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.Utils
import arun.com.chromer.util.Utils.isPackageInstalled
import arun.com.chromer.util.Utils.shareText
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import javax.inject.Inject

/** Where a browsing menu item shows: always in the top bar, in the top bar if room, or in the overflow. */
enum class MenuPlacement { Always, IfRoom, Overflow }

/**
 * One item of the reader's or the web view's menu or bottom bar. [id] is the action id that
 * [MenuDelegate.handleItemSelected] performs. The icon is a drawable or, for the preferred-action
 * button, the chosen app's icon.
 */
data class BrowsingMenuItem(
  @IdRes val id: Int,
  val title: String,
  val placement: MenuPlacement = MenuPlacement.Overflow,
  @DrawableRes val iconRes: Int? = null,
  val appIcon: ImageBitmap? = null,
)

/** The preferred-action button as it shows. */
data class ActionButton(val title: String, @DrawableRes val iconRes: Int? = null, val appIcon: ImageBitmap? = null)

/** Which browsing screen a menu belongs to. */
enum class BrowsingScreen { Article, WebView }

/**
 * The menu in the old order and with the old rules: Open full page (article, always), the
 * preferred-action button (always, left out when its app is gone), Text size (article, if room),
 * then Copy link, Open with, Share, Share with <app> (only with a favourite share app), History,
 * Add to Home Screen, and with the bottom bar off Tabs and (web view) Article mode.
 */
fun browsingMenuItems(
  screen: BrowsingScreen,
  bottomBar: Boolean,
  actionButton: ActionButton?,
  shareWithApp: String?,
  text: (Int) -> String,
): List<BrowsingMenuItem> = buildList {
  val article = screen == BrowsingScreen.Article
  if (article) {
    add(BrowsingMenuItem(R.id.menu_open_full_page, text(R.string.open_full_page), MenuPlacement.Always, R.drawable.ic_open_in_browser_24dp))
  }
  if (actionButton != null) {
    add(BrowsingMenuItem(R.id.menu_action_button, actionButton.title, MenuPlacement.Always, actionButton.iconRes, actionButton.appIcon))
  }
  if (article) {
    add(BrowsingMenuItem(R.id.menu_text_size, text(R.string.text_size), MenuPlacement.IfRoom))
  }
  add(BrowsingMenuItem(R.id.menu_copy_link, text(R.string.copy_link)))
  add(BrowsingMenuItem(R.id.menu_open_with, text(R.string.open_with)))
  add(BrowsingMenuItem(R.id.menu_share, text(R.string.share)))
  if (shareWithApp != null) {
    add(BrowsingMenuItem(R.id.menu_share_with, text(R.string.share_with).format(shareWithApp)))
  }
  add(BrowsingMenuItem(R.id.menu_history, text(R.string.title_history)))
  add(BrowsingMenuItem(R.id.menu_add_to_home_screen, text(R.string.add_to_homescreen)))
  if (!bottomBar) {
    add(BrowsingMenuItem(R.id.tabs, text(R.string.title_tabs)))
    if (!article) add(BrowsingMenuItem(R.id.bottom_bar_article_view, text(R.string.article_mode)))
  }
}

/**
 * The bottom bar in the old order (New tab, Share, Tabs, Minimize, and Article mode in the web
 * view); empty with the setting off.
 */
fun browsingBottomBarItems(screen: BrowsingScreen, bottomBar: Boolean, text: (Int) -> String): List<BrowsingMenuItem> {
  if (!bottomBar) return emptyList()
  return buildList {
    add(BrowsingMenuItem(R.id.bottom_bar_open_in_new_tab, text(R.string.new_tab_text), iconRes = R.drawable.ic_plus_24dp))
    add(BrowsingMenuItem(R.id.bottom_bar_share, text(R.string.share), iconRes = R.drawable.ic_share_24dp))
    add(BrowsingMenuItem(R.id.bottom_bar_tabs, text(R.string.title_tabs), iconRes = R.drawable.ic_tabs_24dp))
    add(BrowsingMenuItem(R.id.bottom_bar_minimize_tab, text(R.string.minimize), iconRes = R.drawable.ic_bottom_bar_minimize))
    if (screen == BrowsingScreen.WebView) {
      add(BrowsingMenuItem(R.id.bottom_bar_article_view, text(R.string.article_mode), iconRes = R.drawable.ic_article_24dp))
    }
  }
}

/**
 * The preferred-action button for [preferredAction]: the secondary browser or the favourite share
 * app with that app's icon, or Share. [secondaryBrowser] and [favShare] are the stored components,
 * null when the app is not installed; the button is left out when the app or its icon is gone.
 */
fun preferredActionButton(
  preferredAction: Int,
  secondaryBrowser: String?,
  favShare: String?,
  iconOf: (String) -> ImageBitmap?,
  text: (Int) -> String,
): ActionButton? = when (preferredAction) {
  Preferences.PREFERRED_ACTION_BROWSER ->
    secondaryBrowser?.let(iconOf)?.let { ActionButton(text(R.string.choose_secondary_browser), appIcon = it) }
  Preferences.PREFERRED_ACTION_FAV_SHARE ->
    favShare?.let(iconOf)?.let { ActionButton(text(R.string.fav_share_app), appIcon = it) }
  Preferences.PREFERRED_ACTION_GEN_SHARE -> ActionButton(text(R.string.share), iconRes = R.drawable.ic_share_24dp)
  else -> null
}

/**
 * Created by arunk on 28-01-2018.
 * Common menu of the browsing activities: the item lists the Compose top bar and bottom bar show,
 * and the action each item performs.
 */
@ActivityScoped
class MenuDelegate @Inject constructor(
  val activity: Activity,
  val tabsManager: TabsManager,
  val preferences: Preferences
) {
  /**
   * Currently active URL.
   */
  private val currentUrl: String
    get() = (activity as BrowsingActivity).getCurrentUrl()
  private val currentUri: Uri
    get() = Uri.parse(currentUrl)
  private val website: Website
    get() = (activity as BrowsingActivity).website ?: Website(currentUrl)
  private val incognito: Boolean
    get() = (activity as BrowsingActivity).incognito

  private val screen = if (activity is ArticleActivity) BrowsingScreen.Article else BrowsingScreen.WebView

  private val text = { id: Int -> activity.getString(id) }

  /** The top-bar and overflow items in the old order. */
  fun menuItems(): List<BrowsingMenuItem> {
    val action = preferredActionButton(
      preferences.preferredAction(),
      secondaryBrowser = preferences.secondaryBrowserComponent()
        ?.takeIf { isPackageInstalled(activity, preferences.secondaryBrowserPackage()) },
      favShare = preferences.favShareComponent()
        ?.takeIf { isPackageInstalled(activity, preferences.favSharePackage()) },
      iconOf = ::activityIcon,
      text = text,
    )
    val shareWith = preferences.favSharePackage()?.let { Utils.getAppNameWithPackage(activity, it) }
    return browsingMenuItems(screen, preferences.bottomBar(), action, shareWith, text)
  }

  /** The bottom-bar items, empty when the bottom bar setting is off. */
  fun bottomBarItems(): List<BrowsingMenuItem> = browsingBottomBarItems(screen, preferences.bottomBar(), text)

  private fun activityIcon(component: String): ImageBitmap? = try {
    ComponentName.unflattenFromString(component)
      ?.let { activity.packageManager.getActivityIcon(it).toBitmap().asImageBitmap() }
  } catch (e: PackageManager.NameNotFoundException) {
    Timber.d(e, "No icon for %s", component)
    null
  }

  private fun shareUrl() {
    shareText(activity, currentUrl)
  }

  fun handleItemSelected(itemId: Int): Boolean {
    when (itemId) {
      R.id.menu_open_full_page -> tabsManager.openBrowsingTab(
        activity,
        website,
        smart = true,
        fromNewTab = false,
        activityNames = listOf(CustomTabActivity::class.java.name),
        incognito = incognito
      )
      android.R.id.home -> activity.finish()
      R.id.bottom_bar_open_in_new_tab -> tabsManager.openNewTab(activity, currentUrl)
      R.id.bottom_bar_share -> shareUrl()
      R.id.bottom_bar_tabs, R.id.tabs -> tabsManager.showTabsActivity()
      R.id.bottom_bar_minimize_tab -> tabsManager.minimizeTabByUrl(
        currentUrl,
        activity::class.java.name,
        incognito
      )
      R.id.bottom_bar_article_view -> tabsManager.openArticle(
        activity,
        website,
        false,
        incognito = incognito
      )
      R.id.menu_action_button -> when (preferences.preferredAction()) {
        Preferences.PREFERRED_ACTION_BROWSER -> activity.sendBroadcast(
          Intent(activity, SecondaryBrowserReceiver::class.java).setData(currentUri)
        )
        Preferences.PREFERRED_ACTION_FAV_SHARE -> activity.sendBroadcast(
          Intent(activity, FavShareBroadcastReceiver::class.java).setData(currentUri)
        )
        Preferences.PREFERRED_ACTION_GEN_SHARE -> shareUrl()
      }
      R.id.menu_copy_link -> activity.sendBroadcast(
        Intent(activity, CopyToClipboardReceiver::class.java).setData(currentUri)
      )
      R.id.menu_open_with -> activity.startActivity(
        Intent(activity, OpenIntentWithActivity::class.java).setData(currentUri)
      )
      R.id.menu_share -> shareUrl()
      R.id.menu_share_with -> activity.sendBroadcast(
        Intent(activity, FavShareBroadcastReceiver::class.java).setData(currentUri)
      )
      R.id.menu_history -> activity.startActivity(Intent(activity, HistoryActivity::class.java))
      R.id.menu_add_to_home_screen -> activity.startActivity(
        Intent(activity, HomeScreenShortcutCreatorActivity::class.java).setData(currentUri)
      )
    }
    return true
  }
}
