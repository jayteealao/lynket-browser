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

package arun.com.chromer.browsing.article

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import arun.com.chromer.R
import arun.com.chromer.browsing.BrowsingActivity
import arun.com.chromer.browsing.menu.MenuDelegate
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.reader.ReaderScreen
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.ui.theme.themeSettings
import arun.com.chromer.util.ColorUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The article reader. It keeps its class name, manifest entry and extras (tab detection, the
 * Article tile and the browsing tab fallback start it by name) and shows the one Compose reader.
 */
@AndroidEntryPoint
class ArticleActivity : BrowsingActivity() {
  private val viewModel: BrowsingArticleViewModel by viewModels()

  @Inject
  lateinit var tabsManager: TabsManager

  @Inject
  lateinit var menuDelegate: MenuDelegate

  /** The website colour BrowsingActivity reports; it becomes the accent under the legacy rule. */
  private var websiteColour by mutableStateOf<Int?>(null)

  public override fun onCreate(savedInstanceState: Bundle?) {
    val themeMode = articleThemeMode(Preferences.get(this).articleTheme())
    val dark = when (themeMode) {
      ThemeMode.Light -> false
      ThemeMode.Dark, ThemeMode.Black -> true
      ThemeMode.Auto -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    super.onCreate(savedInstanceState)
    val url = intent?.dataString ?: return // BrowsingActivity already closed with a message.

    viewModel.initTextSize(preferences.articleTextSizeIncrement())
    viewModel.load(url)
    val settings = preferences.themeSettings()
    val menuItems = menuDelegate.menuItems()
    val bottomBarItems = menuDelegate.bottomBarItems()

    setContent {
      ChromerTheme(themeMode = themeMode, useWallpaperColours = settings.useWallpaperColours, primaryColour = settings.primaryColour) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val increment by viewModel.textSizeIncrement.collectAsStateWithLifecycle()
        var saved by remember { mutableIntStateOf(preferences.articleTextSizeIncrement()) }
        val accent = websiteColour?.takeIf { canUseAsAccentColour(it) }?.let(::Color) ?: MaterialTheme.colorScheme.primary
        ReaderScreen(
          state = state,
          menuItems = menuItems,
          bottomBarItems = bottomBarItems,
          textSizeIncrement = increment ?: saved,
          savedTextSizeIncrement = saved,
          accent = accent,
          onClose = ::finish,
          onMenuItem = { menuDelegate.handleItemSelected(it.id) },
          onRetry = viewModel::retry,
          onOpenInBrowser = { openInBrowser(url) },
          onImage = ::openImage,
          onKeyword = ::searchKeyword,
          onTextSizeChange = viewModel::setTextSize,
          onSaveTextSize = {
            preferences.articleTextSizeIncrement(it)
            saved = it
          },
          modifier = Modifier.semantics { testTagsAsResourceId = true },
        )
      }
    }
  }

  override fun onWebsiteLoaded(website: Website) {}

  override fun onToolbarColorSet(websiteThemeColor: Int) {
    websiteColour = websiteThemeColor
  }

  /** The page in a full browsing tab, as the legacy reader did after a failed extraction. */
  private fun openInBrowser(url: String) {
    tabsManager.openBrowsingTab(
      this,
      Website(url),
      smart = true,
      fromNewTab = false,
      activityNames = TabsManager.FULL_BROWSING_ACTIVITIES
    )
    finish()
  }

  private fun openImage(imageUrl: String) {
    try {
      startActivity(Intent(this, ImageViewActivity::class.java).setData(Uri.parse(imageUrl)))
    } catch (e: Exception) {
      Timber.w(e, "Could not open the image viewer")
      Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_LONG).show()
    }
  }

  /** A keyword chip searches the keyword with the selected search provider (release 2.1.3). */
  private fun searchKeyword(keyword: String) {
    lifecycleScope.launch {
      tabsManager.openUrl(this@ArticleActivity, Website(viewModel.searchUrl(keyword)))
    }
  }

  /** The legacy rule: with the website colour on, a colour that reads on the reader's surface. */
  private fun canUseAsAccentColour(colour: Int): Boolean {
    if (!preferences.dynamiceToolbarEnabledAndWebEnabled()) return false
    val isDark = preferences.articleTheme() != Preferences.THEME_LIGHT
    return if (isDark) {
      !ColorUtil.shouldUseLightForegroundOnBackground(colour)
    } else {
      ColorUtil.shouldUseLightForegroundOnBackground(colour)
    }
  }
}

/** The article theme setting (1 Dark, 2 Light, 3 Auto, 4 Black) as a theme mode; unknown codes are Auto. */
fun articleThemeMode(code: Int): ThemeMode = when (code) {
  Preferences.THEME_DARK -> ThemeMode.Dark
  Preferences.THEME_LIGHT -> ThemeMode.Light
  Preferences.THEME_BLACK -> ThemeMode.Black
  else -> ThemeMode.Auto
}
