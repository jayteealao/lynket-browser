/*
 *
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
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

package arun.com.chromer.di

import android.content.Intent
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.customtabs.bottombar.BottomBarReceiver
import arun.com.chromer.browsing.customtabs.callbacks.MinimizeBroadcastReceiver
import arun.com.chromer.bubbles.webheads.WebHeadService
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric

/**
 * The receivers and the web head service are not exported and cannot be reached from a shell on a
 * device, so these tests check on the JVM that Hilt injects their fields when Android creates them.
 */
@HiltAndroidTest
class HiltEntryPointsTest : LynketRobolectricSuite() {

  @Test
  fun bottomBarReceiverIsInjectedOnReceive() {
    val receiver = BottomBarReceiver()

    receiver.onReceive(application, Intent())

    assertTrue(runCatching { receiver.tabsManager }.isSuccess)
  }

  @Test
  fun minimizeReceiverIsInjectedOnReceive() {
    val receiver = MinimizeBroadcastReceiver()

    receiver.onReceive(application, Intent())

    assertTrue(runCatching { receiver.tabsManager }.isSuccess)
  }

  @Test
  fun webHeadServiceIsInjectedOnCreate() {
    val service = Robolectric.buildService(WebHeadService::class.java).create().get()

    assertTrue(runCatching { service.websiteRepository }.isSuccess)
    assertTrue(runCatching { service.tabsManager }.isSuccess)
    assertTrue(runCatching { service.articlePreloader }.isSuccess)
  }
}
