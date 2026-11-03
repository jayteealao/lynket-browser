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

package arun.com.chromer.bubbles.webheads.qs

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import arun.com.chromer.MainActivity
import arun.com.chromer.R
import arun.com.chromer.shared.base.PreferenceQuickSettingsTile
import arun.com.chromer.ui.navigation.Destination
import arun.com.chromer.util.Utils
import arun.com.chromer.data.preferences.BrowsingMode
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Created by Arun on 09/09/2016.
 */

@RequiresApi(api = Build.VERSION_CODES.N)
@AndroidEntryPoint
class WebHeadTile : PreferenceQuickSettingsTile() {
  /**
   * Turns web heads on as the only browsing mode, or back to slide-over. Web heads cannot show
   * without the permission to draw over other apps, so without it the mode stays as it is and the
   * Browsing mode screen opens, where choosing Web heads asks for the permission.
   */
  override fun togglePreference() {
    when {
      preference() -> preferences.browsingMode(BrowsingMode.SlideOver)
      Utils.isOverlayGranted(this) -> preferences.browsingMode(BrowsingMode.WebHeads)
      else -> openBrowsingModeSettings()
    }
  }

  private fun openBrowsingModeSettings() {
    val intent = Intent(this, MainActivity::class.java)
      .putExtra(Destination.EXTRA, "BrowsingMode")
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ContextCompat.getMainExecutor(this).execute {
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          startActivityAndCollapse(
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
          )
        } else {
          @Suppress("DEPRECATION")
          startActivityAndCollapse(intent)
        }
      }.onFailure { Timber.e(it, "The Browsing mode screen could not be opened from the tile") }
    }
  }

  override fun activeLabel(): String = label()

  override fun inActiveIcon(): Icon = icon()

  override fun activeIcon(): Icon = icon()

  override fun inActiveLabel(): String = label()

  override fun preference(): Boolean = preferences.browsingMode() == BrowsingMode.WebHeads

  private fun label() = getString(R.string.web_heads)
  private fun icon() = Icon.createWithResource(this, R.drawable.ic_bubble_chart_white_24dp)
}
