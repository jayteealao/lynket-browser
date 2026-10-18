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
import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppTask

/**
 * The link intercept closes with [finishPassThrough]. With merged tabs off, the web view or the
 * Custom Tab starts in the intercept's own task, so removing that task closed the page at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FinishPassThroughTest {

  private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

  private fun appTasks(vararg tasks: Pair<Int, Int>) {
    val appTasks = tasks.map { (id, activities) ->
      ShadowAppTask.newInstance().also { task ->
        shadowOf(task).setTaskInfo(
          ActivityManager.RecentTaskInfo().apply {
            taskId = id
            numActivities = activities
          }
        )
      }
    }
    shadowOf(activity.getSystemService(ActivityManager::class.java)).setAppTasks(appTasks)
  }

  @Test
  fun aPageStartedInTheSameTaskKeepsTheTask() {
    appTasks(activity.taskId to 2)

    assertThat(activity.taskHoldsOtherActivities()).isTrue()
    activity.finishPassThrough()
    assertThat(activity.isFinishing).isTrue()
  }

  @Test
  fun anInterceptAloneInItsTaskRemovesTheTask() {
    appTasks(activity.taskId to 1)

    assertThat(activity.taskHoldsOtherActivities()).isFalse()
  }

  @Test
  fun otherTasksOfTheAppDoNotCount() {
    appTasks(activity.taskId to 1, activity.taskId + 1 to 3)

    assertThat(activity.taskHoldsOtherActivities()).isFalse()
  }

  @Test
  fun noTaskInformationRemovesTheTask() {
    appTasks()

    assertThat(activity.taskHoldsOtherActivities()).isFalse()
  }
}
