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

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.facebook.rebound.Spring
import com.facebook.rebound.SpringListener
import arun.com.chromer.bubbles.webheads.WebHeadService
import arun.com.chromer.util.Utils
import java.util.LinkedList

/**
 * Created by Arun on 06/08/2016.
 * Custom spring chain helper that simplifies maintaining 2 separate chains for X and Y axis.
 */
class SpringChain2D private constructor(private val sDispWidth: Int) : SpringListener {
    private val xSprings = LinkedList<Spring>()
    private val ySpring = LinkedList<Spring>()
    private var xMasterSpring: Spring? = null
    private var yMasterSpring: Spring? = null
    private var displacementEnabled = true

    fun clear() {
        xMasterSpring?.removeListener(this)
        yMasterSpring?.removeListener(this)
        for (spring in xSprings) {
            spring.removeListener(this)
        }
        for (spring in ySpring) {
            spring.removeListener(this)
        }
        xSprings.clear()
        ySpring.clear()
    }

    fun setMasterSprings(xMaster: Spring, yMaster: Spring) {
        xMasterSpring = xMaster
        yMasterSpring = yMaster
        xMasterSpring?.addListener(this)
        yMasterSpring?.addListener(this)
    }

    fun addSlaveSprings(xSpring: Spring, ySpring: Spring) {
        if (xSprings.size <= WebHeadService.MAX_VISIBLE_WEB_HEADS) {
            xSprings.add(xSpring)
            this.ySpring.add(ySpring)
        }
    }

    override fun onSpringUpdate(spring: Spring) {
        val masterX = xMasterSpring!!.currentValue.toInt()
        val masterY = yMasterSpring!!.currentValue.toInt()
        performGroupMove(masterX, masterY)
    }

    fun rest() {
        var lit = xSprings.descendingIterator()
        while (lit.hasNext()) {
            val s = lit.next() as Spring
            s.setAtRest()
        }
        lit = ySpring.descendingIterator()
        while (lit.hasNext()) {
            val s = lit.next() as Spring
            s.setAtRest()
        }
    }

    fun performGroupMove(masterX: Int, masterY: Int) {
        var xDisplacement = 0
        var yDisplacement = 0

        val xIter = xSprings.descendingIterator()
        val yIter = ySpring.descendingIterator()

        while (xIter.hasNext() && yIter.hasNext()) {
            val xSpring = xIter.next() as Spring
            val ySpring = yIter.next() as Spring
            if (displacementEnabled) {
                if (isRight(masterX)) {
                    xDisplacement += xDiff
                } else {
                    xDisplacement -= xDiff
                }
                yDisplacement += yDiff
            }
            xSpring.endValue = (masterX + xDisplacement).toDouble()
            ySpring.endValue = (masterY + yDisplacement).toDouble()
        }
    }

    /**
     * Used to determine if the given pixel is to the left or the right of the screen.
     *
     * @return true if right
     */
    private fun isRight(x: Int): Boolean {
        return x > (sDispWidth / 2)
    }

    override fun onSpringAtRest(spring: Spring) {
    }

    override fun onSpringActivate(spring: Spring) {
    }

    override fun onSpringEndStateChange(spring: Spring) {
    }

    fun disableDisplacement() {
        displacementEnabled = false
    }

    fun enableDisplacement() {
        displacementEnabled = true
    }

    companion object {
        private val xDiff = Utils.dpToPx(4.0)
        private val yDiff = Utils.dpToPx(1.7)

        fun create(context: Context): SpringChain2D {
            val metrics = DisplayMetrics()
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay.getMetrics(metrics)
            return SpringChain2D(metrics.widthPixels)
        }
    }
}
