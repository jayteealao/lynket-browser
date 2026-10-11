/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.di.hilt

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import arun.com.chromer.data.apps.store.PaperPerAppMigration
import arun.com.chromer.data.preferences.LegacySettingsMigration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope for work that lives as long as the process, such as the settings snapshots. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

/** The DataStore that holds the per-app rules (blacklist, incognito and colour per app). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PerAppRules

/**
 * The two DataStores of the app:
 * - `user_preferences`: every setting; filled once from the old settings file by
 *   [LegacySettingsMigration].
 * - `per_app_rules`: the per-app rules; filled once from the store release 2.1.3 wrote by
 *   [PaperPerAppMigration].
 * Neither migration deletes the data it reads.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val USER_PREFERENCES_NAME = "user_preferences"
    private const val PER_APP_RULES_NAME = "per_app_rules"

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> =
        settingsDataStore(context) { context.preferencesDataStoreFile(USER_PREFERENCES_NAME) }

    @Provides
    @Singleton
    @PerAppRules
    fun providePerAppRulesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> =
        perAppRulesDataStore(context) { context.preferencesDataStoreFile(PER_APP_RULES_NAME) }

    fun settingsDataStore(context: Context, file: () -> File): DataStore<Preferences> =
        create(file, LegacySettingsMigration(context))

    fun perAppRulesDataStore(context: Context, file: () -> File): DataStore<Preferences> =
        create(file, PaperPerAppMigration(context))

    private fun create(file: () -> File, migration: DataMigration<Preferences>): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { exception ->
                Timber.e(exception, "DataStore file corrupted, using empty preferences")
                emptyPreferences()
            },
            migrations = listOf(migration),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = file,
        )
}
