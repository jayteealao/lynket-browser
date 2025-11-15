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

// Phase 7: Converted from Java to Kotlin

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */

package arun.com.chromer.util

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import timber.log.Timber

/**
 * External applications can pass values into Intents that can cause us to crash: in defense,
 * we wrap [Intent] and catch the exceptions they may force us to throw.
 *
 * Source taken from Firefox.
 * https://hg.mozilla.org/releases/mozilla-aurora/file/46c6e8bb7f6f/mobile/android/base/java/org/mozilla/gecko/mozglue/SafeIntent.java
 */
class SafeIntent(private val intent: Intent) {

    fun hasExtra(name: String): Boolean {
        return try {
            intent.hasExtra(name)
        } catch (e: OutOfMemoryError) {
            Timber.w("Couldn't determine if intent had an extra: OOM. Malformed?")
            false
        } catch (e: RuntimeException) {
            Timber.w(e, "Couldn't determine if intent had an extra. ")
            false
        }
    }

    fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean {
        return try {
            intent.getBooleanExtra(name, defaultValue)
        } catch (e: OutOfMemoryError) {
            Timber.w("Couldn't get intent extras: OOM. Malformed?")
            defaultValue
        } catch (e: RuntimeException) {
            Timber.w(e, "Couldn't get intent extras.")
            defaultValue
        }
    }

    fun getStringExtra(name: String): String? {
        return try {
            intent.getStringExtra(name)
        } catch (e: OutOfMemoryError) {
            Timber.w("Couldn't get intent extras: OOM. Malformed?")
            null
        } catch (e: RuntimeException) {
            Timber.w(e, "Couldn't get intent extras.")
            null
        }
    }

    fun getBundleExtra(name: String): Bundle? {
        return try {
            intent.getBundleExtra(name)
        } catch (e: OutOfMemoryError) {
            Timber.w("Couldn't get intent extras: OOM. Malformed?")
            null
        } catch (e: RuntimeException) {
            Timber.w(e, "Couldn't get intent extras.")
            null
        }
    }

    val action: String?
        get() = intent.action

    val dataString: String?
        get() = try {
            intent.dataString
        } catch (e: OutOfMemoryError) {
            Timber.w("Couldn't get intent data string: OOM. Malformed?")
            null
        } catch (e: RuntimeException) {
            Timber.w(e, "Couldn't get intent data string.")
            null
        }

    fun getStringArrayListExtra(name: String): ArrayList<String>? {
        return try {
            intent.getStringArrayListExtra(name)
        } catch (e: OutOfMemoryError) {
            Timber.w("Couldn't get intent data string: OOM. Malformed?")
            null
        } catch (e: RuntimeException) {
            Timber.w(e, "Couldn't get intent data string.")
            null
        }
    }

    val data: Uri?
        get() = try {
            intent.data
        } catch (e: OutOfMemoryError) {
            Timber.w("Couldn't get intent data: OOM. Malformed?")
            null
        } catch (e: RuntimeException) {
            Timber.w(e, "Couldn't get intent data.")
            null
        }

    val unsafe: Intent
        get() = intent
}
