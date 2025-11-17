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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.text.SpannableString
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants.NO_COLOR
import arun.com.chromer.util.ColorUtil
import arun.com.chromer.util.ColorUtil.getForegroundWhiteOrBlack
import arun.com.chromer.util.Utils
import arun.com.chromer.util.Utils.dpToPx
import butterknife.BindView
import butterknife.ButterKnife
import cn.nekocode.badge.BadgeDrawable
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import timber.log.Timber

/**
 * ViewGroup that holds the web head UI elements. Allows configuring various parameters in relation
 * to UI like favicon, text indicator and is responsible for inflating all the content.
 */
abstract class BaseWebHead @SuppressLint("RtlHardcoded") constructor(
    context: Context,
    private val url: String
) : FrameLayout(context) {

    @BindView(R.id.favicon)
    protected lateinit var favicon: ImageView

    @BindView(R.id.indicator)
    protected lateinit var indicator: TextView

    @BindView(R.id.circleBackground)
    protected lateinit var circleBg: ElevatedCircleView

    @BindView(R.id.revealView)
    protected lateinit var revealView: CircleView

    @BindView(R.id.badge)
    protected lateinit var badgeView: TextView

    protected var website: Website = Website().apply { this.url = this@BaseWebHead.url }
        set(value) {
            field = value
        }
    protected var spawnCoordSet = false

    var deleteColor = NO_COLOR
    var dispWidth = 0
    var dispHeight = 0
    lateinit var contentRoot: FrameLayout
    var userManuallyMoved = false
    var destroyed = false
    var master = false
        set(value) {
            field = value
            if (!value) {
                badgeView.visibility = View.INVISIBLE
            } else {
                badgeView.visibility = View.VISIBLE
                badgeDrawable?.number = WEB_HEAD_COUNT
                inQueue = false
            }
            onMasterChanged(value)
        }
    var inQueue = false
        set(value) {
            field = value
            visibility = if (value) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

    @ColorInt
    var webHeadColor = 0
        set(value) {
            getRevealAnimator(value).start()
        }

    private var fromNewTab = false

    val windowParams: WindowManager.LayoutParams

    init {
        WEB_HEAD_COUNT++

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        inflateContent(context)
        windowParams = createWindowParams()
        windowParams.gravity = Gravity.TOP or Gravity.LEFT
        initDisplayMetrics()
        windowManager?.addView(this, windowParams)

        if (xDrawable == null) {
            xDrawable = IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_close)
                .color(Color.WHITE)
                .sizeDp(18)
        }

        if (deleteColor == NO_COLOR) {
            deleteColor = ContextCompat.getColor(context, R.color.remove_web_head_color)
        }

        if (WEB_HEAD_COUNT > 2) {
            setWebHeadElevation(dpToPx(5.0))
        }
    }

    protected abstract fun onMasterChanged(master: Boolean)

    protected abstract fun onSpawnLocationSet(x: Int, y: Int)

    private fun inflateContent(context: Context) {
        contentRoot = if (Preferences.get(context).webHeadsSize() == 2) {
            LayoutInflater.from(this.context).inflate(R.layout.widget_web_head_layout_small, this, false) as FrameLayout
        } else {
            LayoutInflater.from(this.context).inflate(R.layout.widget_web_head_layout, this, false) as FrameLayout
        }
        addView(contentRoot)
        ButterKnife.bind(this)

        webHeadColor = Preferences.get(context).webHeadColor()
        indicator.text = Utils.getFirstLetter(url)
        indicator.setTextColor(getForegroundWhiteOrBlack(webHeadColor))
        initRevealView(webHeadColor)

        if (badgeDrawable == null) {
            badgeDrawable = BadgeDrawable.Builder()
                .type(BadgeDrawable.TYPE_NUMBER)
                .badgeColor(ContextCompat.getColor(this.context, R.color.accent))
                .textColor(Color.WHITE)
                .number(WEB_HEAD_COUNT)
                .build()
        } else {
            badgeDrawable?.number = WEB_HEAD_COUNT
        }
        badgeView.visibility = View.VISIBLE
        badgeView.text = SpannableString(badgeDrawable?.toSpannable())
        updateBadgeColors(webHeadColor)

        if (!Utils.isLollipopAbove()) {
            val pad = dpToPx(5.0)
            badgeView.setPadding(pad, pad, pad, pad)
        }
    }

    private fun initDisplayMetrics() {
        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(metrics)
        dispWidth = metrics.widthPixels
        dispHeight = metrics.heightPixels
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        masterX = 0
        masterY = 0
        initDisplayMetrics()
    }

    protected fun setInitialSpawnLocation() {
        Timber.d("Initial spawn location set.")
        if (screenBounds == null) {
            screenBounds = ScreenBounds(dispWidth, dispHeight, width)
        }
        if (!spawnCoordSet) {
            var x: Int
            var y = dispHeight / 3
            if (masterX != 0 || masterY != 0) {
                x = masterX
                y = masterY
            } else {
                x = if (Preferences.get(context).webHeadsSpawnLocation() == 1) {
                    screenBounds!!.right
                } else {
                    screenBounds!!.left
                }
            }
            spawnCoordSet = true
            onSpawnLocationSet(x, y)
        }
    }

    fun getTrashy(): Trashy {
        return Trashy.get(context)
    }

    fun updateView() {
        try {
            if (master) {
                masterX = windowParams.x
                masterY = windowParams.y
            }
            windowManager?.updateViewLayout(this, windowParams)
        } catch (e: IllegalArgumentException) {
            Timber.e("Update called after view was removed")
        }
    }

    fun isLastWebHead(): Boolean {
        return WEB_HEAD_COUNT == 0
    }

    private fun setWebHeadElevation(elevationPx: Int) {
        if (Utils.isLollipopAbove()) {
            circleBg.elevation = elevationPx.toFloat()
            revealView.elevation = elevationPx + 1f
        }
    }

    fun getRevealAnimator(@ColorInt newWebHeadColor: Int): Animator {
        revealView.clearAnimation()
        initRevealView(newWebHeadColor)

        val animator = AnimatorSet()
        animator.playTogether(
            ObjectAnimator.ofFloat(revealView, "scaleX", 1f),
            ObjectAnimator.ofFloat(revealView, "scaleY", 1f),
            ObjectAnimator.ofFloat(revealView, "alpha", 1f)
        )
        revealView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                webHeadColor = newWebHeadColor
                updateBadgeColors(webHeadColor)
                circleBg.setColor(newWebHeadColor)
                indicator.setTextColor(getForegroundWhiteOrBlack(newWebHeadColor))
                revealView.setLayerType(View.LAYER_TYPE_NONE, null)
                revealView.scaleX = 0f
                revealView.scaleY = 0f
            }
        })
        animator.interpolator = LinearOutSlowInInterpolator()
        animator.duration = 250
        return animator
    }

    fun revealInAnimation(
        @ColorInt newWebHeadColor: Int,
        start: Runnable,
        end: Runnable
    ) {
        revealView.clearAnimation()
        revealView.setColor(circleBg.color)
        revealView.scaleX = 1f
        revealView.scaleY = 1f
        revealView.alpha = 1f
        circleBg.setColor(newWebHeadColor)

        val animator = AnimatorSet()
        animator.playTogether(
            ObjectAnimator.ofFloat(revealView, "scaleX", 0f),
            ObjectAnimator.ofFloat(revealView, "scaleY", 0f)
        )
        revealView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                start.run()
            }

            override fun onAnimationEnd(animation: Animator) {
                webHeadColor = newWebHeadColor
                indicator.setTextColor(getForegroundWhiteOrBlack(newWebHeadColor))
                revealView.setLayerType(View.LAYER_TYPE_NONE, null)
                revealView.scaleX = 0f
                revealView.scaleY = 0f
                end.run()
            }
        })
        animator.interpolator = LinearOutSlowInInterpolator()
        animator.duration = 400
        animator.startDelay = 100
        animator.start()
    }

    private fun initRevealView(@ColorInt revealColor: Int) {
        revealView.setColor(revealColor)
        revealView.scaleX = 0f
        revealView.scaleY = 0f
        revealView.alpha = 0.8f
    }

    fun crossFadeFaviconToX() {
        favicon.visibility = View.VISIBLE
        favicon.clearAnimation()
        favicon.scaleType = ImageView.ScaleType.CENTER
        val icon = TransitionDrawable(
            arrayOf(
                ColorDrawable(Color.TRANSPARENT),
                xDrawable!!
            )
        )
        favicon.setImageDrawable(icon)
        icon.isCrossFadeEnabled = true
        icon.startTransition(50)
        favicon
            .animate()
            .withLayer()
            .rotation(180f)
            .setDuration(250)
            .setInterpolator(LinearOutSlowInInterpolator())
            .start()
    }

    @ColorInt
    fun getWebHeadColor(ignoreFavicons: Boolean): Int {
        return if (ignoreFavicons) {
            webHeadColor
        } else {
            if (getFaviconBitmap() != null) {
                webHeadColor
            } else {
                NO_COLOR
            }
        }
    }

    fun updateBadgeColors(@ColorInt webHeadColor: Int) {
        val badgeColor = ColorUtil.getClosestAccentColor(webHeadColor)
        badgeDrawable?.badgeColor = badgeColor
        badgeDrawable?.textColor = getForegroundWhiteOrBlack(badgeColor)
        badgeView.invalidate()
    }

    fun getUrl(): String = url

    fun getUnShortenedUrl(): String = website.preferredUrl()

    fun getFaviconBitmap(): Bitmap? {
        return try {
            val roundedBitmapDrawable = getFaviconDrawable() as? RoundedBitmapDrawable
            roundedBitmapDrawable?.bitmap
        } catch (e: Exception) {
            Timber.e("Error while getting favicon bitmap: %s", e.message)
            null
        }
    }

    private fun getFaviconDrawable(): Drawable? {
        return try {
            val drawable = favicon.drawable as? TransitionDrawable
            drawable?.getDrawable(1)
        } catch (e: ClassCastException) {
            Timber.e("Error while getting favicon drawable: %s", e.message)
            null
        }
    }

    fun setFaviconDrawable(faviconDrawable: Drawable) {
        indicator.animate().alpha(0f).withLayer().start()
        val transitionDrawable = TransitionDrawable(
            arrayOf(
                ColorDrawable(Color.TRANSPARENT),
                faviconDrawable
            )
        )
        favicon.visibility = View.VISIBLE
        favicon.setImageDrawable(transitionDrawable)
        transitionDrawable.isCrossFadeEnabled = true
        transitionDrawable.startTransition(500)
    }

    open fun destroySelf(receiveCallback: Boolean) {
        destroyed = true
        Trashy.disappear()
        removeView(contentRoot)
        windowManager?.let {
            try {
                it.removeView(this)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun createWindowParams(): WindowManager.LayoutParams {
        return if (Utils.ANDROID_OREO) {
            WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
        }
    }

    class ScreenBounds(dispWidth: Int, dispHeight: Int, webHeadWidth: Int) {
        var left: Int
        var right: Int
        var top: Int
        var bottom: Int

        init {
            if (webHeadWidth == 0 || dispWidth == 0 || dispHeight == 0) {
                throw IllegalArgumentException("Width of web head or screen size cannot be 0")
            }
            right = (dispWidth - (webHeadWidth * DISPLACE_PERC)).toInt()
            left = ((webHeadWidth * (1 - DISPLACE_PERC)) * -1).toInt()
            top = dpToPx(25.0)
            bottom = (dispHeight * 0.85).toInt()
        }

        companion object {
            private const val DISPLACE_PERC = 0.7
        }
    }

    companion object {
        var screenBounds: ScreenBounds? = null
        var WEB_HEAD_COUNT = 0
        var masterDownX = 0
        var masterDownY = 0

        private var windowManager: WindowManager? = null
        private var xDrawable: Drawable? = null
        private var badgeDrawable: BadgeDrawable? = null
        private var masterX = 0
        private var masterY = 0

        @JvmStatic
        fun clearMasterPosition() {
            masterY = 0
            masterX = 0
        }
    }
}
