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

// Phase 8: Converted from RxJava to Kotlin Flows/Coroutines
package arun.com.chromer.data.apps

import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.data.apps.store.AppStore
import arun.com.chromer.data.common.App
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock implementation of AppStore for testing.
 * Converted from RxJava Observable to Kotlin Flow/suspend functions.
 */
@Singleton
class MockAppSystemStore @Inject constructor() : AppStore {
  override suspend fun allProviders(): List<Provider> {
    return emptyList()
  }

  override suspend fun removeIncognito(packageName: String): App {
    TODO("not implemented")
  }

  override suspend fun getApp(packageName: String): App {
    TODO("not implemented")
  }

  override suspend fun saveApp(app: App): App {
    TODO("not implemented")
  }

  override fun isPackageBlacklisted(packageName: String): Boolean {
    return false
  }

  override suspend fun setPackageBlacklisted(packageName: String): App {
    TODO("not implemented")
  }

  override fun isPackageIncognito(packageName: String): Boolean {
    return false
  }

  override suspend fun setPackageIncognito(packageName: String): App {
    TODO("not implemented")
  }

  override fun getPackageColorSync(packageName: String): Int {
    return 0
  }

  override suspend fun getPackageColor(packageName: String): Int {
    return 0
  }

  override suspend fun setPackageColor(packageName: String, color: Int): App {
    TODO("not implemented")
  }

  override suspend fun removeBlacklist(packageName: String): App {
    TODO("not implemented")
  }

  override fun getInstalledApps(): Flow<App> {
    val app = App("App", "Package", true, true, 0)
    return flowOf(app)
  }
}