/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.ui.theme

import android.app.Activity
import android.util.TypedValue
import androidx.lifecycle.ViewModelProvider
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.MainActivity
import arun.com.chromer.browsing.newtab.NewTabDialogActivity
import arun.com.chromer.browsing.newtab.NewTabViewModel
import arun.com.chromer.history.HistoryActivity
import arun.com.chromer.tabs.ui.TabsActivity
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import org.junit.After
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * A switch to night mode while a screen shows recreates it with its state and the night window
 * of its theme (the recorded night table), and the switch back gives the day window again. The
 * web head list and the sheets are recreated on a device instead: an open ModalBottomSheet never
 * goes idle in Robolectric (see DialogActivitiesRecreateTest).
 */
@HiltAndroidTest
@Config(sdk = [36], qualifiers = "notnight")
class ActivityNightRecreateTest : LynketRobolectricSuite() {

  @After
  fun backToDay() = RuntimeEnvironment.setQualifiers("notnight")

  @Test
  fun newTabKeepsTheTypedTextAndFollowsNightMode() {
    val controller = Robolectric.buildActivity(NewTabDialogActivity::class.java).setup()
    ViewModelProvider(controller.get())[NewTabViewModel::class.java].onQueryChange("exam")
    switchAndCheck(controller)
    assertThat(ViewModelProvider(controller.get())[NewTabViewModel::class.java].query.value).isEqualTo("exam")
  }

  @Test
  fun homeFollowsNightMode() = switchAndCheck(Robolectric.buildActivity(MainActivity::class.java).setup())

  @Test
  fun historyFollowsNightMode() = switchAndCheck(Robolectric.buildActivity(HistoryActivity::class.java).setup())

  @Test
  fun tabsFollowsNightMode() = switchAndCheck(Robolectric.buildActivity(TabsActivity::class.java).setup())

  /** Night: recreated with the night window; day again: recreated with the day window. */
  private fun <T : Activity> switchAndCheck(controller: ActivityController<T>) {
    for (mode in listOf("night", "day")) {
      RuntimeEnvironment.setQualifiers(if (mode == "night") "night" else "notnight")
      controller.recreate()
      val activity = controller.get()
      assertThat(activity.isFinishing).isFalse()
      val recorded = recordedWindow(activity.javaClass.name, mode)
      assertThat(resolve(activity, android.R.attr.isLightTheme)).isEqualTo(recorded.getValue("isLightTheme"))
      assertThat(resolve(activity, android.R.attr.windowBackground)).isEqualTo(recorded.getValue("windowBackground"))
    }
  }

  private fun resolve(activity: Activity, attr: Int): String {
    val value = TypedValue()
    if (!activity.theme.resolveAttribute(attr, value, true)) return "unset"
    return when (value.type) {
      TypedValue.TYPE_INT_BOOLEAN -> (value.data != 0).toString()
      in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> "#%08X".format(value.data)
      else -> "type ${value.type}"
    }
  }

  /** The window attributes of [activityName] recorded for API 36 in [mode] (day or night). */
  private fun recordedWindow(activityName: String, mode: String): Map<String, String> =
    File("src/test/resources/theme/window/api36-$mode.txt").readLines()
      .map { it.trimEnd('\r') }
      .filter { it.startsWith("$activityName ") }
      .associate { it.substringAfter(' ').substringBefore('=') to it.substringAfter('=') }
}
