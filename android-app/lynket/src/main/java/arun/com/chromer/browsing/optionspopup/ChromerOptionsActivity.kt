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

package arun.com.chromer.browsing.optionspopup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import arun.com.chromer.MainActivity
import arun.com.chromer.browsing.customtabs.browsingUrl
import arun.com.chromer.browsing.openwith.OpenIntentWithActivity
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.history.HistoryActivity
import arun.com.chromer.shared.Constants.EXTRA_KEY_FROM_ARTICLE
import arun.com.chromer.shared.Constants.EXTRA_KEY_ORIGINAL_URL
import arun.com.chromer.shared.base.activity.BaseActivity
import arun.com.chromer.shortcuts.HomeScreenShortcutCreatorActivity
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.browsing.OptionItem
import arun.com.chromer.ui.browsing.OptionsSheet
import arun.com.chromer.ui.browsing.optionItems
import arun.com.chromer.ui.navigation.Destination
import arun.com.chromer.ui.navigation.LynketKey
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The "Lynket Options" item of the Custom Tab menu (and of the article and web view menus): one
 * Compose sheet over the page with Settings, Tabs, New tab, History, Add to Home Screen, Open with…
 * and Open article view.
 */
@AndroidEntryPoint
class ChromerOptionsActivity : BaseActivity() {

  @Inject
  lateinit var tabsManager: TabsManager

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val url = intent.browsingUrl()
    val items = optionItems(fromArticle = intent.getBooleanExtra(EXTRA_KEY_FROM_ARTICLE, false))
    setContent {
      ChromerThemeFromSettings {
        OptionsSheet(items = items, urlKnown = url != null, onPick = { pick(it, url) }, onDismiss = ::finish)
      }
    }
  }

  private fun pick(item: OptionItem, url: String?) {
    when (item) {
      OptionItem.Settings -> startActivity(
        Intent(this, MainActivity::class.java)
          .putExtra(Destination.EXTRA, Destination.nameOf(LynketKey.Settings))
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      )
      OptionItem.Tabs -> tabsManager.showTabsActivity()
      OptionItem.NewTab -> tabsManager.openNewTab(this, "")
      OptionItem.History -> startActivity(Intent(this, HistoryActivity::class.java))
      OptionItem.AddToHomeScreen -> url?.let {
        startActivity(Intent(this, HomeScreenShortcutCreatorActivity::class.java).setData(Uri.parse(it)))
      }
      OptionItem.OpenWith -> url?.let {
        startActivity(
          Intent(this, OpenIntentWithActivity::class.java)
            .setData(Uri.parse(it))
            .putExtra(EXTRA_KEY_ORIGINAL_URL, it),
        )
      }
      OptionItem.ArticleView -> url?.let { tabsManager.openArticle(this, Website(it)) }
    }
    finish()
  }
}
