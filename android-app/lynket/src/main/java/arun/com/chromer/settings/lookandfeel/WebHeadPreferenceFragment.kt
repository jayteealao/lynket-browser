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
package arun.com.chromer.settings.lookandfeel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.Preferences.WEB_HEADS_COLOR
import arun.com.chromer.settings.Preferences.WEB_HEAD_ENABLED
import arun.com.chromer.settings.Preferences.WEB_HEAD_SIZE
import arun.com.chromer.settings.Preferences.WEB_HEAD_SPAWN_LOCATION
import arun.com.chromer.settings.preferences.BasePreferenceFragment
import arun.com.chromer.settings.widgets.ColorPreference
import arun.com.chromer.settings.widgets.IconListPreference
import arun.com.chromer.shared.Constants
import arun.com.chromer.shared.Constants.EXTRA_KEY_WEBHEAD_COLOR
import com.afollestad.materialdialogs.color.ColorChooserDialog
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

class WebHeadPreferenceFragment : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

  private val SUMMARY_GROUP = arrayOf(
    WEB_HEAD_SPAWN_LOCATION,
    WEB_HEADS_COLOR,
    WEB_HEAD_SIZE
  )

  private val webHeadColorFilter = IntentFilter(Constants.ACTION_WEBHEAD_COLOR_SET)
  private val colorSelectionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val selectedColor = intent.getIntExtra(EXTRA_KEY_WEBHEAD_COLOR, 0)
      if (selectedColor != 0) {
        val preference = findPreference<ColorPreference>(WEB_HEADS_COLOR)
        preference?.setColor(selectedColor)
      }
    }
  }

  private var webHeadColor: ColorPreference? = null
  private var spawnLocation: IconListPreference? = null
  private var webHeadSize: IconListPreference? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.webhead_preferences)
    init()
    setIcons()
    setupWebHeadColorPreference()
  }

  override fun onResume() {
    super.onResume()
    registerReceiver(colorSelectionReceiver, webHeadColorFilter)
    updatePreferenceStates(WEB_HEAD_ENABLED)
    updatePreferenceSummary(*SUMMARY_GROUP)
  }

  override fun onPause() {
    unregisterReceiver(colorSelectionReceiver)
    super.onPause()
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
    updatePreferenceStates(key)
    updatePreferenceSummary(key)
  }

  private fun init() {
    webHeadColor = findPreference<ColorPreference>(WEB_HEADS_COLOR)
    spawnLocation = findPreference<IconListPreference>(WEB_HEAD_SPAWN_LOCATION)
    webHeadSize = findPreference<IconListPreference>(WEB_HEAD_SIZE)
  }

  private fun setIcons() {
    val materialLight = ContextCompat.getColor(requireActivity(), R.color.material_dark_light)
    webHeadColor?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_palette)
      .color(materialLight)
      .sizeDp(24)
    spawnLocation?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_code_tags)
      .color(materialLight)
      .sizeDp(24)
    webHeadSize?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_crop_free)
      .color(materialLight)
      .sizeDp(24)
  }

  private fun setupWebHeadColorPreference() {
    webHeadColor?.onPreferenceClickListener = androidx.preference.Preference.OnPreferenceClickListener { preference ->
      val chosenColor = (preference as ColorPreference).getColor()
      ColorChooserDialog.Builder(requireActivity(), R.string.web_heads_color)
        .titleSub(R.string.web_heads_color)
        .allowUserColorInputAlpha(false)
        .preselect(chosenColor)
        .dynamicButtonColor(false)
        .show(fragmentManager)
      true
    }
  }

  private fun updatePreferenceStates(key: String?) {
    if (key.equals(WEB_HEAD_ENABLED, ignoreCase = true)) {
      val webHeadsEnabled = Preferences.get(requireContext()).webHeads()
      enableDisablePreference(webHeadsEnabled, *SUMMARY_GROUP)
    }
  }

  companion object {
    @JvmStatic
    fun newInstance(): WebHeadPreferenceFragment {
      val fragment = WebHeadPreferenceFragment()
      val args = Bundle()
      fragment.arguments = args
      return fragment
    }
  }
}
