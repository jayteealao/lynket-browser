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

package arun.com.chromer.bubbles.webheads.ui.context

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_TEXT
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.IntentCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants.ACTION_CLOSE_WEBHEAD_BY_URL
import arun.com.chromer.shared.Constants.ACTION_EVENT_WEBHEAD_DELETED
import arun.com.chromer.shared.Constants.ACTION_EVENT_WEBSITE_UPDATED
import arun.com.chromer.shared.Constants.EXTRA_KEY_WEBSITE
import arun.com.chromer.shared.Constants.TEXT_SHARE_INTENT
import arun.com.chromer.shared.base.activity.BaseActivity
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Shows the open web heads in a bottom sheet over the current app. */
@AndroidEntryPoint
class WebHeadContextActivity : BaseActivity(), WebHeadContextActions {
  private val webHeadsEventsReceiver = WebHeadEventsReceiver()

  @Inject
  lateinit var tabsManager: TabsManager

  /** The sites on the sheet, kept in sync with the heads through the service's broadcasts. */
  internal val websites = mutableStateListOf<Website>()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val extra = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_KEY_WEBSITE, Website::class.java)
    if (extra == null) {
      finish()
      return
    }
    websites.addAll(extra)
    setContent {
      ChromerThemeFromSettings {
        WebHeadContextSheet(websites = websites, actions = this)
      }
    }
    registerEventsReceiver()
  }

  private fun registerEventsReceiver() {
    val filter = IntentFilter().apply {
      addAction(ACTION_EVENT_WEBHEAD_DELETED)
      addAction(ACTION_EVENT_WEBSITE_UPDATED)
    }
    LocalBroadcastManager.getInstance(this).registerReceiver(webHeadsEventsReceiver, filter)
  }

  override fun onDestroy() {
    super.onDestroy()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(webHeadsEventsReceiver)
  }

  override fun open(website: Website) {
    finish()
    tabsManager.openUrl(this, website, true, true, false, false, false)
    if (Preferences.get(this).webHeadsCloseOnOpen()) {
      broadcastDeleteWebHead(website)
    }
  }

  private fun broadcastDeleteWebHead(website: Website) {
    val intent = Intent(ACTION_CLOSE_WEBHEAD_BY_URL).apply {
      putExtra(EXTRA_KEY_WEBSITE, website)
    }
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
  }

  override fun close(website: Website) {
    websites.removeAll { it.url == website.url }
    broadcastDeleteWebHead(website)
    if (websites.isEmpty()) finish()
  }

  override fun share(website: Website) {
    startActivity(
      Intent.createChooser(
        TEXT_SHARE_INTENT.putExtra(EXTRA_TEXT, website.url),
        getString(R.string.share),
      ),
    )
  }

  override fun copy(website: Website) {
    copyToClipboard(website.safeLabel(), website.preferredUrl())
  }

  override fun copyAll() {
    copyToClipboard("Websites", websites.joinToString(", ") { it.preferredUrl() })
  }

  override fun shareAll(format: ShareAllFormat) {
    val intent = when (format) {
      ShareAllFormat.CommaSeparated ->
        TEXT_SHARE_INTENT.putExtra(EXTRA_TEXT, websites.joinToString(", ") { it.preferredUrl() })
      ShareAllFormat.NewLineSeparated ->
        TEXT_SHARE_INTENT.putExtra(EXTRA_TEXT, websites.joinToString("\n") { it.preferredUrl() })
      ShareAllFormat.List -> Intent().apply {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(
          Intent.EXTRA_STREAM,
          ArrayList(websites.mapNotNull { runCatching { Uri.parse(it.preferredUrl()) }.getOrNull() }),
        )
        type = "text/plain"
      }
    }
    startActivity(Intent.createChooser(intent, getString(R.string.share_all)))
  }

  override fun dismiss() {
    finish()
  }

  private fun copyToClipboard(label: String, url: String) {
    val clip = ClipData.newPlainText(label, url)
    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(clip)
    Toast.makeText(this, getString(R.string.copied) + " " + url, LENGTH_SHORT).show()
  }

  /** Receives head removals and site updates from the web head service. */
  private inner class WebHeadEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val website = IntentCompat.getParcelableExtra(intent, EXTRA_KEY_WEBSITE, Website::class.java) ?: return
      when (intent.action) {
        ACTION_EVENT_WEBHEAD_DELETED -> {
          websites.removeAll { it.url == website.url }
          if (websites.isEmpty()) finish()
        }
        ACTION_EVENT_WEBSITE_UPDATED -> {
          val index = websites.indexOfFirst { it.url == website.url }
          if (index != -1) websites[index] = website
        }
      }
    }
  }
}
