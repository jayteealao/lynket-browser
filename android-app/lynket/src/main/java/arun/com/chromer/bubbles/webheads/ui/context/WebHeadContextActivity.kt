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
import android.view.View.GONE
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.cardview.widget.CardView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.afollestad.materialdialogs.MaterialDialog
import javax.inject.Inject

class WebHeadContextActivity : BaseActivity(), WebsiteAdapter.WebSiteAdapterListener {
    private val webHeadsEventsReceiver = WebHeadEventsReceiver()

    lateinit var websiteListView: RecyclerView

    lateinit var copyAll: TextView

    lateinit var shareAll: TextView

    lateinit var rootCardView: CardView

    @Inject
    lateinit var tabsManager: TabsManager

    private lateinit var websitesAdapter: WebsiteAdapter

    override val layoutRes: Int
        get() = R.layout.activity_web_head_context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ButterKnife.bind(this)

        if (intent?.getParcelableArrayListExtra<Website>(EXTRA_KEY_WEBSITE) == null) {
            finish()
            return
        }
        val websites = intent.getParcelableArrayListExtra<Website>(EXTRA_KEY_WEBSITE)!!

        websitesAdapter = WebsiteAdapter(this, this)
        websitesAdapter.setWebsites(websites)

        websiteListView.layoutManager = LinearLayoutManager(this)
        websiteListView.adapter = websitesAdapter

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

    override fun onWebSiteItemClicked(website: Website) {
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

    override fun onWebSiteDelete(website: Website) {
        val shouldFinish = websitesAdapter.websites.isEmpty()
        if (shouldFinish) {
            rootCardView.visibility = GONE
            broadcastDeleteWebHead(website)
            finish()
        } else {
            broadcastDeleteWebHead(website)
        }
    }

    override fun onWebSiteShare(website: Website) {
        startActivity(
            Intent.createChooser(
                TEXT_SHARE_INTENT.putExtra(EXTRA_TEXT, website.url),
                getString(R.string.share)
            )
        )
    }

    override fun onWebSiteLongClicked(website: Website) {
        copyToClipboard(website.safeLabel(), website.preferredUrl())
    }

    fun onCopyAllClick() {
        copyToClipboard("Websites", getCSVUrls().toString())
    }

    fun onShareAllClick() {
        val items = arrayOf<CharSequence>(
            getString(R.string.comma_separated),
            getString(R.string.new_line_separated),
            getString(R.string.share_all_list)
        )
        MaterialDialog.Builder(this)
            .title(R.string.choose_share_method)
            .items(*items)
            .itemsCallbackSingleChoice(0) { _, _, which, _ ->
                when (which) {
                    0 -> startActivity(
                        Intent.createChooser(
                            TEXT_SHARE_INTENT.putExtra(EXTRA_TEXT, getCSVUrls().toString()),
                            getString(R.string.share_all)
                        )
                    )
                    1 -> startActivity(
                        Intent.createChooser(
                            TEXT_SHARE_INTENT.putExtra(EXTRA_TEXT, getNSVUrls().toString()),
                            getString(R.string.share_all)
                        )
                    )
                    else -> {
                        val webSites = ArrayList<Uri>()
                        for (website in websitesAdapter.websites) {
                            try {
                                webSites.add(Uri.parse(website.preferredUrl()))
                            } catch (ignored: Exception) {
                            }
                        }
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND_MULTIPLE
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, webSites)
                            type = "text/plain"
                        }
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_all)))
                    }
                }
                false
            }
            .show()
    }

    private fun getCSVUrls(): StringBuilder {
        val builder = StringBuilder()
        val websites = websitesAdapter.websites
        val size = websites.size
        for (i in 0 until size) {
            builder.append(websites[i].preferredUrl())
            if (i != size - 1) {
                builder.append(", ")
            }
        }
        return builder
    }

    private fun getNSVUrls(): StringBuilder {
        val builder = StringBuilder()
        val websites = websitesAdapter.websites
        val size = websites.size
        for (i in 0 until size) {
            builder.append(websites[i].preferredUrl())
            if (i != size - 1) {
                builder.append('\n')
            }
        }
        return builder
    }

    private fun copyToClipboard(label: String, url: String) {
        val clip = ClipData.newPlainText(label, url)
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copied) + " " + url, LENGTH_SHORT).show()
    }

        activityComponent.inject(this)
    }

    /**
     * This receiver is responsible for receiving events from web head service.
     */
    private inner class WebHeadEventsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_EVENT_WEBHEAD_DELETED -> {
                    val website = intent.getParcelableExtra<Website>(EXTRA_KEY_WEBSITE)
                    if (website != null) {
                        websitesAdapter.delete(website)
                    }
                }
                ACTION_EVENT_WEBSITE_UPDATED -> {
                    val web = intent.getParcelableExtra<Website>(EXTRA_KEY_WEBSITE)
                    if (web != null) {
                        websitesAdapter.update(web)
                    }
                }
            }
        }
    }
}
