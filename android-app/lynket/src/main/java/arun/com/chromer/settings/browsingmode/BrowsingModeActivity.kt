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
package arun.com.chromer.settings.browsingmode

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.di.activity.ActivityComponent
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.RxPreferences
import arun.com.chromer.shared.base.activity.BaseActivity
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.Utils
import butterknife.BindView
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import javax.inject.Inject

class BrowsingModeActivity : BaseActivity(), BrowsingModeAdapter.BrowsingModeClickListener {

  @BindView(R.id.toolbar)
  lateinit var toolbar: Toolbar
  @BindView(R.id.browsing_mode_list_view)
  lateinit var browsingModeListView: RecyclerView
  @BindView(R.id.coordinatorLayout)
  lateinit var coordinatorLayout: CoordinatorLayout

  @Inject
  lateinit var rxPreferences: RxPreferences

  @Inject
  lateinit var adapter: BrowsingModeAdapter

  @Inject
  lateinit var tabsManager: TabsManager

  override val layoutRes: Int
    get() = R.layout.activity_browsing_mode

  override fun inject(activityComponent: ActivityComponent) {
    activityComponent.inject(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    browsingModeListView.layoutManager = LinearLayoutManager(this)
    browsingModeListView.adapter = adapter
    adapter.setBrowsingModeClickListener(this)

    supportFragmentManager
      .beginTransaction()
      .replace(R.id.browse_faster_preferences_container, BrowseFasterPreferenceFragment.newInstance())
      .commit()
  }

  override fun onDestroy() {
    adapter.cleanUp()
    super.onDestroy()
  }

  override fun onResume() {
    super.onResume()
    adapter.notifyDataSetChanged()
  }

  override fun onModeClicked(position: Int, view: View) {
    val webHeadsEnabled = position == BrowsingModeAdapter.WEB_HEADS
    val nativeBubbles = position == BrowsingModeAdapter.NATIVE_BUBBLES

    rxPreferences.nativeBubbles.set(nativeBubbles)
    Preferences.get(this).webHeads(webHeadsEnabled)

    if (webHeadsEnabled) {
      if (Utils.isOverlayGranted(this)) {
        Preferences.get(this).webHeads(true)
      } else {
        Preferences.get(this).webHeads(false)
        // Utils.openDrawOverlaySettings(this);
        val snackbar = Snackbar.make(coordinatorLayout, R.string.overlay_permission_content, Snackbar.LENGTH_INDEFINITE)
        snackbar.setAction(R.string.grant) {
          snackbar.dismiss()
          Utils.openDrawOverlaySettings(this@BrowsingModeActivity)
        }
        snackbar.show()
      }
    } else if (nativeBubbles) {
      MaterialDialog.Builder(this)
        .title(R.string.browsing_mode_native_bubbles)
        .content(R.string.browsing_mode_native_bubbles_warning)
        .positiveText(R.string.browsing_mode_native_bubbles_guide)
        .onPositive { _, _ ->
          tabsManager.openUrl(
            this,
            Website("https://github.com/arunkumar9t2/lynket-browser/wiki/Android-10-Bubbles-Guide"),
            true,
            false,
            false,
            false,
            false
          )
        }
        .icon(
          IconicsDrawable(this)
            .icon(CommunityMaterial.Icon.cmd_android_head)
            .colorRes(R.color.material_dark_color)
            .sizeDp(24)
        ).show()
    }
    adapter.notifyDataSetChanged()
  }
}
