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

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants.ACTION_TOOLBAR_COLOR_SET
import arun.com.chromer.shared.Constants.ACTION_WEBHEAD_COLOR_SET
import arun.com.chromer.shared.Constants.EXTRA_KEY_TOOLBAR_COLOR
import arun.com.chromer.shared.Constants.EXTRA_KEY_WEBHEAD_COLOR
import arun.com.chromer.shared.base.Snackable
import com.afollestad.materialdialogs.color.ColorChooserDialog
import com.google.android.material.snackbar.Snackbar

class LookAndFeelActivity : AppCompatActivity(), ColorChooserDialog.ColorCallback, Snackable, SharedPreferences.OnSharedPreferenceChangeListener {
  lateinit var toolbar: Toolbar
  lateinit var coordinatorLayout: CoordinatorLayout
  lateinit var error: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_look_and_feel)
    ButterKnife.bind(this)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    supportFragmentManager
      .beginTransaction()
      .replace(R.id.toolbar_options_preferences_container, PersonalizationPreferenceFragment.newInstance())
      .replace(R.id.web_head_options_preferences_container, WebHeadPreferenceFragment.newInstance())
      .replace(R.id.article_options_preferences_container, ArticlePreferenceFragment.newInstance())
      .commit()
  }

  override fun onResume() {
    super.onResume()
    PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
    showHideErrorView()
  }

  private fun showHideErrorView() {
    error.visibility = if (!Preferences.get(this).webHeads()) {
      View.VISIBLE
    } else {
      View.GONE
    }
  }

  override fun onPause() {
    PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
    super.onPause()
  }

  override fun onColorSelection(dialog: ColorChooserDialog, selectedColor: Int) {
    when (dialog.title) {
      R.string.default_toolbar_color -> {
        val toolbarColorIntent = Intent(ACTION_TOOLBAR_COLOR_SET)
        toolbarColorIntent.putExtra(EXTRA_KEY_TOOLBAR_COLOR, selectedColor)
        LocalBroadcastManager.getInstance(this).sendBroadcast(toolbarColorIntent)
      }
      R.string.web_heads_color -> {
        val webHeadColorIntent = Intent(ACTION_WEBHEAD_COLOR_SET)
        webHeadColorIntent.putExtra(EXTRA_KEY_WEBHEAD_COLOR, selectedColor)
        LocalBroadcastManager.getInstance(this).sendBroadcast(webHeadColorIntent)
      }
    }
  }

  override fun onColorChooserDismissed(dialog: ColorChooserDialog) {
  }

  override fun snack(message: String) {
    Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_SHORT).show()
  }

  override fun snackLong(message: String) {
    Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show()
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
    if (Preferences.WEB_HEAD_ENABLED.equals(key, ignoreCase = true)) {
      showHideErrorView()
    }
  }
}
