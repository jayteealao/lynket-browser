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

package arun.com.chromer.settings.widgets

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceViewHolder
import arun.com.chromer.util.Utils

/**
 * Phase 7: Converted from Java to Kotlin
 *
 * Sub-preference checkbox with custom styling (smaller text, left padding).
 */
class SubCheckBoxPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : CheckBoxPreference(context, attrs, defStyleAttr, defStyleRes) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val title = holder.findViewById(android.R.id.title) as TextView
        val summary = holder.findViewById(android.R.id.summary) as TextView

        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val dp56 = Utils.dpToPx(56.0)
        title.setPadding(dp56, 0, 0, 0)
        summary.setPadding(dp56, 0, 0, 0)
    }
}
