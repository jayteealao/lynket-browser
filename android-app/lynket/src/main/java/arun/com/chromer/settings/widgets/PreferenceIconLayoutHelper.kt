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

// Phase 7: Converted from Java to Kotlin

package arun.com.chromer.settings.widgets

import android.graphics.Color
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.PreferenceViewHolder
import arun.com.chromer.util.Utils
import com.mikepenz.iconics.IconicsDrawable
import timber.log.Timber

/**
 * Created by Arun on 23/06/2016.
 */
object PreferenceIconLayoutHelper {

  @ColorInt
  private val CHECKED_COLOR = Color.parseColor("#757575")

  @ColorInt
  private val UNCHECKED_COLOR = Color.parseColor("#C5C5C5")

  /**
   * Applies layout changes on the preference icon view so that it does not look overly big.
   *
   * @param holder  the preference view holder
   * @param checked if the preference is enabled
   */
  fun applyLayoutChanges(holder: PreferenceViewHolder, checked: Boolean) {
    try {
      val iconFrame = holder.findViewById(androidx.preference.R.id.icon_frame) as LinearLayout
      val imageView = holder.findViewById(android.R.id.icon) as ImageView

      if (iconFrame.minimumWidth != 0) {
        iconFrame.minimumWidth = 0
        imageView.scaleType = ImageView.ScaleType.CENTER
        val params = LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        val dp12 = Utils.dpToPx(12.0)
        params.setMargins(dp12, 0, dp12, 0)
        imageView.layoutParams = params
      }

      applyIconTint(imageView, checked)
    } catch (e: Exception) {
      Timber.e(e)
    }
  }

  /**
   * Finds the icon view and attempts to tint it based on the checked state of the preference
   *
   * @param imageView The image view of icon
   * @param checked   Whether the preference is enabled
   */
  private fun applyIconTint(imageView: ImageView, checked: Boolean) {
    val drawable = imageView.drawable
    if (drawable != null) {
      if (drawable is IconicsDrawable) {
        // Just redraw with the correct color
        imageView.setImageDrawable(drawable.color(if (checked) CHECKED_COLOR else UNCHECKED_COLOR))
      } else {
        val wrap = DrawableCompat.wrap(drawable)
        DrawableCompat.setTint(wrap, if (checked) CHECKED_COLOR else UNCHECKED_COLOR)
        imageView.setImageDrawable(drawable)
      }
    }
  }
}
