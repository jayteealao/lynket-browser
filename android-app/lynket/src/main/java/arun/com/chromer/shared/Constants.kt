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

package arun.com.chromer.shared

import android.content.Intent
import android.net.Uri
import androidx.annotation.ColorInt

/**
 * Created by Arun on 04/01/2016.
 */
object Constants {
    @ColorInt
    const val NO_COLOR = -1

    // Package names
    const val CHROME_PACKAGE = "com.android.chrome"
    const val SYSTEM_WEBVIEW = "com.google.andorid.webview"

    // URL
    const val GOOGLE_URL = "https://www.google.com/"
    const val CUSTOM_TAB_URL = "https://developer.chrome.com/multidevice/android/customtabs#whentouse"
    const val APP_TESTING_URL = "https://play.google.com/apps/testing/arun.com.chromer"
    const val G_COMMUNITY_URL = "https://plus.google.com/communities/109754631011301174504"
    const val G_SEARCH_URL = "https://www.google.com/search?q="

    // Objects
    @JvmField
    val WEB_INTENT = Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_URL))

    @JvmField
    val TEXT_SHARE_INTENT = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "")

    @JvmField
    val DUMMY_INTENT = Intent("Namey McNameFace")

    // Misc
    const val MAILID = "arunk.beece@gmail.com"
    const val ME = "Arunkumar"
    const val LOCATION = "Tamilnadu, India"

    // Intent Actions
    const val ACTION_TOOLBAR_COLOR_SET = "ACTION_TOOLBAR_COLOR_SET"
    const val ACTION_WEBHEAD_COLOR_SET = "ACTION_WEBHEAD_COLOR_SET"
    const val ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT"
    const val ACTION_STOP_WEBHEAD_SERVICE = "close_service"
    const val ACTION_REBIND_WEBHEAD_TAB_CONNECTION = "rebind_event"
    const val ACTION_CLOSE_WEBHEAD_BY_URL = "ACTION_CLOSE_WEBHEAD_BY_URL"
    const val ACTION_MINIMIZE = "ACTION_MINIMIZE"
    const val ACTION_EVENT_WEBSITE_UPDATED = "ACTION_EVENT_WEBSITE_UPDATED"
    const val ACTION_EVENT_WEBHEAD_DELETED = "ACTION_EVENT_WEBHEAD_DELETED"
    const val ACTION_OPEN_CONTEXT_ACTIVITY = "ACTION_OPEN_CONTEXT_ACTIVITY"
    const val ACTION_OPEN_NEW_TAB = "ACTION_OPEN_NEW_TAB"

    // Extra keys
    const val EXTRA_KEY_FROM_WEBHEAD = "EXTRA_KEY_FROM_WEBHEAD"
    const val EXTRA_KEY_TOOLBAR_COLOR = "EXTRA_KEY_TOOLBAR_COLOR"
    const val EXTRA_KEY_WEBHEAD_COLOR = "EXTRA_KEY_WEBHEAD_COLOR"
    const val EXTRA_KEY_CLEAR_LAST_TOP_APP = "EXTRA_KEY_CLEAR_LAST_TOP_APP"
    const val EXTRA_KEY_REBIND_WEBHEAD_CXN = "EXTRA_KEY_REBIND_WEBHEAD_CXN"
    const val EXTRA_KEY_FROM_NEW_TAB = "EXTRA_KEY_FROM_NEW_TAB"
    const val EXTRA_KEY_WEBSITE = "EXTRA_KEY_WEBSITE"
    const val EXTRA_KEY_MINIMIZE = "EXTRA_KEY_MINIMIZE"
    const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
    const val EXTRA_KEY_ORIGINAL_URL = "EXTRA_KEY_ORIGINAL_URL"
    const val EXTRA_KEY_FROM_ARTICLE = "EXTRA_KEY_FROM_ARTICLE"
    const val EXTRA_KEY_FROM_AMP = "EXTRA_KEY_FROM_AMP"
    const val EXTRA_KEY_INCOGNITO = "EXTRA_KEY_INCOGNITO"

    // Request codes
    const val REQUEST_CODE_VOICE = 112
}
