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

import android.app.Application
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.data.common.App
import arun.com.chromer.data.common.BookStore
import arun.com.chromer.util.Utils
import io.paperdb.Book
import io.paperdb.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDiskStore @Inject constructor(
    private val application: Application
) : AppStore, BookStore {

    override fun getBook(): Book = Paper.book(APP_BOOK_NAME)

    override suspend fun getApp(packageName: String): App = withContext(Dispatchers.IO) {
        var app = Utils.createApp(application, packageName)
        try {
            app = getBook().read(packageName, app)
        } catch (e: Exception) {
            try {
                getBook().delete(packageName)
            } catch (ignored: Exception) {
            }
        }
        app
    }

    override suspend fun saveApp(app: App): App = withContext(Dispatchers.IO) {
        getBook().write(app.packageName, app)
        Timber.d("Wrote %s to storage", app.packageName)
        app
    }

    override fun isPackageBlacklisted(packageName: String): Boolean {
        return getBook().contains(packageName) && runBlocking { getApp(packageName).blackListed }
    }

    override suspend fun setPackageBlacklisted(packageName: String): App {
        val app = getApp(packageName)
        app.blackListed = true
        app.incognito = false
        Timber.d("Set %s as blacklisted", app.packageName)
        return saveApp(app)
    }

    override fun getPackageColorSync(packageName: String): Int {
        return runBlocking { getApp(packageName).color }
    }

    override suspend fun getPackageColor(packageName: String): Int {
        val app = getApp(packageName)
        Timber.d("Got %d color for %s from storage", app.color, app.packageName)
        return app.color
    }

    override suspend fun setPackageColor(packageName: String, color: Int): App {
        val app = getApp(packageName)
        app.color = color
        Timber.d("Saved %d color for %s", color, app.packageName)
        return saveApp(app)
    }

    override suspend fun removeBlacklist(packageName: String): App {
        val app = getApp(packageName)
        app.blackListed = false
        Timber.d("Blacklist removed %s", app.packageName)
        return saveApp(app)
    }

    override suspend fun removeIncognito(packageName: String): App {
        val app = getApp(packageName)
        app.incognito = false
        Timber.d("Incognito removed %s", app.packageName)
        return saveApp(app)
    }

    override fun getInstalledApps(): Flow<App> = emptyFlow()

    override fun isPackageIncognito(packageName: String): Boolean {
        return getBook().contains(packageName) && runBlocking { getApp(packageName).incognito }
    }

    override suspend fun setPackageIncognito(packageName: String): App {
        val app = getApp(packageName)
        app.incognito = true
        app.blackListed = false
        Timber.d("Set %s as incognito", app.packageName)
        return saveApp(app)
    }

    override suspend fun allProviders(): List<Provider> = emptyList()

    companion object {
        private const val APP_BOOK_NAME = "APPS"
    }
}
