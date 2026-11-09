/*
 *
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
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

package arun.com.chromer.bubbles.webheads

import android.os.Build
import android.view.HapticFeedbackConstants
import arun.com.chromer.bubbles.webheads.motion.TrashTick

/**
 * The platform haptic of each trash moment: the lock is CONFIRM where the platform has it
 * (Android 11+) and VIRTUAL_KEY before; leaving is the lighter CLOCK_TICK.
 */
fun TrashTick.feedbackConstant(): Int = when (this) {
  // The version check reads SDK_INT itself, so lint can see that CONFIRM is guarded.
  TrashTick.LOCK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
  TrashTick.LEAVE -> HapticFeedbackConstants.CLOCK_TICK
}
