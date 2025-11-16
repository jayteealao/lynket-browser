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

package arun.com.chromer.util

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import arun.com.chromer.appdetect.AppDetectService
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants

/**
 * Phase 7: Converted from Java to Kotlin
 *
 * Utility for managing app services like AppDetectService.
 * Kotlin object provides singleton pattern.
 */
object ServiceManager {

    fun takeCareOfServices(context: Context) {
        if (shouldRunAppDetection(context)) {
            startAppDetectionService(context)
        } else {
            stopAppDetectionService(context)
        }
    }

    private fun shouldRunAppDetection(context: Context): Boolean {
        return Preferences.get(context).isAppBasedToolbar() || Preferences.get(context).perAppSettings()
    }

    fun startAppDetectionService(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, AppDetectService::class.java).apply {
                putExtra(Constants.EXTRA_KEY_CLEAR_LAST_TOP_APP, true)
            }
        )
    }

    fun stopAppDetectionService(context: Context) {
        context.stopService(Intent(context, AppDetectService::class.java))
    }

    fun refreshCustomTabBindings(context: Context) {
        val intent = Intent(Constants.ACTION_REBIND_WEBHEAD_TAB_CONNECTION).apply {
            putExtra(Constants.EXTRA_KEY_REBIND_WEBHEAD_CXN, true)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    fun restartAppDetectionService(context: Context) {
        if (shouldRunAppDetection(context)) {
            stopAppDetectionService(context)
            startAppDetectionService(context)
        }
    }
}
