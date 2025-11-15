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

package arun.com.chromer.browsing.article.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.annotation.ColorInt
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import arun.com.chromer.R
import kotlin.math.abs

/**
 * Scroll listener for interacting with the toolbar when the recyclerview scrolls. This includes
 * hiding the toolbar and showing it again when appropriate, along with changing the colors.
 */
class ArticleScrollListener(
    private val toolbar: Toolbar,
    private val statusBar: View,
    private var primaryColor: Int
) : RecyclerView.OnScrollListener() {

    private val transparentColor: Int = ContextCompat.getColor(toolbar.context, R.color.article_toolbarBackground)
    private var transparentBackground = true
    private var isUpdatingTranslation = false
    private var isUpdatingBackground = false

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        super.onScrollStateChanged(recyclerView, newState)
        val manager = recyclerView.layoutManager as LinearLayoutManager
        val firstItem = manager.findFirstCompletelyVisibleItemPosition()
        if (newState == RecyclerView.SCROLL_STATE_IDLE && !transparentBackground &&
            firstItem == 0 && !isUpdatingBackground
        ) {
            animateBackgroundColor(primaryColor, transparentColor, DecelerateInterpolator())
            transparentBackground = true
        }
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        val minDistance = toolbar.context.resources
            .getDimensionPixelSize(R.dimen.article_minToolbarScroll)
        if (abs(dy) < minDistance) {
            return
        }
        if (dy > 0 && toolbar.translationY == 0f) {
            val interpolator = AccelerateInterpolator()

            if (!isUpdatingTranslation) {
                animateTranslation(-1 * toolbar.height, interpolator)
            }

            if (transparentBackground && !isUpdatingBackground) {
                animateBackgroundColor(transparentColor, primaryColor, interpolator)
                transparentBackground = false
            }
        } else if (dy < 0 && toolbar.translationY != 0f) {
            val interpolator = DecelerateInterpolator()

            if (!isUpdatingTranslation) {
                animateTranslation(0, interpolator)
            }

            val manager = recyclerView.layoutManager as LinearLayoutManager
            val firstItem = manager.findFirstVisibleItemPosition()
            if (!transparentBackground && firstItem == 0 && !isUpdatingBackground) {
                animateBackgroundColor(primaryColor, transparentColor, interpolator)
                transparentBackground = true
            }
        }
    }

    fun setPrimaryColor(@ColorInt primaryColor: Int) {
        this.primaryColor = primaryColor
    }

    private fun animateTranslation(to: Int, interpolator: Interpolator) {
        toolbar.animate()
            .translationY(to.toFloat())
            .setDuration(ANIMATION_DURATION.toLong())
            .setInterpolator(interpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    isUpdatingTranslation = false
                }
            })
            .start()
        isUpdatingTranslation = true
    }

    private fun animateBackgroundColor(from: Int, to: Int, interpolator: Interpolator) {
        val anim = ValueAnimator()
        anim.setIntValues(from, to)
        anim.setEvaluator(ArgbEvaluator())
        anim.interpolator = interpolator
        anim.addUpdateListener { valueAnimator ->
            toolbar.setBackgroundColor(valueAnimator.animatedValue as Int)
            statusBar.setBackgroundColor(valueAnimator.animatedValue as Int)
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                isUpdatingBackground = false
            }
        })

        anim.duration = ANIMATION_DURATION.toLong()
        anim.start()
        isUpdatingBackground = true
    }

    companion object {
        private const val ANIMATION_DURATION = 200 // ms
    }
}
