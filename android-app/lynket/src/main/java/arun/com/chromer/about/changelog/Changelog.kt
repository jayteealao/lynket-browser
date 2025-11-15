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

package arun.com.chromer.about.changelog

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.MaterialDialog
import arun.com.chromer.BuildConfig
import arun.com.chromer.R
import arun.com.chromer.util.Utils
import me.zhanghai.android.materialprogressbar.MaterialProgressBar

/**
 * Created by Arun on 25/12/2015.
 */
object Changelog {
    private const val PREF_VERSION_CODE_KEY = "version_code"
    private const val DOES_NT_EXIST = -1

    fun show(activity: Activity) {
        try {
            @SuppressLint("InflateParams")
            val content = LayoutInflater
                .from(activity)
                .inflate(R.layout.widget_changelog_layout, null) as FrameLayout
            val progress = content.findViewById<MaterialProgressBar>(R.id.changelog_progress)
            val webView = content.findViewById<WebView>(R.id.changelog_web_view)
            webView.loadData(activity.getString(R.string.changelog_text), "text/html", "utf-8")
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    content.removeView(progress)
                    webView.visibility = View.VISIBLE
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return super.shouldOverrideUrlLoading(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    // FIXME Sorry future me but I have to ship this today
                    if (url.equals("https://goo.gl/photos/BzRV69ABov9zJxVu9", ignoreCase = true) ||
                        url.equals("https://github.com/arunkumar9t2/lynket-browser/wiki/Android-10-Bubbles-Guide", ignoreCase = true)
                    ) {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        activity.startActivity(i)
                        return true
                    }
                    return super.shouldOverrideUrlLoading(view, url)
                }
            }
            MaterialDialog.Builder(activity)
                .customView(content, false)
                .title("Changelog")
                .positiveText(android.R.string.ok)
                .neutralText(R.string.rate_play_store)
                .onNeutral { _, _ -> Utils.openPlayStore(activity, activity.packageName) }
                .dismissListener { content.removeAllViews() }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, R.string.changelog_skipped, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shows the changelog dialog if necessary
     *
     * @param activity Activity for which it should be shown
     */
    fun conditionalShow(activity: Activity) {
        if (shouldShow(activity)) {
            show(activity)
            handleMigration(activity)
        }
    }

    private fun handleMigration(activity: Activity) {
        // Utils.deleteCache(activity).subscribe();
    }

    private fun shouldShow(context: Context): Boolean {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedVersionCode = prefs.getInt(PREF_VERSION_CODE_KEY, DOES_NT_EXIST)
        prefs.edit().putInt(PREF_VERSION_CODE_KEY, currentVersionCode).apply()
        return when {
            currentVersionCode == savedVersionCode -> false // This is just a normal run
            savedVersionCode == DOES_NT_EXIST -> true // This is a new install (or the user cleared the shared preferences)
            currentVersionCode > savedVersionCode -> true // This is an upgrade
            else -> false
        }
    }
}
