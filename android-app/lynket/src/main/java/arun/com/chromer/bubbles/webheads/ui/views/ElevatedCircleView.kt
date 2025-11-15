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

package arun.com.chromer.bubbles.webheads.ui.views

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Outline
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import arun.com.chromer.R
import arun.com.chromer.util.Utils

/**
 * Circle view that draws bitmap shadow layers on pre L and system drop shadow on post L systems.
 */
class ElevatedCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CircleView(context, attrs, defStyleAttr) {

    init {
        if (!Utils.isLollipopAbove()) {
            val shadowR = context.resources.getDimension(R.dimen.web_head_shadow_radius)
            val shadowDx = context.resources.getDimension(R.dimen.web_head_shadow_dx)
            val shadowDy = context.resources.getDimension(R.dimen.web_head_shadow_dy)
            mBgPaint.setShadowLayer(shadowR, shadowDx, shadowDy, 0x55000000)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Utils.isLollipopAbove()) {
            outlineProvider = object : ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                override fun getOutline(view: View, outline: Outline) {
                    val shapeSize = measuredWidth
                    outline.setRoundRect(0, 0, shapeSize, shapeSize, shapeSize / 2f)
                }
            }
            clipToOutline = true
        }
    }

    /**
     * Use only on pre L devices. For post L use [androidx.core.view.ViewCompat.setElevation].
     * No op when called for post L devices.
     */
    fun clearElevation() {
        if (!Utils.isLollipopAbove()) {
            mBgPaint.clearShadowLayer()
            invalidate()
        }
    }
}
