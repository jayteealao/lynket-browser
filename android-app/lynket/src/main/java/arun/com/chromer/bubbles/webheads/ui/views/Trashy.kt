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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.BounceInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.facebook.rebound.SimpleSpringListener
import com.facebook.rebound.Spring
import com.facebook.rebound.SpringConfig
import com.facebook.rebound.SpringSystem
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import arun.com.chromer.R
import arun.com.chromer.util.Utils
import timber.log.Timber

/**
 * Created by Arun on 03/02/2016.
 */
@SuppressLint("ViewConstructor")
class Trashy private constructor(
    context: Context,
    windowManager: WindowManager
) : FrameLayout(context) {

    private var windowParams: WindowManager.LayoutParams? = null
    private var scaleSpring: Spring? = null
    private var springSystem: SpringSystem? = null
    private var hidden = false
    private var removeHeadCircle: RemoveHeadCircle? = null
    private var grew = false
    private var centrePoint: IntArray? = null

    init {
        Companion.windowManager = windowManager

        removeHeadCircle = RemoveHeadCircle(context)
        addView(removeHeadCircle)

        visibility = INVISIBLE
        hidden = true

        setInitialLocation()
        setUpSprings()
        initCentreCoords()

        Companion.windowManager?.addView(this, windowParams)
    }

    @SuppressLint("RtlHardcoded")
    private fun setInitialLocation() {
        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(metrics)
        val dispWidth = metrics.widthPixels
        val dispHeight = metrics.heightPixels

        createWindowParams()

        windowParams?.gravity = Gravity.LEFT or Gravity.TOP
        val offset = adaptWidth / 2
        windowParams?.x = (dispWidth / 2) - offset
        windowParams?.y = dispHeight - (dispHeight / 6) - offset
    }

    private fun createWindowParams() {
        windowParams = if (Utils.ANDROID_OREO) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )
        }
    }

    private fun updateView() {
        windowParams?.let {
            windowManager?.updateViewLayout(this, it)
        }
    }

    fun destroyAnimator(endAction: Runnable) {
        if (INSTANCE == null || removeHeadCircle == null) {
            endAction.run()
            return
        }

        INSTANCE?.removeHeadCircle?.animate()
            ?.scaleX(0.0f)
            ?.scaleY(0.0f)
            ?.alpha(0.5f)
            ?.setDuration(300)
            ?.withLayer()
            ?.withEndAction(endAction)
            ?.setInterpolator(BounceInterpolator())
            ?.start()
    }

    private fun destroySelf() {
        scaleSpring?.setAtRest()
        scaleSpring?.destroy()
        scaleSpring = null

        removeView(removeHeadCircle)
        removeHeadCircle = null

        windowParams = null
        springSystem = null

        windowManager?.removeView(this)

        centrePoint = null
        INSTANCE = null
        Timber.d("Remove view detached and killed")
    }

    private val adaptWidth: Int
        get() = maxOf(width, RemoveHeadCircle.sizePx)

    fun getCenterCoordinates(): IntArray {
        if (centrePoint == null) {
            initCentreCoords()
        }
        return centrePoint!!
    }

    val centerCoordinates: IntArray
        get() = getCenterCoordinates()

    private fun initCentreCoords() {
        val offset = adaptWidth / 2
        val rX = windowParams!!.x + offset
        val rY = windowParams!!.y + offset
        centrePoint = intArrayOf(rX, rY)
    }

    private fun setUpSprings() {
        springSystem = SpringSystem.create()
        scaleSpring = springSystem?.createSpring()

        val scaleSpringConfig = SpringConfig.fromOrigamiTensionAndFriction(100.0, 9.0)
        scaleSpring?.springConfig = scaleSpringConfig
        scaleSpring?.addListener(object : SimpleSpringListener() {
            override fun onSpringUpdate(spring: Spring) {
                val value = spring.currentValue.toFloat()
                removeHeadCircle?.scaleX = value
                removeHeadCircle?.scaleY = value
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.d(newConfig.toString())
        centrePoint = null
        setInitialLocation()
        post { updateView() }
    }

    private fun hide() {
        if (!hidden) {
            scaleSpring?.endValue = 0.0
            hidden = true
        }
    }

    fun reveal() {
        visibility = VISIBLE
        if (hidden) {
            scaleSpring?.endValue = 0.9
            hidden = false
        }
    }

    fun grow() {
        if (!grew) {
            scaleSpring?.setCurrentValue(0.9, true)
            scaleSpring?.endValue = 1.0
            grew = true
        }
    }

    fun shrink() {
        if (grew) {
            scaleSpring?.endValue = 0.9
            grew = false
        }
    }

    /**
     * Created by Arun on 04/02/2016.
     */
    private class RemoveHeadCircle(context: Context) : View(context) {

        private val mBgPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.remove_web_head_color)
            style = Paint.Style.FILL

            val shadowR = context.resources.getDimension(R.dimen.remove_head_shadow_radius)
            val shadowDx = context.resources.getDimension(R.dimen.remove_head_shadow_dx)
            val shadowDy = context.resources.getDimension(R.dimen.remove_head_shadow_dy)

            setShadowLayer(shadowR, shadowDx, shadowDy, 0x75000000)
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            sizePx = context.resources.getDimensionPixelSize(R.dimen.remove_head_size)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(sizePx, sizePx)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            val radius = (width / 2.4).toFloat()
            canvas.drawCircle(width / 2f, height / 2f, radius, mBgPaint)

            drawDeleteIcon(canvas)

            diameterPx = (2 * radius).toInt()
        }

        private fun drawDeleteIcon(canvas: Canvas) {
            val deleteIcon: Bitmap = IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_delete)
                .color(Color.WHITE)
                .sizeDp(18).toBitmap()
            val cHeight = canvas.clipBounds.height()
            val cWidth = canvas.clipBounds.width()
            val x = cWidth / 2f - deleteIcon.width / 2
            val y = cHeight / 2f - deleteIcon.height / 2
            canvas.drawBitmap(deleteIcon, x, y, null)
        }

        companion object {
            var sizePx: Int = 0
                private set
            var diameterPx: Int = 0
                private set
        }
    }

    companion object {
        @JvmField
        val MAGNETISM_THRESHOLD = 120.0 * android.content.res.Resources.getSystem().displayMetrics.density

        private var windowManager: WindowManager? = null
        private var INSTANCE: Trashy? = null

        @JvmStatic
        fun init(context: Context) {
            get(context)
        }

        /**
         * Returns an instance of this view. If the view is not initialized, then a new view is created
         * and returned.
         * The returned view might not have been laid out yet.
         */
        @JvmStatic
        @Synchronized
        fun get(context: Context): Trashy {
            if (INSTANCE == null) {
                Timber.d("Creating new instance of remove web head")
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                INSTANCE = Trashy(context, wm)
            }
            return INSTANCE!!
        }

        @JvmStatic
        fun destroy() {
            INSTANCE?.destroySelf()
        }

        @JvmStatic
        fun disappear() {
            INSTANCE?.hide()
        }
    }
}
