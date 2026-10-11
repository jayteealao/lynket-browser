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
import androidx.multidex.MultiDex
import arun.com.chromer.util.ServiceManager
import arun.com.chromer.util.drawer.GlideDrawerImageLoader
import com.airbnb.epoxy.EpoxyController
import com.mikepenz.materialdrawer.util.DrawerImageLoader
// Phase 3: PaperDB removed - legacy data storage replaced with Room + DataStore
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Lynket Browser Application Class
 *
 * Phase 1.2: Migrated to Hilt (@HiltAndroidApp)
 * Phase 3: PaperDB removed (legacy storage)
 */
@HiltAndroidApp
open class Lynket : Application() {

  @Inject
  lateinit var glideDrawerImageLoader: GlideDrawerImageLoader

  override fun onCreate() {
    super.onCreate()
    // Phase 3: Paper.init(this) removed - no longer using PaperDB

    if (BuildConfig.DEBUG) {
      // TODO: RxDogTag removed - no longer needed as RxJava is being phased out
      // RxDogTag.install()
      Timber.plant(Timber.DebugTree())
    }
    ServiceManager.takeCareOfServices(applicationContext)

    initMaterialDrawer()

    initEpoxy()
  }

  private fun initEpoxy() {
    EpoxyController.setGlobalDebugLoggingEnabled(true)
  }

  private fun initMaterialDrawer() {
    DrawerImageLoader.init(glideDrawerImageLoader)
      .withHandleAllUris(true)
  }

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    MultiDex.install(this)
  }

  companion object {
    init {
      AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }
  }
}
