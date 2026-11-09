// Phase 8: Converted from RxJava to Kotlin Flows/Coroutines
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

package arun.com.chromer.data.apps

import arun.com.chromer.browsing.customtabs.dynamictoolbar.AppColorExtractor
import arun.com.chromer.data.apps.qualifiers.System
import arun.com.chromer.data.apps.store.AppStore
import arun.com.chromer.data.common.App
import arun.com.chromer.data.common.qualifiers.Disk
import arun.com.chromer.di.hilt.ApplicationScope
import arun.com.chromer.di.hilt.IoDispatcher
import arun.com.chromer.shared.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppRepository
@Inject internal constructor(
  @param:Disk private val diskStore: AppStore,
  @param:System private val systemStore: AppStore,
  @param:ApplicationScope private val appScope: CoroutineScope,
  @param:IoDispatcher private val extractionDispatcher: CoroutineDispatcher,
  private val extractor: AppColorExtractor,
) : AppRepository {

  /**
   * The packages whose colour this process has already looked for. A package stays here whatever the
   * result, so a second link from an app whose colour is pending, or that has none, starts no second
   * extraction; the next start of the app looks again.
   */
  private val requested: MutableSet<String> = ConcurrentHashMap.newKeySet()

  override suspend fun getApp(packageName: String): App {
    return diskStore.getApp(packageName)
  }

  override suspend fun saveApp(app: App): App {
    return diskStore.saveApp(app)
  }

  override suspend fun getPackageColor(packageName: String): Int {
    val color = diskStore.getPackageColor(packageName)
    if (color == Constants.NO_COLOR) startColorExtraction(packageName)
    return color
  }

  /** Looks for the colour of [packageName] once per process, off the main thread, and stores it. */
  private fun startColorExtraction(packageName: String) {
    if (!requested.add(packageName)) return
    Timber.d("Color not found for %s, starting extraction.", packageName)
    appScope.launch(extractionDispatcher) {
      try {
        val color = extractor.extract(packageName)
        if (color != Constants.NO_COLOR) diskStore.setPackageColor(packageName, color)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Colour extraction failed for %s", packageName)
      }
    }
  }

  override suspend fun setPackageColor(packageName: String, color: Int): App {
    return diskStore.setPackageColor(packageName, color)
  }

  override suspend fun removeBlacklist(packageName: String): App {
    return diskStore.removeBlacklist(packageName)
  }

  override fun isPackageBlacklisted(packageName: String): Boolean {
    return diskStore.isPackageBlacklisted(packageName)
  }

  override suspend fun setPackageBlacklisted(packageName: String): App {
    return diskStore.setPackageBlacklisted(packageName)
  }

  /** Reads the in-memory per-app rules, so the link path does not wait on the main thread. */
  override fun getPackageColorSync(packageName: String): Int {
    val color = diskStore.getPackageColorSync(packageName)
    if (color == Constants.NO_COLOR) startColorExtraction(packageName)
    return color
  }

  override fun isPackageIncognito(packageName: String): Boolean {
    return diskStore.isPackageIncognito(packageName)
  }

  override suspend fun setPackageIncognito(packageName: String): App {
    return diskStore.setPackageIncognito(packageName)
  }

  override suspend fun removeIncognito(packageName: String): App {
    return diskStore.removeIncognito(packageName)
  }

  override suspend fun allApps(): List<App> {
    val appComparator = App.PerAppListComparator()
    return systemStore.getInstalledApps()
      .map { app ->
        app.blackListed = diskStore.isPackageBlacklisted(app.packageName)
        app.incognito = diskStore.isPackageIncognito(app.packageName)
        app
      }
      .toList()
      .sortedWith(appComparator)
  }

  override suspend fun allProviders() = systemStore.allProviders()
}
