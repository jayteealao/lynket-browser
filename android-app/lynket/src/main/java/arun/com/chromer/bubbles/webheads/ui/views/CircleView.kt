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

package arun.com.chromer.bubbles.webheads.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import arun.com.chromer.settings.Preferences
import arun.com.chromer.util.Utils

/**
 * A simple circle view that fills the canvas with a circle fill color. Padding is given to pre L
 * devices to accommodate shadows if needed.
 */
open class CircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Paint used to draw the circle background
     */
    protected val mBgPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    @ColorInt
    private var mColor: Int

    init {
        mBgPaint.style = Paint.Style.FILL
        mColor = Preferences.get(context).webHeadColor()
        mBgPaint.color = mColor
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val outerRadius = if (Utils.isLollipopAbove()) {
            measuredWidth / 2f
        } else {
            (measuredWidth / 2.4).toFloat()
        }
        canvas.drawCircle(
            measuredWidth / 2f,
            measuredWidth / 2f,
            outerRadius,
            mBgPaint
        )
    }

    @ColorInt
    fun getColor(): Int {
        return mColor
    }

    fun setColor(@ColorInt color: Int) {
        mColor = color
        mBgPaint.color = color
        invalidate()
    }
}
