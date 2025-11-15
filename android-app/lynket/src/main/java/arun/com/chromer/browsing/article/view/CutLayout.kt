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

package arun.com.chromer.browsing.article.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout

/**
 * Adapted from: https://gist.github.com/ZieIony/8480b2d335c1aeb51167
 */
class CutLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pdMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val path = Path()

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null, Canvas.ALL_SAVE_FLAG)
        super.dispatchDraw(canvas)

        paint.xfermode = pdMode
        path.reset()
        path.moveTo(0f, height - TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            CUT_HEIGHT.toFloat(), resources.displayMetrics
        ))
        path.lineTo(width.toFloat(), height.toFloat())
        path.lineTo(0f, height.toFloat())
        path.close()
        canvas.drawPath(path, paint)

        canvas.restoreToCount(saveCount)
        paint.xfermode = null
    }

    companion object {
        private const val CUT_HEIGHT = 48
    }
}
