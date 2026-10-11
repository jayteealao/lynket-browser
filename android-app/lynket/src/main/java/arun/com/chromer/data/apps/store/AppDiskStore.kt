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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.data.common.App
import arun.com.chromer.data.preferences.PreferencesSnapshot
import arun.com.chromer.data.preferences.readBoolean
import arun.com.chromer.data.preferences.readInt
import arun.com.chromer.di.hilt.ApplicationScope
import arun.com.chromer.di.hilt.PerAppRules
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app rules (blacklist, incognito, colour per package), kept in the `per_app_rules`
 * DataStore so they survive restarts and updates. Release 2.1.3's rules are read in once by
 * [PaperPerAppMigration]. The synchronous members read a hot copy of the store.
 */
@Singleton
class AppDiskStore @Inject constructor(
    private val application: Application,
    @PerAppRules dataStore: DataStore<Preferences>,
    @ApplicationScope appScope: CoroutineScope,
) : AppStore {

    private val store = PreferencesSnapshot(dataStore, appScope)

    /** Latest stored rules; null until the first read finishes. */
    val snapshot: StateFlow<Preferences?> get() = store.flow

    private fun rules(): Preferences = store.current()

    override suspend fun getApp(packageName: String): App = withContext(Dispatchers.IO) {
        Utils.createApp(application, packageName).apply {
            val rules = rules()
            blackListed = rules.readBoolean(blacklistedKey(packageName).name, false)
            incognito = rules.readBoolean(incognitoKey(packageName).name, false)
            color = rules.readInt(colorKey(packageName).name, Constants.NO_COLOR)
        }
    }

    override suspend fun saveApp(app: App): App = withContext(Dispatchers.IO) {
        val pkg = app.packageName
        store.edit { rules ->
            if (app.blackListed) rules[blacklistedKey(pkg)] = true else rules.remove(blacklistedKey(pkg))
            if (app.incognito) rules[incognitoKey(pkg)] = true else rules.remove(incognitoKey(pkg))
            if (app.color != Constants.NO_COLOR) rules[colorKey(pkg)] = app.color else rules.remove(colorKey(pkg))
        }
        Timber.d("Saved rules of %s", pkg)
        app
    }

    override fun isPackageBlacklisted(packageName: String): Boolean =
        rules().readBoolean(blacklistedKey(packageName).name, false)

    override suspend fun setPackageBlacklisted(packageName: String): App {
        val app = getApp(packageName)
        app.blackListed = true
        app.incognito = false
        Timber.d("Set %s as blacklisted", app.packageName)
        return saveApp(app)
    }

    override fun getPackageColorSync(packageName: String): Int =
        rules().readInt(colorKey(packageName).name, Constants.NO_COLOR)

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

    override fun isPackageIncognito(packageName: String): Boolean =
        rules().readBoolean(incognitoKey(packageName).name, false)

    override suspend fun setPackageIncognito(packageName: String): App {
        val app = getApp(packageName)
        app.incognito = true
        app.blackListed = false
        Timber.d("Set %s as incognito", app.packageName)
        return saveApp(app)
    }

    override suspend fun allProviders(): List<Provider> = emptyList()

    /** Every package with a stored rule, read from the store itself (used by the debug dump). */
    suspend fun storedRules(): List<App> {
        val rules = store.fresh()
        val packages = rules.asMap().keys.mapNotNull { key ->
            PREFIXES.firstOrNull { key.name.startsWith(it) }?.let { key.name.removePrefix(it) }
        }.toSortedSet()
        return packages.map { pkg ->
            App(
                appName = "",
                packageName = pkg,
                blackListed = rules.readBoolean(blacklistedKey(pkg).name, false),
                incognito = rules.readBoolean(incognitoKey(pkg).name, false),
                color = rules.readInt(colorKey(pkg).name, Constants.NO_COLOR),
            )
        }
    }

    companion object {
        private const val BLACKLISTED = "blacklisted."
        private const val INCOGNITO = "incognito."
        private const val COLOR = "color."
        private val PREFIXES = listOf(BLACKLISTED, INCOGNITO, COLOR)

        fun blacklistedKey(packageName: String) = booleanPreferencesKey(BLACKLISTED + packageName)
        fun incognitoKey(packageName: String) = booleanPreferencesKey(INCOGNITO + packageName)
        fun colorKey(packageName: String) = intPreferencesKey(COLOR + packageName)
    }
}
