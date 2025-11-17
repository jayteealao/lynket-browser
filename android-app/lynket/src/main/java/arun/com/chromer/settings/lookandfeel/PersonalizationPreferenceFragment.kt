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

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.preference.SwitchPreference
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.preferences.BasePreferenceFragment
import arun.com.chromer.settings.widgets.ColorPreference
import arun.com.chromer.settings.widgets.IconListPreference
import arun.com.chromer.settings.widgets.IconSwitchPreference
import arun.com.chromer.settings.widgets.SubCheckBoxPreference
import arun.com.chromer.shared.Constants.ACTION_TOOLBAR_COLOR_SET
import arun.com.chromer.shared.Constants.EXTRA_KEY_TOOLBAR_COLOR
import arun.com.chromer.shared.Constants.NO_COLOR
import arun.com.chromer.util.ServiceManager
import arun.com.chromer.util.Utils
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.ColorChooserDialog
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

class PersonalizationPreferenceFragment : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

  private val SUMMARY_GROUP = arrayOf(
    Preferences.ANIMATION_SPEED,
    Preferences.ANIMATION_TYPE,
    Preferences.PREFERRED_ACTION,
    Preferences.TOOLBAR_COLOR
  )

  private val toolbarColorSetFilter = IntentFilter(ACTION_TOOLBAR_COLOR_SET)
  private val colorSelectionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val selectedColor = intent.getIntExtra(EXTRA_KEY_TOOLBAR_COLOR, NO_COLOR)
      if (selectedColor != NO_COLOR) {
        val preference = findPreference(Preferences.TOOLBAR_COLOR) as? ColorPreference
        preference?.setColor(selectedColor)
      }
    }
  }

  private var dynamicColorPreference: IconSwitchPreference? = null
  private var coloredToolbarPreference: IconSwitchPreference? = null
  private var toolbarColorPreference: ColorPreference? = null
  private var animationSpeedPreference: IconListPreference? = null
  private var openingAnimationPreference: IconListPreference? = null
  private var preferredActionPreference: IconListPreference? = null
  private var dynamicAppPreference: SubCheckBoxPreference? = null
  private var dynamicWebPreference: SubCheckBoxPreference? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.personalization_preferences)
    // init and set icon
    init()
    setupIcons()
    // setup preferences after creation
    setupToolbarColorPreference()
    setupDynamicToolbar()
  }

  override fun onResume() {
    super.onResume()
    registerReceiver(colorSelectionReceiver, toolbarColorSetFilter)
    updatePreferenceStates(Preferences.TOOLBAR_COLOR_PREF)
    updatePreferenceStates(Preferences.ANIMATION_TYPE)
    updatePreferenceStates(Preferences.DYNAMIC_COLOR)
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
    dynamicColorPreference = findPreference(Preferences.DYNAMIC_COLOR) as? IconSwitchPreference
    coloredToolbarPreference = findPreference(Preferences.TOOLBAR_COLOR_PREF) as? IconSwitchPreference
    toolbarColorPreference = findPreference(Preferences.TOOLBAR_COLOR) as? ColorPreference
    preferredActionPreference = findPreference(Preferences.PREFERRED_ACTION) as? IconListPreference
    openingAnimationPreference = findPreference(Preferences.ANIMATION_TYPE) as? IconListPreference
    animationSpeedPreference = findPreference(Preferences.ANIMATION_SPEED) as? IconListPreference
    dynamicAppPreference = findPreference(Preferences.DYNAMIC_COLOR_APP) as? SubCheckBoxPreference
    dynamicWebPreference = findPreference(Preferences.DYNAMIC_COLOR_WEB) as? SubCheckBoxPreference
  }

  private fun setupIcons() {
    val palette = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_palette)
      .color(ContextCompat.getColor(requireActivity(), R.color.material_dark_light))
      .sizeDp(24)
    toolbarColorPreference?.icon = palette
    coloredToolbarPreference?.icon = palette
    dynamicColorPreference?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_format_color_fill)
      .color(ContextCompat.getColor(requireActivity(), R.color.material_dark_light))
      .sizeDp(24)
    preferredActionPreference?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_heart)
      .color(ContextCompat.getColor(requireActivity(), R.color.material_dark_light))
      .sizeDp(24)
    openingAnimationPreference?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_image_filter_none)
      .color(ContextCompat.getColor(requireActivity(), R.color.material_dark_light))
      .sizeDp(24)
    animationSpeedPreference?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_speedometer)
      .color(ContextCompat.getColor(requireActivity(), R.color.material_dark_light))
      .sizeDp(24)
  }

  private fun updatePreferenceStates(key: String?) {
    if (key.equals(Preferences.TOOLBAR_COLOR_PREF, ignoreCase = true)) {
      val coloredToolbar = Preferences.get(requireContext()).isColoredToolbar()
      enableDisablePreference(coloredToolbar, Preferences.TOOLBAR_COLOR, Preferences.DYNAMIC_COLOR)
      if (!coloredToolbar) {
        dynamicColorPreference?.isChecked = false
      }
    } else if (key.equals(Preferences.ANIMATION_TYPE, ignoreCase = true)) {
      val animationEnabled = Preferences.get(requireContext()).isAnimationEnabled()
      enableDisablePreference(animationEnabled, Preferences.ANIMATION_SPEED)
    } else if (key.equals(Preferences.DYNAMIC_COLOR, ignoreCase = true) ||
      key.equals(Preferences.DYNAMIC_COLOR_APP, ignoreCase = true) ||
      key.equals(Preferences.DYNAMIC_COLOR_WEB, ignoreCase = true)
    ) {
      val dynamicColor = Preferences.get(requireContext()).dynamicToolbar()
      if (!dynamicColor) {
        dynamicAppPreference?.isVisible = false
        dynamicWebPreference?.isVisible = false
        dynamicAppPreference?.isChecked = false
        dynamicWebPreference?.isChecked = false
      } else {
        dynamicAppPreference?.isVisible = true
        dynamicWebPreference?.isVisible = true
      }
      if (key.equals(Preferences.DYNAMIC_COLOR_APP, ignoreCase = true)) {
        if (!Utils.canReadUsageStats(requireActivity())) {
          requestUsagePermission()
        }
        handleAppDetectionService()
      }
      updateDynamicSummary()
    }
  }

  private fun updateDynamicSummary() {
    dynamicColorPreference?.summary = Preferences.get(requireContext()).dynamicColorSummary()
    val isColoredToolbar = Preferences.get(requireContext()).isColoredToolbar()
    if (!isColoredToolbar) {
      dynamicColorPreference?.isChecked = false
    }
  }

  private fun setupDynamicToolbar() {
    dynamicColorPreference?.onPreferenceClickListener = androidx.preference.Preference.OnPreferenceClickListener { preference ->
      val switchCompat = preference as SwitchPreference
      val isChecked = switchCompat.isChecked
      if (isChecked) {
        MaterialDialog.Builder(requireActivity())
          .title(R.string.dynamic_toolbar_color)
          .content(R.string.dynamic_toolbar_help)
          .positiveText(android.R.string.ok)
          .show()
      }
      updateDynamicSummary()
      false
    }
  }

  private fun setupToolbarColorPreference() {
    toolbarColorPreference?.onPreferenceClickListener = androidx.preference.Preference.OnPreferenceClickListener { preference ->
      val chosenColor = (preference as ColorPreference).getColor()
      ColorChooserDialog.Builder(requireActivity(), R.string.default_toolbar_color)
        .titleSub(R.string.default_toolbar_color)
        .allowUserColorInputAlpha(false)
        .preselect(chosenColor)
        .dynamicButtonColor(false)
        .show(fragmentManager)
      true
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private fun requestUsagePermission() {
    MaterialDialog.Builder(requireActivity())
      .title(R.string.permission_required)
      .content(R.string.usage_permission_explanation_appcolor)
      .positiveText(R.string.grant)
      .onPositive { _, _ ->
        requireActivity().startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
      }
      .dismissListener {
        dynamicAppPreference?.isChecked = Utils.canReadUsageStats(requireContext())
      }
      .show()
  }

  private fun handleAppDetectionService() {
    if (Preferences.get(requireContext()).isAppBasedToolbar() || Preferences.get(requireContext()).perAppSettings()) {
      ServiceManager.startAppDetectionService(requireContext())
    } else {
      ServiceManager.stopAppDetectionService(requireContext())
    }
  }

  companion object {
    @JvmStatic
    fun newInstance(): PersonalizationPreferenceFragment {
      val fragment = PersonalizationPreferenceFragment()
      val args = Bundle()
      fragment.arguments = args
      return fragment
    }
  }
}
