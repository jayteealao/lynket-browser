/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
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

package arun.com.chromer.data.apps

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager.GET_META_DATA
import android.content.res.Resources
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import arun.com.chromer.R
import arun.com.chromer.shared.Constants.NO_COLOR
import arun.com.chromer.util.ColorUtil
import arun.com.chromer.util.Utils
import timber.log.Timber
import javax.inject.Inject

/**
 * Finds the colour of another app, for the toolbar of the links it opens: the primary colour of the
 * app's theme first, then the most common colour of its icon.
 *
 * It reads another app's resources and icon, so call it off the main thread. It never throws: an app
 * whose colour cannot be found, or that is not installed, gives [NO_COLOR].
 */
class AppColorExtractor @Inject constructor(private val application: Application) {

  /** The colour of [packageName], or [NO_COLOR] when it has none worth using. */
  fun extract(packageName: String): Int {
    if (isSkipped(packageName)) return NO_COLOR
    Timber.d("Extracting the colour of %s", packageName)
    var color = colorFromTheme(packageName)
    if (color == NO_COLOR) color = colorFromIcon(packageName)
    if (color == NO_COLOR) {
      Timber.d("No colour found for %s", packageName)
    } else {
      Timber.d("Extracted %d for %s", color, packageName)
    }
    return color
  }

  private fun isSkipped(app: String): Boolean {
    return app.equals(application.packageName, ignoreCase = true) ||
      app.equals("android", ignoreCase = true) ||
      app.isEmpty()
  }

  // Another app's theme attribute can only be found by its name.
  // sdlc-debt: getIdentifier is discouraged but is the only lookup for a foreign app's attribute; no upgrade path.
  @SuppressLint("DiscouragedApi")
  private fun colorFromTheme(packageName: String): Int {
    return try {
      val resources = application.packageManager.getResourcesForApplication(packageName)
      // Try the AppCompat primary colour first.
      val appCompatId = resources.getIdentifier("colorPrimary", "attr", packageName)
      if (appCompatId > 0) {
        val color = themedColor(resources, appCompatId, packageName)
        if (color != NO_COLOR) return color
      }
      // Then the platform primary colour.
      val platformId = resources.getIdentifier("android:colorPrimary", "attr", packageName)
      if (platformId > 0) {
        val color = themedColor(resources, platformId, packageName)
        if (color != NO_COLOR) return color
      }
      NO_COLOR
    } catch (e: Exception) {
      Timber.d(e, "No theme colour for %s", packageName)
      NO_COLOR
    }
  }

  private fun themedColor(resources: Resources, attributeId: Int, packageName: String): Int {
    val tempTheme = resources.newTheme()
    val themeId = application.packageManager
      .getPackageInfo(packageName, GET_META_DATA).applicationInfo?.theme ?: return NO_COLOR
    tempTheme.applyStyle(themeId, false)
    val array = tempTheme.obtainStyledAttributes(intArrayOf(attributeId))
    var color = array.getColor(0, NO_COLOR)
    array.recycle()
    if (color == ContextCompat.getColor(application, R.color.md_grey_100) ||
      color == ContextCompat.getColor(application, R.color.md_grey_900)
    ) {
      color = NO_COLOR
    }
    return color
  }

  private fun colorFromIcon(packageName: String): Int {
    return try {
      val iconBitmap = Utils.drawableToBitmap(application.packageManager.getApplicationIcon(packageName))
      val palette = Palette.from(iconBitmap)
        .clearFilters()
        .generate()
      preferredColorFromSwatches(palette)
    } catch (e: Exception) {
      Timber.d(e, "No icon colour for %s", packageName)
      NO_COLOR
    }
  }

  private fun preferredColorFromSwatches(palette: Palette): Int {
    val swatchList = ColorUtil.getSwatchListFromPalette(palette)
    val prominentSwatch = swatchList.maxWithOrNull { swatch1, swatch2 ->
      val a = swatch1?.population ?: 0
      val b = swatch2?.population ?: 0
      a - b
    }
    return prominentSwatch?.rgb ?: NO_COLOR
  }
}
