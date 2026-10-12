/*
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

import arun.com.chromer.settings.Preferences

/**
 * When the changelog shows: on a new install and after an upgrade, once. The screen itself is the
 * Changelog destination (`ui/changelog/ChangelogDialog.kt`).
 */
object Changelog {
  private const val DOES_NOT_EXIST = -1

  /** True once per new install or higher version code; saves [currentVersionCode] as seen. */
  fun shouldShow(preferences: Preferences, currentVersionCode: Int): Boolean {
    val savedVersionCode = preferences.versionCode()
    if (savedVersionCode != currentVersionCode) preferences.versionCode(currentVersionCode)
    return shouldShow(savedVersionCode, currentVersionCode)
  }

  internal fun shouldShow(savedVersionCode: Int, currentVersionCode: Int): Boolean = when {
    currentVersionCode == savedVersionCode -> false // a normal start
    savedVersionCode == DOES_NOT_EXIST -> true // a new install, or the settings were cleared
    currentVersionCode > savedVersionCode -> true // an upgrade
    else -> false
  }
}
