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
package arun.com.chromer.settings.browsingoptions

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.perapp.PerAppSettingsActivity
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.preferences.BasePreferenceFragment
import arun.com.chromer.settings.widgets.IconSwitchPreference
import arun.com.chromer.util.Utils
import com.afollestad.materialdialogs.MaterialDialog
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

/**
 * Created by Arun on 21/06/2016.
 */
class BehaviorPreferenceFragment : BasePreferenceFragment() {

  private var mergeTabsPreference: IconSwitchPreference? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.behavior_preferences)
    setupMergeTabsPreference()
    setupBlacklistPreference()
  }

  private fun setupBlacklistPreference() {
    val perAppSettingsPreference = findPreference(Preferences.PER_APP_PREFERENCE_DUMMY) as? IconSwitchPreference
    perAppSettingsPreference?.let {
      val recentImg = IconicsDrawable(requireActivity())
        .icon(CommunityMaterial.Icon.cmd_filter_variant)
        .color(ContextCompat.getColor(requireActivity(), R.color.material_dark_light))
        .sizeDp(24)
      it.icon = recentImg
      it.hideSwitch()
      it.onPreferenceClickListener = androidx.preference.Preference.OnPreferenceClickListener {
        Handler().postDelayed({
          val perAppSettingActivity = Intent(activity, PerAppSettingsActivity::class.java)
          startActivity(perAppSettingActivity)
        }, 150)
        false
      }
    }
  }

  private fun setupMergeTabsPreference() {
    mergeTabsPreference = findPreference(Preferences.MERGE_TABS_AND_APPS) as? IconSwitchPreference
    mergeTabsPreference?.let {
      val recentImg = IconicsDrawable(requireActivity())
        .icon(CommunityMaterial.Icon.cmd_animation)
        .color(ContextCompat.getColor(requireActivity(), R.color.material_dark_light))
        .sizeDp(24)
      it.icon = recentImg
      it.setOnPreferenceChangeListener { _, newValue ->
        if (!(newValue as Boolean) && Preferences.get(requireContext()).aggressiveLoading()) {
          MaterialDialog.Builder(requireActivity())
            .title(R.string.merge_tabs_off_title)
            .content(R.string.merget_tabs_off_content)
            .positiveText(android.R.string.ok)
            .show()
          Preferences.get(requireContext()).aggressiveLoading(false)
        }
        true
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (!Utils.isLollipopAbove()) {
      mergeTabsPreference?.isVisible = false
    }
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
    if (key.equals(Preferences.AGGRESSIVE_LOADING, ignoreCase = true)) {
      if (Preferences.get(requireContext()).aggressiveLoading()) {
        mergeTabsPreference?.isChecked = true
      }
    }
  }

  companion object {
    @JvmStatic
    fun newInstance(): BehaviorPreferenceFragment {
      val fragment = BehaviorPreferenceFragment()
      val args = Bundle()
      fragment.arguments = args
      return fragment
    }
  }
}
