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

package arun.com.chromer.browsing.customtabs.callbacks

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences

/**
 * Phase 7: Converted from Java to Kotlin
 *
 * BroadcastReceiver that shares URL to favorite share app from Custom Tab.
 */
class FavShareBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.dataString
        if (url != null) {
            val openAppIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName.unflattenFromString(Preferences.get(context).favShareComponent() ?: "")
            }
            try {
                context.startActivity(openAppIntent)
            } catch (e: Exception) {
                defaultShare(context, url)
            }
        } else {
            Toast.makeText(context, context.getString(R.string.unsupported_link), Toast.LENGTH_SHORT).show()
        }
    }

    private fun defaultShare(context: Context, url: String) {
        Toast.makeText(context, context.getString(R.string.share_failed_msg), Toast.LENGTH_SHORT).show()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooserIntent = Intent.createChooser(shareIntent, context.getString(R.string.share_via)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooserIntent)
    }
}
