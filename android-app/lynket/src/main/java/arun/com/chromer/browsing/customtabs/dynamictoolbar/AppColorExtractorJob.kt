/*
 * Phase 7: Converted from Java to Kotlin
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

package arun.com.chromer.browsing.customtabs.dynamictoolbar

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_META_DATA
import android.content.res.Resources
import androidx.annotation.ColorInt
import androidx.core.app.JobIntentService
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import arun.com.chromer.Lynket
import arun.com.chromer.R
import arun.com.chromer.data.apps.AppRepository
import arun.com.chromer.shared.Constants.EXTRA_PACKAGE_NAME
import arun.com.chromer.shared.Constants.NO_COLOR
import arun.com.chromer.util.ColorUtil
import arun.com.chromer.util.Utils
import timber.log.Timber
import javax.inject.Inject

class AppColorExtractorJob : JobIntentService() {

    @Inject
    lateinit var appRepository: AppRepository

    override fun onHandleWork(intent: Intent) {
        (application as Lynket).appComponent.inject(this)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName != null) {
            if (isValidPackage(packageName))
                return
            if (!extractColorFromResources(packageName)) {
                extractColorFromAppIcon(packageName)
            }
        }
    }

    private fun extractColorFromResources(packageName: String): Boolean {
        try {
            var color: Int
            val resources = packageManager.getResourcesForApplication(packageName)
            // Try to extract appcompat primary color value
            val appCompatId = resources.getIdentifier("colorPrimary", "attr", packageName)
            if (appCompatId > 0) {
                // Successful, let's get the themed value of this attribute
                color = getThemedColor(resources, appCompatId, packageName)
                if (color != NO_COLOR) {
                    saveColorToDb(packageName, color)
                    return true
                }
            }
            // If above was not successful, then attempt to get lollipop colorPrimary attribute
            val lollipopAttrId = resources.getIdentifier("android:colorPrimary", "attr", packageName)
            if (lollipopAttrId > 0) {
                // Found
                color = getThemedColor(resources, lollipopAttrId, packageName)
                if (color != NO_COLOR) {
                    saveColorToDb(packageName, color)
                    return true
                }
            }
            return false
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return false
        }
    }

    private fun getThemedColor(resources: Resources?, attributeId: Int, packageName: String): Int {
        if (resources == null || attributeId == 0) return -1
        try {
            // Create dummy theme
            val tempTheme = resources.newTheme()
            // Need the theme id to apply the theme, so let's get it.
            val themeId = packageManager?.getPackageInfo(packageName, GET_META_DATA)?.applicationInfo?.theme ?: return -1
            // Apply the theme
            tempTheme.applyStyle(themeId, false)
            // Attempt to get styled values now
            val array = tempTheme.obtainStyledAttributes(intArrayOf(attributeId))
            // Styled color
            var color = array.getColor(0, NO_COLOR)
            array.recycle()
            if (color == ContextCompat.getColor(this, R.color.md_grey_100) ||
                color == ContextCompat.getColor(this, R.color.md_grey_900)
            ) {
                color = NO_COLOR
            }
            return color
        } catch (e: PackageManager.NameNotFoundException) {
            return -1
        }
    }

    private fun extractColorFromAppIcon(packageName: String) {
        try {
            val iconBitmap = Utils.drawableToBitmap(packageManager.getApplicationIcon(packageName))
            val palette = Palette.from(iconBitmap)
                .clearFilters()
                .generate()
            val extractColor = getPreferredColorFromSwatches(palette)
            if (extractColor != NO_COLOR) {
                Timber.d("Extracted %d for %s", extractColor, packageName)
                try {
                    saveColorToDb(packageName, extractColor)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isValidPackage(app: String): Boolean {
        return app.equals(packageName, ignoreCase = true) ||
                app.equals("android", ignoreCase = true) ||
                app.isEmpty()
    }

    private fun getPreferredColorFromSwatches(palette: Palette): Int {
        val swatchList = ColorUtil.getSwatchListFromPalette(palette)
        val prominentSwatch = swatchList.maxWithOrNull { swatch1, swatch2 ->
            val a = swatch1?.population ?: 0
            val b = swatch2?.population ?: 0
            a - b
        }
        return prominentSwatch?.rgb ?: -1
    }

    private fun saveColorToDb(packageName: String, @ColorInt extractedColor: Int) {
        try {
            kotlinx.coroutines.runBlocking {
                appRepository.setPackageColor(packageName, extractedColor)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save color to DB")
        }
    }

    companion object {
        const val JOB_ID = 112
    }
}
