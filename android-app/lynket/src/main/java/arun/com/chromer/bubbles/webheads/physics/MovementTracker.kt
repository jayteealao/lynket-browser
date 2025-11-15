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

package arun.com.chromer.bubbles.webheads.physics

import android.view.MotionEvent
import java.util.LinkedList
import kotlin.math.abs

/**
 * A helper class for tracking web heads movements. This is needed to correctly apply polarity on
 * calculated velocity by velocity tracker. For example when web head is moved from left to right
 * and top to bottom, the X and Y velocity should be positive. Sometimes that is not the case with
 * raw values given by[android.view.VelocityTracker]
 */
class MovementTracker private constructor() {
    private val trackingSize: Int = 10
    private val xPoints: SizedQueue<Float> = SizedQueue(trackingSize)
    private val yPoints: SizedQueue<Float> = SizedQueue(trackingSize)

    /**
     * Adds a motion event to the tracker.
     *
     * @param event The event to be added.
     */
    fun addMovement(event: MotionEvent) {
        val x = event.rawX
        val y = event.rawY
        xPoints.add(x)
        yPoints.add(y)
    }

    /**
     * Clear the tracking queue when user begins the gesture.
     */
    fun onDown() {
        xPoints.clear()
        yPoints.clear()
    }

    /**
     * Clear the tracking queue when user ends the gesture.
     */
    fun onUp() {
        xPoints.clear()
        yPoints.clear()
    }

    fun getAdjustedVelocities(xVelocity: Float, yVelocity: Float): FloatArray? {
        val trackingThreshold = (0.25 * trackingSize).toInt()
        val velocities: FloatArray?
        if (xPoints.size >= trackingThreshold) {
            val downIndex = xPoints.size - trackingThreshold

            val up = floatArrayOf(xPoints.last, yPoints.last)
            val down = floatArrayOf(xPoints[downIndex], yPoints[downIndex])

            velocities = adjustVelocities(down, up, xVelocity, yVelocity)
        } else {
            velocities = null
        }
        return velocities
    }

    override fun toString(): String {
        return xPoints.toString() + yPoints
    }

    companion object {
        private var INSTANCE: MovementTracker? = null

        fun obtain(): MovementTracker {
            if (INSTANCE == null) {
                INSTANCE = MovementTracker()
            }
            return INSTANCE!!
        }

        fun adjustVelocities(p1: FloatArray, p2: FloatArray, xVelocity: Float, yVelocity: Float): FloatArray {
            val downX = p1[0]
            val downY = p1[1]

            val upX = p2[0]
            val upY = p2[1]

            val x: Float
            val y: Float

            when {
                upX >= downX && upY >= downY -> {
                    // Bottom right
                    x = positive(xVelocity)
                    y = positive(yVelocity)
                }
                upX >= downX && upY <= downY -> {
                    // Top right
                    x = positive(xVelocity)
                    y = negate(yVelocity)
                }
                upX <= downX && upY <= downY -> {
                    // Top left
                    x = negate(xVelocity)
                    y = negate(yVelocity)
                }
                else -> {
                    // Bottom left
                    x = negate(xVelocity)
                    y = positive(yVelocity)
                }
            }
            return floatArrayOf(x, y)
        }

        private fun negate(value: Float): Float {
            return if (value > 0) -value else value
        }

        private fun positive(value: Float): Float {
            return abs(value)
        }
    }
}

/**
 * A size limited queue structure that evicts the queue head when maximum queue size is reached. At
 * any instant the queue is equal or less than the max queue size.
 *
 * @param E
 */
internal class SizedQueue<E>(private val limit: Int) : LinkedList<E>() {

    override fun add(element: E): Boolean {
        super.add(element)
        while (size > limit) {
            super.remove()
        }
        return true
    }

    override fun addAll(elements: Collection<E>): Boolean {
        throw UnsupportedOperationException("Not implemented, use add()")
    }

    override fun add(index: Int, element: E) {
        throw UnsupportedOperationException("Not implemented, use add()")
    }

    override fun addFirst(element: E) {
        throw UnsupportedOperationException("Not implemented, use add()")
    }

    override fun addLast(element: E) {
        throw UnsupportedOperationException("Not implemented, use add()")
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        throw UnsupportedOperationException("Not implemented, use add()")
    }
}
