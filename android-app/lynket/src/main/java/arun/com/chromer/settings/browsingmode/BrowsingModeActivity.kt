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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.RxPreferences
import arun.com.chromer.shared.base.activity.BaseActivity
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.permission.PermissionExplainerDialog
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import arun.com.chromer.util.Utils
import butterknife.BindView
import com.afollestad.materialdialogs.MaterialDialog
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BrowsingModeActivity : BaseActivity(), BrowsingModeAdapter.BrowsingModeClickListener {

  @BindView(R.id.toolbar)
  lateinit var toolbar: Toolbar
  @BindView(R.id.browsing_mode_list_view)
  lateinit var browsingModeListView: RecyclerView

  @Inject
  lateinit var rxPreferences: RxPreferences

  @Inject
  lateinit var adapter: BrowsingModeAdapter

  @Inject
  lateinit var tabsManager: TabsManager

  override val layoutRes: Int
    get() = R.layout.activity_browsing_mode

  /** Which permission explainer shows, if any. */
  private enum class Explainer { Overlay, WebHeadsNotifications, BubblesNotifications }

  private var explainer by mutableStateOf<Explainer?>(null)

  private val overlaySettings =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (Settings.canDrawOverlays(this)) {
        Preferences.get(this).webHeads(true)
        rxPreferences.nativeBubbles.set(false)
        adapter.notifyDataSetChanged()
        askForNotifications(Explainer.WebHeadsNotifications)
      }
    }

  private val notificationPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

    addContentView(
      ComposeView(this).apply {
        setContent {
          ChromerThemeFromSettings { ExplainerDialog() }
        }
      },
      ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
  }

  @Composable
  private fun ExplainerDialog() {
    val shown = explainer ?: return
    val dismiss = { explainer = null }
    when (shown) {
      Explainer.Overlay -> PermissionExplainerDialog(
        icon = Icons.Outlined.Layers,
        title = stringResource(R.string.overlay_explainer_title),
        body = stringResource(R.string.overlay_explainer_body),
        onGrant = {
          dismiss()
          openOverlaySettings()
        },
        onDismiss = dismiss,
      )
      Explainer.WebHeadsNotifications, Explainer.BubblesNotifications -> PermissionExplainerDialog(
        icon = Icons.Outlined.Notifications,
        title = stringResource(
          if (shown == Explainer.WebHeadsNotifications) R.string.web_heads_notification_explainer_title
          else R.string.bubbles_notification_explainer_title,
        ),
        body = stringResource(
          if (shown == Explainer.WebHeadsNotifications) R.string.web_heads_notification_explainer_body
          else R.string.bubbles_notification_explainer_body,
        ),
        onGrant = {
          dismiss()
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        },
        onDismiss = dismiss,
      )
    }
  }

  private fun openOverlaySettings() {
    try {
      overlaySettings.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    } catch (e: Exception) {
      // No settings screen for the permission on this device; the old path shows the message.
      Utils.openDrawOverlaySettings(this)
    }
  }

  /** On Android 13 and later, explains and asks for notifications when they are not allowed yet. */
  private fun askForNotifications(which: Explainer) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      explainer = which
    }
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
        askForNotifications(Explainer.WebHeadsNotifications)
      } else {
        Preferences.get(this).webHeads(false)
        explainer = Explainer.Overlay
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
        )
        .dismissListener { askForNotifications(Explainer.BubblesNotifications) }
        .show()
    }
    adapter.notifyDataSetChanged()
  }
}
