/*
 *
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
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

package arun.com.chromer.bubbles.webheads

import android.content.Context
import arun.com.chromer.appdetect.LinkSource
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import dagger.Lazy
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a web heads link goes when the overlay permission ("Display over other apps") is off.
 *
 * The link opens in a browsing tab and keeps its incognito flag, so it is never lost. This path
 * never opens the overlay settings page: that page opens only when a person taps to open it.
 * Every web heads entry that finds the permission off hands its link here.
 */
@Singleton
class OverlayRefusal
@Inject
constructor(
  // Lazy: the tabs manager reaches this class through itself and the bubble factory.
  private val tabsManager: Lazy<TabsManager>,
) {
  fun refuse(
    context: Context,
    website: Website,
    incognito: Boolean,
    linkSource: LinkSource = LinkSource.NONE,
  ) {
    // No address in the log: the event is enough to see the refusal on a device.
    Timber.w("Overlay permission is off; the link opens in a tab")
    tabsManager.get().openBrowsingTab(
      context,
      website,
      smart = true,
      fromNewTab = false,
      incognito = incognito,
      linkSource = linkSource,
    )
  }
}
