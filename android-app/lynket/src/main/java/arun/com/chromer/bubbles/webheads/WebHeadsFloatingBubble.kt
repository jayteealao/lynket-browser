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
import android.widget.Toast
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.bubbles.FloatingBubble
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants.EXTRA_KEY_FROM_AMP
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.shared.Constants.EXTRA_KEY_MINIMIZE
import arun.com.chromer.util.Utils
import arun.com.chromer.util.debug.DebugHooks
import timber.log.Timber
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WebHeadsFloatingBubble
@Inject
constructor(
  private val application: Application,
  debugHooks: Optional<DebugHooks>,
) : FloatingBubble {

  private val debugHooks = DebugHooks.of(debugHooks)

  private val mainHandler = Handler(Looper.getMainLooper())

  override fun openBubble(
    website: Website,
    fromMinimize: Boolean,
    fromAmp: Boolean,
    incognito: Boolean,
    context: Context?,
    color: Int
  ): Boolean {
    val ctx = context ?: application
    if (!Utils.isOverlayGranted(ctx)) {
      mainHandler.post {
        Utils.openDrawOverlaySettings(ctx)
      }
      return true
    }
    val webHeadLauncher = Intent(ctx, WebHeadService::class.java).apply {
      data = website.preferredUri()
      addFlags(FLAG_ACTIVITY_NEW_TASK)
      putExtra(EXTRA_KEY_MINIMIZE, fromMinimize)
      putExtra(EXTRA_KEY_FROM_AMP, fromAmp)
      putExtra(EXTRA_KEY_INCOGNITO, incognito)
    }
    try {
      if (debugHooks.webHeadStartRefused(ctx)) {
        throw IllegalStateException("Web head start refused by the debug fault switch")
      }
      ContextCompat.startForegroundService(ctx, webHeadLauncher)
      return true
    } catch (e: IllegalStateException) {
      // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException.
      Timber.e(e, "Web heads could not start")
    } catch (e: SecurityException) {
      Timber.e(e, "Web heads could not start")
    }
    mainHandler.post {
      Toast.makeText(ctx, R.string.web_heads_start_failed, Toast.LENGTH_LONG).show()
    }
    return false
  }
}
