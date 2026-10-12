/*
 * Lynket
 *
 * Copyright (C) 2019 Arunkumar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.tabs

import android.content.Intent
import android.os.Looper
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.amp.AmpResolverActivity
import arun.com.chromer.bubbles.webheads.WebHeadService
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.MainActivity
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowApplication
import javax.inject.Inject


/**
 * Created by Arunkumar on 15-12-2017.
 */
@HiltAndroidTest
class DefaultTabsManagerTest : LynketRobolectricSuite() {
  @Inject
  lateinit var tabs: TabsManager

  private val url = "https://www.example.com"

  @Test
  fun testInject() {
    assert(::tabs.isInitialized)
  }

  @Test
  @Ignore("Has issues causing timeout on CI")
  fun testHomeActivityClearedOnExternalIntent() {
    clearPreferences()
    preferences.mergeTabs(false)
    preferences.webHeads(false)

    val homeActivity = Robolectric.buildActivity(MainActivity::class.java).create().get()

    tabs.openUrl(application, Website(url), fromApp = false)
    assert(homeActivity.isFinishing)
  }

  @Ignore("Needs to be adapted to support Android 10 bubbles")
  @Test
  fun testFromWebheadsDoesNotLaunchNewWebHead() {
    clearPreferences()
    preferences.webHeads(true)
    val shadowApp = shadowOf(application)

    assertWebHeadServiceLaunched(shadowApp)

    tabs.openUrl(application, Website(url), fromApp = false, fromWebHeads = true)
    assert(shadowApp.nextStartedService == null)
  }

  @Test
  fun testAmpResolverOpened() {
    clearPreferences()
    preferences.ampMode(true)

    val shadowApp = shadowOf(application)
    tabs.openUrl(application, Website(url), fromApp = false, fromWebHeads = false)
    assert(
      awaitNextStartedActivity(shadowApp).component == Intent(
        application,
        AmpResolverActivity::class.java
      ).component
    )
  }

  /** openUrl() starts the activity from a background coroutine, so wait for it (bounded). */
  private fun awaitNextStartedActivity(shadowApp: ShadowApplication, timeoutMs: Long = 5_000): Intent {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      shadowApp.nextStartedActivity?.let { return it }
      Thread.sleep(10)
    }
    throw AssertionError("No activity started within $timeoutMs ms")
  }

  private fun assertWebHeadServiceLaunched(shadowApp: ShadowApplication) {
    tabs.openUrl(application, Website(url), fromApp = false, fromWebHeads = false)
    assert(
      shadowApp.peekNextStartedService().component == Intent(
        application,
        WebHeadService::class.java
      ).component
    )
    assert(shadowApp.nextStartedService.dataString == url)
  }
}
