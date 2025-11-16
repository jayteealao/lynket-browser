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
import android.annotation.TargetApi
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.Interpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import arun.com.chromer.bubbles.webheads.physics.MovementTracker
import arun.com.chromer.bubbles.webheads.physics.SpringConfigs
import arun.com.chromer.bubbles.webheads.ui.WebHeadContract
import arun.com.chromer.bubbles.webheads.ui.views.Trashy.Companion.MAGNETISM_THRESHOLD
import arun.com.chromer.settings.Preferences
import arun.com.chromer.util.Utils
import com.facebook.rebound.Spring
import com.facebook.rebound.SpringConfig
import com.facebook.rebound.SpringListener
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Web head object which adds draggable and gesture functionality.
 */
@SuppressLint("ViewConstructor")
class WebHead(
    context: Context,
    url: String,
    private val webHeadContract: WebHeadContract
) : BaseWebHead(context, url), SpringListener {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val gestureDetector = GestureDetector(context, GestureDetectorListener())

    private var trashLockingCoordinates: IntArray? = null
    private var dragging = false
    private var wasLockedToRemove = false
    private var wasFlung = false
    private var wasClicked = false
    private var scaledDown = false
    private var isCoasting = false

    private lateinit var xSpring: Spring
    private lateinit var ySpring: Spring
    private var posX = 0f
    private var posY = 0f
    private var initialDownX = 0
    private var initialDownY = 0
    private var coastingTask: TimerTask? = null
    private var fromAmp = false
    private var incognito = false

    init {
        master = true
        movementTracker = MovementTracker.obtain()
        calcVelocities()
        setupSprings()
        scheduleCoastingTask()
    }

    private fun calcVelocities() {
        if (MINIMUM_HORIZONTAL_FLING_VELOCITY == 0) {
            val scaledScreenWidthDp = resources.configuration.screenWidthDp * 10
            MINIMUM_HORIZONTAL_FLING_VELOCITY = Utils.dpToPx(scaledScreenWidthDp.toDouble())
        }
    }

    private fun setupSprings() {
        ySpring = webHeadContract.newSpring()
        ySpring.addListener(this)
        xSpring = webHeadContract.newSpring()
        xSpring.addListener(this)
        setContentScale(0.0f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (destroyed || !master || inQueue) return true

        try {
            wasFlung = false
            wasClicked = false

            gestureDetector.onTouchEvent(event)

            if (wasClicked) return true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> handleTouchDown(event)
                MotionEvent.ACTION_MOVE -> handleMove(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (handleTouchUp()) return true
                }
            }
        } catch (e: NullPointerException) {
            destroySelf(true)
        }
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        trashLockingCoordinates = null
        MINIMUM_HORIZONTAL_FLING_VELOCITY = 0
        spawnCoordSet = false
        screenBounds = null
        calcVelocities()
        Utils.doAfterLayout(this) { setInitialSpawnLocation() }
    }

    private fun handleTouchDown(event: MotionEvent) {
        dragging = false

        movementTracker?.onDown()

        initialDownX = windowParams.x
        initialDownY = windowParams.y

        posX = event.rawX
        posY = event.rawY

        if (master) {
            masterDownX = posX.toInt()
            masterDownY = posY.toInt()
        }

        touchDown()
        cancelCoastingTask()
    }

    private fun handleMove(event: MotionEvent) {
        movementTracker?.addMovement(event)

        val offsetX = event.rawX - posX
        val offsetY = event.rawY - posY

        if (hypot(offsetX.toDouble(), offsetY.toDouble()) > touchSlop) {
            dragging = true
        }

        if (dragging) {
            getTrashy().reveal()
            userManuallyMoved = true

            val x = (initialDownX + offsetX).toInt()
            val y = (initialDownY + offsetY).toInt()

            if (isNearRemoveCircle(x, y)) {
                getTrashy().grow()
                touchUp()

                xSpring.springConfig = SpringConfigs.SNAP
                ySpring.springConfig = SpringConfigs.SNAP

                xSpring.endValue = trashLockCoOrd()[0].toDouble()
                ySpring.endValue = trashLockCoOrd()[1].toDouble()
            } else {
                getTrashy().shrink()

                xSpring.springConfig = SpringConfigs.DRAG
                ySpring.springConfig = SpringConfigs.DRAG

                xSpring.currentValue = x.toDouble()
                ySpring.currentValue = y.toDouble()

                touchDown()
            }
        }
    }

    private fun handleTouchUp(): Boolean {
        if (wasLockedToRemove) {
            destroySelf(true)
            return true
        }
        dragging = false

        movementTracker?.onUp()

        if (!wasFlung && userManuallyMoved) {
            stickToWall()
        }
        touchUp()
        Trashy.disappear()
        scheduleCoastingTask()
        return false
    }

    private fun scheduleCoastingTask() {
        if (!isMaster()) {
            return
        }
        cancelCoastingTask()
        coastingTask = object : TimerTask() {
            override fun run() {
                Timber.v("Coasting active")
                isCoasting = true
                val halfWidth = width / 4
                screenBounds?.let {
                    if (windowParams.x < dispWidth / 2) {
                        xSpring.endValue = (it.left - halfWidth).toDouble()
                    } else {
                        xSpring.endValue = (it.right + halfWidth).toDouble()
                    }
                }
            }
        }
        Timber.v("Scheduled a coasting task")
        timer.schedule(coastingTask, 6000)
    }

    private fun cancelCoastingTask() {
        isCoasting = false
        coastingTask?.cancel()
        timer.purge()
    }

    private fun trashLockCoOrd(): IntArray {
        if (trashLockingCoordinates == null) {
            val removeCentre = getTrashy().centerCoordinates
            val offset = width / 2
            val x = removeCentre[0] - offset
            val y = removeCentre[1] - offset
            trashLockingCoordinates = intArrayOf(x, y)
        }
        return trashLockingCoordinates!!
    }

    private fun isNearRemoveCircle(x: Int, y: Int): Boolean {
        val p = getTrashy().centerCoordinates
        val rX = p[0]
        val rY = p[1]

        val offset = width / 2
        val adjustedX = x + offset
        val adjustedY = y + offset

        return if (dist(rX.toDouble(), rY.toDouble(), adjustedX.toDouble(), adjustedY.toDouble()) < MAGNETISM_THRESHOLD) {
            wasLockedToRemove = true
            badgeView.visibility = View.INVISIBLE
            webHeadContract.onMasterLockedToTrashy()
            true
        } else {
            wasLockedToRemove = false
            badgeView.visibility = View.VISIBLE
            webHeadContract.onMasterReleasedFromTrashy()
            false
        }
    }

    private fun dist(x1: Double, y1: Double, x2: Double, y2: Double): Float {
        return sqrt((x2 - x1).pow(2.0) + (y2 - y1).pow(2.0)).toFloat()
    }

    private fun setContentScale(scale: Float) {
        contentRoot.scaleX = scale
        contentRoot.scaleY = scale
    }

    private fun animateContentScale(scale: Float, endAction: Runnable? = null) {
        contentRoot.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setInterpolator(SpringInterpolator(0.2, 5.0))
            .setListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    endAction?.run()
                }
            }).start()
    }

    fun reveal(endAction: Runnable?) {
        Utils.doAfterLayout(this) {
            setInitialSpawnLocation()
            Timber.d("Reveal %s", website.url)
            animateContentScale(TOUCH_UP_SCALE, endAction)
            scaledDown = false
        }
    }

    private fun touchDown() {
        if (!scaledDown) {
            scaledDown = true
        }
    }

    private fun touchUp() {
        if (scaledDown) {
            scaledDown = false
        }
    }

    fun getXSpring(): Spring = xSpring

    fun getYSpring(): Spring = ySpring

    fun setSpringConfig(config: SpringConfig) {
        xSpring.springConfig = config
        ySpring.springConfig = config
    }

    override fun onSpringUpdate(spring: Spring) {
        windowParams.x = xSpring.currentValue.toInt()
        windowParams.y = ySpring.currentValue.toInt()
        updateView()
        if (master) {
            webHeadContract.onMasterWebHeadMoved(windowParams.x, windowParams.y)
            checkBounds()
            updateBadgeLocation()
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun updateBadgeLocation() {
        val params = badgeView.layoutParams as LayoutParams
        params.gravity = if (windowParams.x > dispWidth / 2) {
            Gravity.TOP or Gravity.LEFT
        } else {
            Gravity.TOP or Gravity.RIGHT
        }
        badgeView.layoutParams = params
    }

    override fun onSpringAtRest(spring: Spring) {}

    override fun onSpringActivate(spring: Spring) {}

    override fun onSpringEndStateChange(spring: Spring) {}

    private fun checkBounds() {
        if (dragging || screenBounds == null || !master || inQueue || isCoasting) {
            return
        }

        val x = windowParams.x
        val y = windowParams.y
        val width = width

        screenBounds?.let { bounds ->
            if (x + width >= dispWidth) {
                xSpring.springConfig = SpringConfigs.FLING
                xSpring.endValue = bounds.right.toDouble()
            }
            if (x - width <= 0) {
                xSpring.springConfig = SpringConfigs.FLING
                xSpring.endValue = bounds.left.toDouble()
            }
            if (y + width >= dispHeight) {
                ySpring.springConfig = SpringConfigs.FLING
                ySpring.endValue = bounds.bottom.toDouble()
            }
            if (y - width <= 0) {
                ySpring.springConfig = SpringConfigs.FLING
                ySpring.endValue = bounds.top.toDouble()
            }
        }
    }

    private fun stickToWall() {
        screenBounds?.let { bounds ->
            if (windowParams.x > dispWidth / 2) {
                xSpring.springConfig = SpringConfigs.FLING
                xSpring.endValue = bounds.right.toDouble()
            } else {
                xSpring.springConfig = SpringConfigs.FLING
                xSpring.endValue = bounds.left.toDouble()
            }
            if (windowParams.y < bounds.top) {
                ySpring.springConfig = SpringConfigs.FLING
                ySpring.endValue = bounds.top.toDouble()
            } else if (windowParams.y > bounds.bottom) {
                ySpring.springConfig = SpringConfigs.FLING
                ySpring.endValue = bounds.bottom.toDouble()
            }
        }
    }

    fun goToMasterTouchDownPoint() {
        setSpringConfig(SpringConfigs.FLING)
        xSpring.endValue = masterDownX.toDouble()
        ySpring.endValue = masterDownY.toDouble()
    }

    override fun onMasterChanged(master: Boolean) {
        if (master) {
            updateBadgeLocation()
            updateBadgeColors(webHeadColor)
            isCoasting = false
        }
    }

    override fun onSpawnLocationSet(x: Int, y: Int) {
        try {
            ySpring.currentValue = y.toDouble()
            xSpring.currentValue = x.toDouble()
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
        }
    }

    override fun destroySelf(receiveCallback: Boolean) {
        cancelCoastingTask()
        destroyed = true
        WEB_HEAD_COUNT--
        destroySprings()
        if (isCurrentlyAtRemoveWeb()) {
            if (Utils.isLollipopAbove()) {
                closeWithAnimationL(receiveCallback)
            } else {
                closeWithAnimation(receiveCallback)
            }
        } else {
            if (receiveCallback) {
                webHeadContract.onWebHeadDestroyed(this, isLastWebHead())
            }
            super.destroySelf(receiveCallback)
        }
    }

    private fun closeWithAnimation(receiveCallback: Boolean) {
        revealInAnimation(
            deleteColor,
            Runnable {
                circleBg.clearElevation()
                indicator.visibility = View.GONE
                crossFadeFaviconToX()
            },
            Runnable {
                Handler().postDelayed({
                    if (receiveCallback) {
                        webHeadContract.onWebHeadDestroyed(this@WebHead, isLastWebHead())
                    }
                    super@WebHead.destroySelf(receiveCallback)
                }, 200)
            }
        )
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun closeWithAnimationL(receiveCallback: Boolean) {
        circleBg.animate()
            .setDuration(50)
            .withLayer()
            .translationZ(0f)
            .z(0f)
            .withEndAction {
                revealInAnimation(
                    deleteColor,
                    Runnable {
                        crossFadeFaviconToX()
                        indicator.visibility = View.GONE
                    },
                    Runnable {
                        Handler().postDelayed({
                            if (receiveCallback) {
                                webHeadContract.onWebHeadDestroyed(this@WebHead, isLastWebHead())
                            }
                            super@WebHead.destroySelf(receiveCallback)
                        }, 200)
                    }
                )
            }
    }

    private fun isCurrentlyAtRemoveWeb(): Boolean {
        val rx = trashLockCoOrd()[0]
        val ry = trashLockCoOrd()[1]
        if (windowParams.x == rx && windowParams.y == ry) {
            return true
        } else {
            val dist = dist(windowParams.x.toDouble(), windowParams.y.toDouble(), rx.toDouble(), ry.toDouble())
            if (dist < Utils.dpToPx(15.0)) {
                Timber.d("Adjusting positions")
                windowParams.x = rx
                windowParams.y = ry
                updateView()
                return true
            } else {
                return false
            }
        }
    }

    private fun destroySprings() {
        xSpring.destroy()
        ySpring.destroy()
    }

    fun isFromAmp(): Boolean = fromAmp

    fun setFromAmp(fromAmp: Boolean) {
        this.fromAmp = fromAmp
    }

    fun isIncognito(): Boolean = incognito

    fun setIncognito(incognito: Boolean) {
        this.incognito = incognito
    }

    override fun toString(): String {
        return "Webhead ${getUrl()} master: ${isMaster()}"
    }

    class SpringInterpolator(private val amp: Double, private val frequency: Double) : Interpolator {
        override fun getInterpolation(time: Float): Float {
            return (-1 * Math.E.pow(-time / amp) * kotlin.math.cos(frequency * time) + 1).toFloat()
        }
    }

    private inner class GestureDetectorListener : GestureDetector.SimpleOnGestureListener() {

        override fun onLongPress(e: MotionEvent) {
            webHeadContract.onMasterLongClick()
        }

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            wasClicked = true
            if (Preferences.get(context).webHeadsCloseOnOpen()) {
                if (windowParams.x < dispWidth / 2) {
                    contentRoot.pivotX = 0f
                } else {
                    contentRoot.pivotX = contentRoot.width.toFloat()
                }
                contentRoot.pivotY = (contentRoot.height * 0.75).toFloat()
                contentRoot.animate()
                    .scaleX(0.0f)
                    .scaleY(0.0f)
                    .alpha(0.5f)
                    .withLayer()
                    .setDuration(125)
                    .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
                    .withEndAction { sendCallback() }
                    .start()

                if (master) {
                    masterDownX = windowParams.x
                    masterDownY = windowParams.y
                }
            } else {
                sendCallback()
            }
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            dragging = false

            var adjustedVelocities = movementTracker?.getAdjustedVelocities(velocityX, velocityY)
            if (adjustedVelocities == null && e1 != null) {
                val down = floatArrayOf(e1.rawX, e1.rawY)
                val up = floatArrayOf(e2.rawX, e2.rawY)
                adjustedVelocities = MovementTracker.adjustVelocities(down, up, velocityX, velocityY)
            }

            return if (adjustedVelocities != null) {
                wasFlung = true

                val interpolatedVelocityX = interpolateXVelocity(e2, adjustedVelocities[0])

                xSpring.springConfig = SpringConfigs.DRAG
                ySpring.springConfig = SpringConfigs.DRAG

                xSpring.velocity = interpolatedVelocityX.toDouble()
                ySpring.velocity = adjustedVelocities[1].toDouble()
                true
            } else {
                false
            }
        }

        private fun interpolateXVelocity(upEvent: MotionEvent, velocityX: Float): Float {
            var velocity = velocityX
            val x = upEvent.rawX / dispWidth
            if (velocity > 0) {
                velocity = max(velocity, MINIMUM_HORIZONTAL_FLING_VELOCITY * (1 - x))
            } else {
                velocity = -max(velocity, MINIMUM_HORIZONTAL_FLING_VELOCITY * x)
            }
            return velocity
        }

        private fun sendCallback() {
            Trashy.disappear()
            webHeadContract.onWebHeadClick(this@WebHead)
        }
    }

    companion object {
        private const val TOUCH_DOWN_SCALE = 1f
        private const val TOUCH_UP_SCALE = 1f
        private val FAST_OUT_SLOW_IN_INTERPOLATOR: Interpolator = FastOutSlowInInterpolator()
        private val timer = Timer()

        private var MINIMUM_HORIZONTAL_FLING_VELOCITY = 0
        private var movementTracker: MovementTracker? = null

        /**
         * Clears the master position tracking when the service is destroyed.
         */
        fun clearMasterPosition() {
            movementTracker = null
        }
    }
}
