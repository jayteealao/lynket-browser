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

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.Preferences.ARTICLE_THEME
import arun.com.chromer.settings.Preferences.WEB_HEAD_ENABLED
import arun.com.chromer.settings.preferences.BasePreferenceFragment
import arun.com.chromer.settings.widgets.IconListPreference
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

class ArticlePreferenceFragment : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

  private val SUMMARY_GROUP = arrayOf(ARTICLE_THEME)
  private var spawnLocation: IconListPreference? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.article_preferences)
    init()
    setIcons()
  }

  override fun onResume() {
    super.onResume()
    updatePreferenceSummary(*SUMMARY_GROUP)
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
    updatePreferenceStates(key)
    updatePreferenceSummary(key)
  }

  private fun init() {
    spawnLocation = findPreference(ARTICLE_THEME)
  }

  private fun setIcons() {
    val materialLight = ContextCompat.getColor(requireActivity(), R.color.material_dark_light)
    spawnLocation?.icon = IconicsDrawable(requireActivity())
      .icon(CommunityMaterial.Icon.cmd_format_color_fill)
      .color(ContextCompat.getColor(requireActivity(), R.color.material_dark_light))
      .sizeDp(24)
  }

  private fun updatePreferenceStates(key: String?) {
    if (key.equals(WEB_HEAD_ENABLED, ignoreCase = true)) {
      val articleMode = Preferences.get(requireContext()).articleMode()
      enableDisablePreference(articleMode, *SUMMARY_GROUP)
    }
  }

  companion object {
    @JvmStatic
    fun newInstance(): ArticlePreferenceFragment {
      val fragment = ArticlePreferenceFragment()
      val args = Bundle()
      fragment.arguments = args
      return fragment
    }
  }
}
