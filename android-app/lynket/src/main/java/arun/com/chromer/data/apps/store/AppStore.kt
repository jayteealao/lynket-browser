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

package arun.com.chromer.data.apps.store

import androidx.annotation.ColorInt
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.data.common.App
import kotlinx.coroutines.flow.Flow

interface AppStore {
  suspend fun getApp(packageName: String): App

  suspend fun saveApp(app: App): App

  fun isPackageBlacklisted(packageName: String): Boolean

  suspend fun setPackageBlacklisted(packageName: String): App

  fun isPackageIncognito(packageName: String): Boolean

  suspend fun setPackageIncognito(packageName: String): App

  suspend fun removeIncognito(packageName: String): App

  @ColorInt
  fun getPackageColorSync(packageName: String): Int

  suspend fun getPackageColor(packageName: String): Int

  suspend fun setPackageColor(packageName: String, color: Int): App

  suspend fun removeBlacklist(packageName: String): App

  fun getInstalledApps(): Flow<App>

  suspend fun allProviders(): List<Provider>
}
