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

// Phase 7: Fully migrated to Hilt - removed legacy Dagger 2 AppComponent
package arun.com.chromer

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDex
import arun.com.chromer.util.ServiceManager
import arun.com.chromer.util.drawer.GlideDrawerImageLoader
import com.airbnb.epoxy.EpoxyController
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lynket Browser Application Class
 *
 * Phase 1.2: Migrated to Hilt (@HiltAndroidApp)
 * Phase 3: PaperDB removed (legacy storage)
 * Phase 7: Legacy Dagger 2 AppComponent removed - now 100% Hilt
 */
@HiltAndroidApp
open class Lynket : Application() {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface LynketEntryPoint {
    fun glideDrawerImageLoader(): GlideDrawerImageLoader
  }

  override fun onCreate() {
    super.onCreate()

    if (BuildConfig.DEBUG) {
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
    val entryPoint = EntryPointAccessors.fromApplication(this, LynketEntryPoint::class.java)
    DrawerImageLoader.init(entryPoint.glideDrawerImageLoader())
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
