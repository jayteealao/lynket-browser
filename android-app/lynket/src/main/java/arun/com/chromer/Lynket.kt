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

package arun.com.chromer

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.multidex.MultiDex
import arun.com.chromer.data.apps.store.AppDiskStore
import arun.com.chromer.data.preferences.UserPreferencesRepository
import com.airbnb.epoxy.EpoxyController
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Lynket Browser Application Class
 *
 * Phase 1.2: Migrated to Hilt (@HiltAndroidApp)
 * Settings and per-app rules live in DataStore; release 2.1.3's PaperDB rules are read once by
 * the per-app rule store's migration.
 */
@HiltAndroidApp
open class Lynket : Application() {

  // Injecting the two stores starts their hot copies on the IO scope without blocking.
  @Inject
  lateinit var userPreferencesRepository: UserPreferencesRepository

  @Inject
  lateinit var appDiskStore: AppDiskStore

  override fun onCreate() {
    super.onCreate()
    Timber.d(
      "Stores warming up: settings ready=%b, per-app rules ready=%b",
      userPreferencesRepository.snapshot.value != null,
      appDiskStore.snapshot.value != null,
    )

    if (BuildConfig.DEBUG) {
      // TODO: RxDogTag removed - no longer needed as RxJava is being phased out
      // RxDogTag.install()
      Timber.plant(Timber.DebugTree())
    }
    // Links now name their source app when they open, so the old app-detection service and its
    // notification channel are gone; remove the channel earlier versions created.
    NotificationManagerCompat.from(this).deleteNotificationChannel(OLD_APP_DETECTION_CHANNEL)

    initEpoxy()
  }

  private fun initEpoxy() {
    EpoxyController.setGlobalDebugLoggingEnabled(true)
  }

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    MultiDex.install(this)
  }

  companion object {
    private const val OLD_APP_DETECTION_CHANNEL = "App detection service"

    init {
      AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }
  }
}
