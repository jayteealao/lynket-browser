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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import arun.com.chromer.R
import kotlin.math.abs
import kotlin.math.log10

/**
 * A [FrameLayout] which responds to nested scrolls to create drag-dismissable layouts.
 * Applies an elasticity factor to reduce movement as you approach the given dismiss distance.
 * Optionally also scales down content during drag.
 *
 * Adapted from
 * https://github.com/nickbutcher/plaid/blob/master/app/src/main/java/io/plaidapp/
 * ui/widget/ElasticDragDismissFrameLayout.java
 * with some changes so that the background color can be adjusted behind the dragging view.
 *
 * This view is required to only have a single child.
 */
class ElasticDragDismissFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val dragDismissFraction = -1f
    private val dragDismissScale = 1f
    private val dragElasticity = 0.5f

    // configurable attribs
    private var dragDismissDistance = Float.MAX_VALUE
    private var shouldScale = false

    // state
    private var totalDrag = 0f
    private var draggingDown = false
    private var draggingUp = false
    private var enabled = true
    private var callbacks: MutableList<ElasticDragDismissCallback>? = null

    private var draggingBackground: RectF? = null
    private var draggingBackgroundPaint: Paint? = null

    init {
        init()
    }

    private fun init() {
        dragDismissDistance = resources
            .getDimensionPixelSize(R.dimen.article_drag_down_dismiss_distance).toFloat()

        shouldScale = dragDismissScale != 1f

        draggingBackgroundPaint = Paint().apply {
            color = context.resources.getColor(R.color.article_transparentSideBackground)
            style = Paint.Style.FILL
        }
    }

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return enabled && (nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        if (enabled) {
            // if we're in a drag gesture and the user reverses up the we should take those events
            if (draggingDown && dy > 0 || draggingUp && dy < 0) {
                dragScale(dy)
                consumed[1] = dy
            }
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int
    ) {
        if (enabled) {
            dragScale(dyUnconsumed)
        }
    }

    override fun onStopNestedScroll(child: View) {
        if (enabled) {
            if (abs(totalDrag) >= dragDismissDistance) {
                dispatchDismissCallback()
            } else { // settle back to natural position
                val interpolator = fastOutSlowInInterpolator ?: AnimationUtils.loadInterpolator(
                    context,
                    android.R.interpolator.fast_out_slow_in
                ).also { fastOutSlowInInterpolator = it }

                getChildAt(0).animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200L)
                    .setInterpolator(interpolator)
                    .setListener(null)
                    .start()

                var animator: ValueAnimator? = null
                draggingBackground?.let { background ->
                    if (draggingUp) {
                        animator = ValueAnimator.ofFloat(background.top, background.bottom)
                        animator?.addUpdateListener { valueAnimator ->
                            background.top = valueAnimator.animatedValue as Float
                            invalidate()
                        }
                    } else if (draggingDown) {
                        animator = ValueAnimator.ofFloat(background.bottom, background.top)
                        animator?.addUpdateListener { valueAnimator ->
                            background.bottom = valueAnimator.animatedValue as Float
                            invalidate()
                        }
                    }
                }

                animator?.apply {
                    this.interpolator = interpolator
                    duration = 200L
                    start()
                }

                totalDrag = 0f
                draggingDown = false
                draggingUp = false
                dispatchDragCallback(0f, 0f, 0f, 0f)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (dragDismissFraction > 0f) {
            dragDismissDistance = h * dragDismissFraction
        }
    }

    fun addListener(listener: ElasticDragDismissCallback) {
        if (callbacks == null) {
            callbacks = ArrayList()
        }
        callbacks?.add(listener)
    }

    override fun isEnabled(): Boolean {
        return enabled
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun removeListener(listener: ElasticDragDismissCallback) {
        callbacks?.takeIf { it.isNotEmpty() }?.remove(listener)
    }

    private fun dragScale(scroll: Int) {
        if (scroll == 0) return

        totalDrag += scroll
        val child = getChildAt(0)

        // track the direction & set the pivot point for scaling
        // don't double track i.e. if play dragging down and then reverse, keep tracking as
        // dragging down until they reach the 'natural' position
        if (scroll < 0 && !draggingUp && !draggingDown) {
            draggingDown = true
            if (shouldScale) child.pivotY = height.toFloat()
        } else if (scroll > 0 && !draggingDown && !draggingUp) {
            draggingUp = true
            if (shouldScale) child.pivotY = 0f
        }
        // how far have we dragged relative to the distance to perform a dismiss
        // (0–1 where 1 = dismiss distance). Decreasing logarithmically as we approach the limit
        var dragFraction = log10(1.0 + (abs(totalDrag) / dragDismissDistance)).toFloat()

        // calculate the desired translation given the drag fraction
        var dragTo = dragFraction * dragDismissDistance * dragElasticity

        if (draggingUp) {
            // as we use the absolute magnitude when calculating the drag fraction, need to
            // re-apply the drag direction
            dragTo *= -1
        }
        child.translationY = dragTo

        if (draggingBackground == null) {
            draggingBackground = RectF().apply {
                left = 0f
                right = width.toFloat()
            }
        }

        if (shouldScale) {
            val scale = 1 - ((1 - dragDismissScale) * dragFraction)
            child.scaleX = scale
            child.scaleY = scale
        }

        // if we've reversed direction and gone past the settle point then clear the flags to
        // allow the list to get the scroll events & reset any transforms
        if ((draggingDown && totalDrag >= 0) || (draggingUp && totalDrag <= 0)) {
            totalDrag = 0f
            dragTo = 0f
            dragFraction = 0f
            draggingDown = false
            draggingUp = false
            child.translationY = 0f
            child.scaleX = 1f
            child.scaleY = 1f
        }

        // draw the background above or below the view where it has scrolled at
        draggingBackground?.let { background ->
            if (draggingUp) {
                background.bottom = height.toFloat()
                background.top = height + dragTo
                invalidate()
            } else if (draggingDown) {
                background.top = 0f
                background.bottom = dragTo
                invalidate()
            }
        }

        dispatchDragCallback(
            dragFraction, dragTo,
            minOf(1f, abs(totalDrag) / dragDismissDistance), totalDrag
        )
    }

    private fun dispatchDragCallback(
        elasticOffset: Float,
        elasticOffsetPixels: Float,
        rawOffset: Float,
        rawOffsetPixels: Float
    ) {
        callbacks?.takeIf { it.isNotEmpty() }?.forEach { callback ->
            callback.onDrag(elasticOffset, elasticOffsetPixels, rawOffset, rawOffsetPixels)
        }
    }

    private fun dispatchDismissCallback() {
        callbacks?.takeIf { it.isNotEmpty() }?.forEach { callback ->
            callback.onDragDismissed()
        }
    }

    fun isDragging(): Boolean {
        return draggingDown || draggingUp
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        draggingBackground?.let { background ->
            draggingBackgroundPaint?.let { paint ->
                canvas.drawRect(background, paint)
            }
        }
    }

    abstract class ElasticDragDismissCallback {
        /**
         * Called for each drag event.
         *
         * @param elasticOffset       Indicating the drag offset with elasticity applied i.e. may
         *                            exceed 1.
         * @param elasticOffsetPixels The elastically scaled drag distance in pixels.
         * @param rawOffset           Value from [0, 1] indicating the raw drag offset i.e.
         *                            without elasticity applied. A value of 1 indicates that the
         *                            dismiss distance has been reached.
         * @param rawOffsetPixels     The raw distance the user has dragged
         */
        open fun onDrag(
            elasticOffset: Float,
            elasticOffsetPixels: Float,
            rawOffset: Float,
            rawOffsetPixels: Float
        ) {
        }

        /**
         * Called when dragging is released and has exceeded the threshold dismiss distance.
         */
        open fun onDragDismissed() {
        }
    }

    companion object {
        private var fastOutSlowInInterpolator: Interpolator? = null
    }
}
