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

package arun.com.chromer.data.apps

import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.data.apps.store.AppStore
import arun.com.chromer.data.common.App
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by arunk on 10-02-2018.
 */
@Singleton
class MockAppSystemStore @Inject constructor() : AppStore {
  override suspend fun allProviders(): List<Provider> = emptyList()

  override suspend fun removeIncognito(packageName: String): App {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override suspend fun getApp(packageName: String): App {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override suspend fun saveApp(app: App): App {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isPackageBlacklisted(packageName: String): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override suspend fun setPackageBlacklisted(packageName: String): App {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isPackageIncognito(packageName: String): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override suspend fun setPackageIncognito(packageName: String): App {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getPackageColorSync(packageName: String): Int {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override suspend fun getPackageColor(packageName: String): Int {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override suspend fun setPackageColor(packageName: String, color: Int): App {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override suspend fun removeBlacklist(packageName: String): App {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getInstalledApps(): Flow<App> {
    val app = App("App", "Package", true, true, 0)
    return flowOf(app)
  }
}