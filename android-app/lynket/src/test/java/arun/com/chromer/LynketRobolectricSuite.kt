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

package arun.com.chromer

import android.app.Application
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.settings.Preferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import javax.inject.Inject


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class)
abstract class LynketRobolectricSuite {
  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var preferences: Preferences

  @Inject
  lateinit var userPreferencesRepository: UserPreferencesRepository

  val application: Application get() = RuntimeEnvironment.getApplication()

  @Before
  fun setup() {
    hiltRule.inject()
  }


  internal fun clearPreferences() {
    runBlocking { userPreferencesRepository.clearAll() }
  }
}

