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

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.preferences.BasePreferenceFragment
import arun.com.chromer.settings.widgets.IconCheckboxPreference
import arun.com.chromer.util.Utils
import com.afollestad.materialdialogs.MaterialDialog
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

class WebHeadOptionsFragment : BasePreferenceFragment() {

  private val SUMMARY_GROUP = arrayOf(
    Preferences.WEB_HEAD_SPAWN_LOCATION,
    Preferences.WEB_HEADS_COLOR,
    Preferences.WEB_HEAD_SIZE
  )

  private var closeOnOpen: IconCheckboxPreference? = null
  private var aggressiveLoading: IconCheckboxPreference? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.webhead_options)
    init()
    setIcons()
    setupAggressivePreference()
  }

  override fun onResume() {
    super.onResume()
    updatePreferenceStates(Preferences.WEB_HEAD_ENABLED)
    updatePreferenceSummary(*SUMMARY_GROUP)
    if (!Utils.isLollipopAbove()) {
      aggressiveLoading?.isVisible = false
    }
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
    updatePreferenceStates(key)
    updatePreferenceSummary(key)
    if (key.equals(Preferences.MERGE_TABS_AND_APPS, ignoreCase = true)) {
      if (!Preferences.get(requireContext()).mergeTabs()) {
        aggressiveLoading?.isChecked = false
      }
    }
  }

  private fun init() {
    closeOnOpen = findPreference(Preferences.WEB_HEAD_CLOSE_ON_OPEN) as? IconCheckboxPreference
    aggressiveLoading = findPreference(Preferences.AGGRESSIVE_LOADING) as? IconCheckboxPreference
  }

  private fun setIcons() {
    val materialLight = ContextCompat.getColor(requireActivity(), R.color.material_dark_light)
    closeOnOpen?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_close_circle_outline)
      .color(materialLight)
      .sizeDp(24)
    aggressiveLoading?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_fast_forward)
      .color(materialLight)
      .sizeDp(24)
  }

  private fun setupAggressivePreference() {
    aggressiveLoading?.setOnPreferenceChangeListener { _, newValue ->
      if (newValue as Boolean && !Preferences.get(requireContext()).mergeTabs()) {
        MaterialDialog.Builder(requireActivity())
          .title(R.string.aggresive_dia_title)
          .content(R.string.aggresive_dia_content)
          .positiveText(android.R.string.ok)
          .show()
        Preferences.get(requireContext()).mergeTabs(true)
      }
      true
    }
  }

  private fun updatePreferenceStates(key: String?) {
    if (key.equals(Preferences.WEB_HEAD_ENABLED, ignoreCase = true)) {
      val webHeadsEnabled = Preferences.get(requireContext()).webHeads()
      enableDisablePreference(webHeadsEnabled, Preferences.WEB_HEAD_CLOSE_ON_OPEN, Preferences.AGGRESSIVE_LOADING)
    }
  }

  companion object {
    @JvmStatic
    fun newInstance(): WebHeadOptionsFragment {
      val fragment = WebHeadOptionsFragment()
      val args = Bundle()
      fragment.arguments = args
      return fragment
    }
  }
}
