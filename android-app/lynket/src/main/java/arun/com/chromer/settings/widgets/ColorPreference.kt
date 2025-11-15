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

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import arun.com.chromer.R

/**
 * Created by Arun on 16/04/2016.
 */
class ColorPreference @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

  private var colorIndicator: View? = null

  @ColorInt
  private var color: Int = 0

  private var defaultColor: Int = 0

  init {
    init(context, attrs)
  }

  private fun init(context: Context, attrs: AttributeSet?) {
    widgetLayoutResource = R.layout.widget_color_preference

    defaultColor = ContextCompat.getColor(getContext(), R.color.colorPrimary)

    if (attrs != null) {
      val ta = context.obtainStyledAttributes(attrs, R.styleable.ColorPreference)
      val colorRes = ta.getResourceId(R.styleable.ColorPreference_color, 0)
      if (colorRes != 0) {
        color = ContextCompat.getColor(getContext(), colorRes)
      }
      ta.recycle()
    }
  }

  override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
    if (restorePersistedValue) {
      color = getPersistedInt(defaultColor)
    } else {
      color = defaultValue as? Int ?: defaultColor
      persistInt(color)
    }
  }

  override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any {
    return a.getInteger(index, defaultColor)
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    colorIndicator = holder.findViewById(R.id.color_preview)
    PreferenceIconLayoutHelper.applyLayoutChanges(holder, isEnabled)
    invalidate()
  }

  private fun invalidate() {
    colorIndicator?.setBackgroundColor(color)
  }

  @ColorInt
  fun getColor(): Int {
    return getPersistedInt(color)
  }

  fun setColor(@ColorInt color: Int) {
    this.color = color
    persistInt(color)
    summary = colorHexValue()
    invalidate()
  }

  private fun colorHexValue(): String {
    return String.format("#%06X", 0xFFFFFF and color)
  }

  fun refreshSummary() {
    summary = colorHexValue()
  }
}
