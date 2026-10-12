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

package arun.com.chromer.history

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import arun.com.chromer.ui.history.HistoryScreen
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint

/**
 * The History screen for callers outside the app's navigation: the Custom Tab menu and bottom bar
 * and the launcher shortcut. It shows the one Compose History screen; back returns to the caller.
 */
@AndroidEntryPoint
class HistoryActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      ChromerThemeFromSettings {
        Surface(
          modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
          color = MaterialTheme.colorScheme.background,
        ) {
          HistoryScreen(onBack = ::finish)
        }
      }
    }
  }
}
