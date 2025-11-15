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
package arun.com.chromer.settings.browsingmode

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.preferences.BasePreferenceFragment
import arun.com.chromer.settings.widgets.IconSwitchPreference
import com.afollestad.materialdialogs.MaterialDialog
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

class BrowseFasterPreferenceFragment : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

  private var dialog: MaterialDialog? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.browse_faster_options)
    setupAmpPreference()
    setupArticlePreference()
  }

  private fun setupArticlePreference() {
    val articleModePreference = findPreference<IconSwitchPreference>(Preferences.ARTICLE_MODE)
    articleModePreference?.let {
      val articleImg = IconicsDrawable(requireActivity())
        .icon(CommunityMaterial.Icon.cmd_file_document)
        .color(ContextCompat.getColor(requireActivity(), R.color.android_green))
        .sizeDp(24)
      it.icon = articleImg
      it.onPreferenceClickListener = androidx.preference.Preference.OnPreferenceClickListener { false }
      it.setOnPreferenceChangeListener { _, newValue ->
        showInformationDialog(Preferences.get(requireActivity()).ampMode(), newValue as Boolean)
        true
      }
    }
  }

  private fun setupAmpPreference() {
    val ampModePreference = findPreference<IconSwitchPreference>(Preferences.AMP_MODE)
    ampModePreference?.let {
      it.setIcon(R.drawable.ic_action_amp_icon)
      it.setOnPreferenceChangeListener { _, newValue ->
        val isSlideOver = !Preferences.get(requireContext()).webHeads()
        if (isSlideOver && newValue as Boolean) {
          MaterialDialog.Builder(requireActivity())
            .title(R.string.amp_warning_title)
            .content(R.string.amp_warning_content, true)
            .positiveText(android.R.string.ok)
            .iconRes(R.drawable.ic_action_amp_icon)
            .show()
        }
        showInformationDialog(newValue as Boolean, Preferences.get(requireActivity()).articleMode())
        true
      }
    }
  }

  private fun showInformationDialog(ampMode: Boolean, article: Boolean) {
    dismissDialog()
    if (ampMode && article) {
      dialog = MaterialDialog.Builder(requireActivity())
        .iconRes(R.drawable.ic_action_amp_icon)
        .title(R.string.attention)
        .content(R.string.amp_article_combined_explanation, true)
        .positiveText(android.R.string.ok)
        .show()
    }
  }

  private fun dismissDialog() {
    dialog?.dismiss()
  }

  override fun onResume() {
    super.onResume()
    sharedPreferences.registerOnSharedPreferenceChangeListener(this)
  }

  override fun onPause() {
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    super.onPause()
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
  }

  companion object {
    @JvmStatic
    fun newInstance(): BrowseFasterPreferenceFragment {
      val fragment = BrowseFasterPreferenceFragment()
      val args = Bundle()
      fragment.arguments = args
      return fragment
    }
  }
}
