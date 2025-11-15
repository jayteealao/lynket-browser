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
import android.view.View
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference

/**
 * Phase 7: Converted from Java to Kotlin
 *
 * A helper preference view without weird margin on preference icon. Attempts to alter properties on
 * icon frame and icon itself so that it looks better. Supports hiding the switch widget.
 */
class IconSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : SwitchPreference(context, attrs, defStyleAttr, defStyleRes) {

    private var hideSwitch = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        PreferenceIconLayoutHelper.applyLayoutChanges(holder, isEnabled)
        val switchView = holder.findViewById(androidx.preference.R.id.switchWidget)
        if (hideSwitch && switchView != null) {
            switchView.visibility = View.GONE
        }
    }

    fun hideSwitch() {
        hideSwitch = true
    }

    fun showSwitch() {
        hideSwitch = false
    }
}
