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
package arun.com.chromer.settings

import android.content.BroadcastReceiver
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import arun.com.chromer.R
import arun.com.chromer.settings.browsingmode.BrowsingModeActivity
import arun.com.chromer.settings.browsingoptions.BrowsingOptionsActivity
import arun.com.chromer.settings.lookandfeel.LookAndFeelActivity
import arun.com.chromer.shared.Constants
import arun.com.chromer.shared.base.activity.SubActivity
import arun.com.chromer.util.Utils
import butterknife.BindView
import butterknife.ButterKnife
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

class SettingsGroupActivity : SubActivity(), SettingsGroupAdapter.GroupItemClickListener {
  @BindView(R.id.toolbar)
  lateinit var toolbar: Toolbar
  @BindView(R.id.settings_list_view)
  lateinit var settingsListView: RecyclerView
  @BindView(R.id.set_default_card)
  lateinit var setDefaultCard: CardView
  @BindView(R.id.set_default_image)
  lateinit var setDefaultImage: ImageView

  private lateinit var adapter: SettingsGroupAdapter
  private var closeReceiver: BroadcastReceiver? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
    ButterKnife.bind(this)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    adapter = SettingsGroupAdapter(this)
    settingsListView.layoutManager = LinearLayoutManager(this)
    settingsListView.adapter = adapter
    settingsListView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    adapter.setGroupItemClickListener(this)

    setDefaultImage.setImageDrawable(
      IconicsDrawable(this)
        .icon(CommunityMaterial.Icon.cmd_auto_fix)
        .color(Color.WHITE)
        .sizeDp(24)
    )

    setDefaultCard.setOnClickListener {
      val defaultBrowser = Utils.getDefaultBrowserPackage(applicationContext)
      if (defaultBrowser.equals("android", ignoreCase = true) ||
        defaultBrowser.startsWith("org.cyanogenmod") ||
        defaultBrowser.equals("com.huawei.android.internal.app", ignoreCase = true)
      ) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.GOOGLE_URL)))
      } else {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$defaultBrowser")
        Toast.makeText(
          this@SettingsGroupActivity,
          "${Utils.getAppNameWithPackage(applicationContext, defaultBrowser)} ${getString(R.string.default_clear_msg)}",
          Toast.LENGTH_LONG
        ).show()
        startActivity(intent)
      }
    }
    updateDefaultBrowserCard()
  }

  override fun onResume() {
    super.onResume()
    updateDefaultBrowserCard()
  }

  override fun onDestroy() {
    adapter.cleanUp()
    closeReceiver?.let {
      LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
    }
    super.onDestroy()
  }

  private fun updateDefaultBrowserCard() {
    setDefaultCard.visibility = if (!Utils.isDefaultBrowser(this)) {
      View.VISIBLE
    } else {
      View.GONE
    }
  }

  override fun onGroupItemClicked(position: Int, view: View) {
    when (position) {
      0 -> startActivity(Intent(this, BrowsingModeActivity::class.java))
      1 -> startActivity(Intent(this, LookAndFeelActivity::class.java))
      2 -> startActivity(Intent(this, BrowsingOptionsActivity::class.java))
    }
  }
}
