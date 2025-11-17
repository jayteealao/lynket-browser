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

// Phase 7: Converted to Kotlin
package arun.com.chromer.settings.preferences

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import arun.com.chromer.settings.widgets.ColorPreference
import arun.com.chromer.shared.base.Snackable

/**
 * Created by Arun on 02/03/2016.
 */
abstract class BasePreferenceFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

  override fun onCreatePreferences(bundle: Bundle?, s: String?) {
    // To be used by deriving classes
  }

  override fun onCreateRecyclerView(
    inflater: LayoutInflater,
    parent: ViewGroup,
    savedInstanceState: Bundle?
  ): RecyclerView {
    val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
    // Needed for not eating touch event when flinged.
    recyclerView.isNestedScrollingEnabled = false
    return recyclerView
  }

  protected fun enableDisablePreference(enabled: Boolean, vararg preferenceKeys: String) {
    for (preferenceKey in preferenceKeys) {
      val preference = findPreference(preferenceKey) as? Preference
      preference?.isEnabled = enabled
    }
  }

  protected fun updatePreferenceSummary(vararg preferenceKeys: String?) {
    for (key in preferenceKeys) {
      key?.let {
        val preference = preferenceScreen.findPreference(it) as? Preference
        when (preference) {
          is ListPreference -> {
            preference.summary = preference.entry
          }
          is ColorPreference -> {
            preference.refreshSummary()
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    sharedPreferences.registerOnSharedPreferenceChangeListener(this)
  }

  override fun onPause() {
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    super.onPause()
  }

  abstract override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)

  protected fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?) {
    if (receiver != null && filter != null) {
      localBroadcastManager.registerReceiver(receiver, filter)
    }
  }

  protected fun unregisterReceiver(receiver: BroadcastReceiver?) {
    receiver?.let {
      localBroadcastManager.unregisterReceiver(it)
    }
  }

  protected val localBroadcastManager: LocalBroadcastManager
    get() = LocalBroadcastManager.getInstance(requireActivity())

  protected fun snackLong(textToSnack: String) {
    if (activity is Snackable) {
      val snackable = activity as Snackable
      snackable.snackLong(textToSnack)
    }
  }

  protected val sharedPreferences: SharedPreferences
    get() = preferenceManager.sharedPreferences!!
}
