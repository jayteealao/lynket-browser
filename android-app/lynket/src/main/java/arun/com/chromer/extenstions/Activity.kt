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

package arun.com.chromer.extenstions

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import arun.com.chromer.util.Utils

fun Activity.finishAndRemoveTaskCompat() {
  if (Utils.isLollipopAbove()) {
    finishAndRemoveTask()
  } else {
    finish()
  }
}

/**
 * Closes a pass-through activity, such as the link intercept. When it started another activity in
 * its own task (a web view or a Custom Tab with merged tabs off), only this activity closes, so the
 * page stays open. Otherwise the task holds nothing else and is removed from Recents.
 */
fun Activity.finishPassThrough() {
  if (taskHoldsOtherActivities()) finish() else finishAndRemoveTaskCompat()
}

internal fun Activity.taskHoldsOtherActivities(): Boolean {
  val tasks = runCatching { getSystemService(ActivityManager::class.java)?.appTasks }.getOrNull()
    ?: return false
  return tasks.any { task ->
    val info = runCatching { task.taskInfo }.getOrNull() ?: return@any false
    @Suppress("DEPRECATION")
    val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.taskId else info.id
    id == taskId && info.numActivities > 1
  }
}
