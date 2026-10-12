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

package arun.com.chromer.browsing.article

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import arun.com.chromer.R
import arun.com.chromer.ui.reader.ImageViewerScreen
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.util.Utils

/**
 * Full-size image from an article, with pinch zoom. Pass the image address with .setData(url).
 * Close returns to the reader; Share sends the image address.
 */
class ImageViewActivity : ComponentActivity() {

  public override fun onCreate(savedInstanceState: Bundle?) {
    val dark = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
    super.onCreate(savedInstanceState)
    val url = intent?.dataString
    if (url.isNullOrBlank()) {
      Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
      finish()
      return
    }
    setContent {
      ChromerTheme(themeMode = ThemeMode.Dark) {
        ImageViewerScreen(
          url = url,
          onClose = ::finish,
          onShare = { Utils.shareText(this, url) },
          modifier = Modifier.semantics { testTagsAsResourceId = true },
        )
      }
    }
  }
}
