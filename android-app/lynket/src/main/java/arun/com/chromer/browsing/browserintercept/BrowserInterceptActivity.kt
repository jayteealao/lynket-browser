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

package arun.com.chromer.browsing.browserintercept

import arun.com.chromer.ui.browsing.isShowingSecondaryBrowserError
import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.lifecycle.lifecycleScope
import arun.com.chromer.R
import arun.com.chromer.extenstions.finishAndRemoveTaskCompat
import arun.com.chromer.shared.base.activity.BaseActivity
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.SafeIntent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("GoogleAppIndexingApiWarning")
@AndroidEntryPoint
class BrowserInterceptActivity : BaseActivity() {
  @Inject
  lateinit var defaultTabsManager: TabsManager

  override val layoutRes: Int get() = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    intent?.let {
      val safeIntent = SafeIntent(intent)
      if (safeIntent.data == null) {
        invalidLink()
        return
      }
      lifecycleScope.launch {
        defaultTabsManager.processIncomingIntent(this@BrowserInterceptActivity, intent)
        // A blacklist error dialog keeps this activity open; closing the dialog finishes it.
        if (!isShowingSecondaryBrowserError) {
          finishAndRemoveTaskCompat()
        }
      }
    } ?: run {
      finishAndRemoveTaskCompat()
    }

  }

  private fun invalidLink() {
    Toast.makeText(this, getString(R.string.unsupported_link), LENGTH_SHORT).show()
    finishAndRemoveTaskCompat()
  }
}
