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

// Phase 7: Converted to Kotlin
package arun.com.chromer.data.apps.store

import android.app.Application
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.data.common.App
import arun.com.chromer.data.common.BookStore
import arun.com.chromer.util.Utils
import io.paperdb.Book
import io.paperdb.Paper
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDiskStore @Inject constructor(
    private val application: Application
) : AppStore, BookStore {

    override fun getBook(): Book = Paper.book(APP_BOOK_NAME)

    override fun getApp(packageName: String): Observable<App> {
        return Observable.fromCallable {
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
    }

    override fun saveApp(app: App): Observable<App> {
        return Observable.just(app)
            .flatMap { app1 ->
                getBook().write(app1.packageName, app1)
                Timber.d("Wrote %s to storage", app1.packageName)
                Observable.just(app1)
            }
    }

    override fun isPackageBlacklisted(packageName: String): Boolean {
        return getBook().contains(packageName) && getApp(packageName).toBlocking().first().blackListed
    }

    override fun setPackageBlacklisted(packageName: String): Observable<App> {
        return getApp(packageName)
            .flatMap { app ->
                app.blackListed = true
                app.incognito = false
                Timber.d("Set %s as blacklisted", app.packageName)
                saveApp(app)
            }
    }

    override fun getPackageColorSync(packageName: String): Int {
        return getApp(packageName).toBlocking().first().color
    }

    override fun getPackageColor(packageName: String): Observable<Int> {
        return getApp(packageName)
            .map { app ->
                Timber.d("Got %d color for %s from storage", app.color, app.packageName)
                app.color
            }
    }

    override fun setPackageColor(packageName: String, color: Int): Observable<App> {
        return getApp(packageName)
            .flatMap { app ->
                app.color = color
                Timber.d("Saved %d color for %s", color, app.packageName)
                saveApp(app)
            }
    }

    override fun removeBlacklist(packageName: String): Observable<App> {
        return getApp(packageName)
            .flatMap { app ->
                app.blackListed = false
                Timber.d("Blacklist removed %s", app.packageName)
                saveApp(app)
            }
    }

    override fun removeIncognito(packageName: String): Observable<App> {
        return getApp(packageName)
            .flatMap { app ->
                app.incognito = false
                Timber.d("Incognito removed %s", app.packageName)
                saveApp(app)
            }
    }

    override fun getInstalledApps(): Observable<App> = Observable.empty()

    override fun isPackageIncognito(packageName: String): Boolean {
        return getBook().contains(packageName) && getApp(packageName).toBlocking().first().incognito
    }

    override fun setPackageIncognito(packageName: String): Observable<App> {
        return getApp(packageName)
            .flatMap { app ->
                app.incognito = true
                app.blackListed = false
                Timber.d("Set %s as incognito", app.packageName)
                saveApp(app)
            }
    }

    override fun allProviders(): Observable<List<Provider>> = Observable.empty()

    companion object {
        private const val APP_BOOK_NAME = "APPS"
    }
}
