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

package arun.com.chromer.bubbles.webheads

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import arun.com.chromer.bubbles.FloatingBubble
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants.EXTRA_KEY_FROM_AMP
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.shared.Constants.EXTRA_KEY_MINIMIZE
import arun.com.chromer.util.Utils
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WebHeadsFloatingBubble
@Inject
constructor(
  private val application: Application
) : FloatingBubble {

  private val mainHandler = Handler(Looper.getMainLooper())

  override fun openBubble(
    website: Website,
    fromMinimize: Boolean,
    fromAmp: Boolean,
    incognito: Boolean,
    context: Context?,
    color: Int
  ) {
    (context ?: application).let { ctx ->
      if (Utils.isOverlayGranted(ctx)) {
        val webHeadLauncher = Intent(ctx, WebHeadService::class.java).apply {
          data = website.preferredUri()
          addFlags(FLAG_ACTIVITY_NEW_TASK)
          putExtra(EXTRA_KEY_MINIMIZE, fromMinimize)
          putExtra(EXTRA_KEY_FROM_AMP, fromAmp)
          putExtra(EXTRA_KEY_INCOGNITO, incognito)
        }
        ContextCompat.startForegroundService(ctx, webHeadLauncher)
      } else {
        mainHandler.post {
          Utils.openDrawOverlaySettings(ctx)
        }
      }
    }
  }
}

