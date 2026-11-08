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

package arun.com.chromer.browsing.customtabs

import android.app.Activity
import android.os.Build
import android.os.Bundle
import arun.com.chromer.browsing.BrowsingActivity
import arun.com.chromer.bubbles.system.BubbleRefusedActivity
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants.EXTRA_KEY_TOOLBAR_COLOR
import arun.com.chromer.util.WebUrl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CustomTabActivity : BrowsingActivity() {
  /**
   * As soon as user presses back, this activity will get focus. We need to kill this activity else
   * user will see a ghost tab.
   */
  private var isLoaded = false

  /**
   * Whether the tab opened as a sheet. The sheet leaves the app the link came from in view behind
   * it, so this activity must not stay between the two: it would cover that app.
   */
  private var sheetLaunch = false

  @Inject
  lateinit var customTabs: CustomTabs

  /** Only web links go to the Custom Tab provider (no javascript:, file:, content: or intent:). */
  override fun acceptsLink(url: String): Boolean = WebUrl.hasWebScheme(url)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (isFinishing) return

    val toolbarColor = intent.getIntExtra(EXTRA_KEY_TOOLBAR_COLOR, 0)

    if (savedInstanceState == null) {
      sheetLaunch = customTabs
        .forUrl(intent.dataString!!)
        .toolbarColor(toolbarColor)
        .launch()
    } else finish()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    isLoaded = true
  }

  override fun onPause() {
    super.onPause()
    // The sheet is in front now; leave without an animation, so nothing moves behind the sheet.
    if (sheetLaunch && !isFinishing) {
      finish()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
      } else {
        @Suppress("DEPRECATION") // sdlc-debt: the call API 34 replaced, kept for older Android; drop it with minSdk 34
        overridePendingTransition(0, 0)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (isLoaded) {
      finish()
    }
  }

  /**
   * The provider opens its tab through a trampoline that lands on top of anything started now, so
   * the message waits until the tab covers this activity.
   */
  private var refusalPending = false

  override fun showRefusalMessage() {
    refusalPending = true
  }

  override fun onStop() {
    super.onStop()
    if (refusalPending) {
      refusalPending = false
      BubbleRefusedActivity.showIfRefused(this)
    }
  }

  override fun onWebsiteLoaded(website: Website) {

  }

}
