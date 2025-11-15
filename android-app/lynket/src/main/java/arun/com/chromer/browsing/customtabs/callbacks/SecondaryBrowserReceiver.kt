/*
 * Phase 7: Converted from Java to Kotlin
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

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import timber.log.Timber

class SecondaryBrowserReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.dataString
        if (url != null) {
            val webIntentExplicit = Intent(ACTION_VIEW, Uri.parse(url))
            webIntentExplicit.flags = FLAG_ACTIVITY_NEW_TASK
            val componentFlatten = Preferences.get(context).secondaryBrowserComponent()
            if (componentFlatten != null) {
                val cN = ComponentName.unflattenFromString(componentFlatten)
                webIntentExplicit.component = cN
                try {
                    context.startActivity(webIntentExplicit)
                } catch (e: ActivityNotFoundException) {
                    launchComponentWithIteration(context, url)
                }
            } else {
                showChooser(context, url)
            }
        } else {
            Toast.makeText(context, context.getString(R.string.unsupported_link), Toast.LENGTH_LONG).show()
        }
    }

    private fun launchComponentWithIteration(context: Context, url: String) {
        Timber.d("Attempting to launch activity with iteration")
        val webIntentImplicit = Intent(ACTION_VIEW, Uri.parse(url))
        webIntentImplicit.flags = FLAG_ACTIVITY_NEW_TASK
        @SuppressLint("InlinedApi")
        val resolvedActivityList = context.packageManager.queryIntentActivities(
            webIntentImplicit,
            PackageManager.MATCH_ALL
        )
        val secondaryPackage = Preferences.get(context).secondaryBrowserPackage()
        if (secondaryPackage != null) {
            var found = false
            for (info in resolvedActivityList) {
                if (info.activityInfo.packageName.equals(secondaryPackage, ignoreCase = true)) {
                    found = true
                    val componentName = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                    webIntentImplicit.component = componentName
                    // This will be the new component, so write it to preferences
                    Preferences.get(context).secondaryBrowserComponent(componentName.flattenToString())
                    context.startActivity(webIntentImplicit)
                    break
                }
            }
            if (!found) {
                showChooser(context, url)
            }
        }
    }

    private fun showChooser(context: Context, url: String) {
        Timber.d("Falling back to intent chooser")
        Toast.makeText(context, context.getString(R.string.unxp_err), Toast.LENGTH_SHORT).show()
        val implicitViewIntent = Intent(ACTION_VIEW, Uri.parse(url))
        val chooserIntent = Intent.createChooser(implicitViewIntent, context.getString(R.string.open_with))
        chooserIntent.flags = FLAG_ACTIVITY_NEW_TASK
        context.startActivity(chooserIntent)
    }
}
