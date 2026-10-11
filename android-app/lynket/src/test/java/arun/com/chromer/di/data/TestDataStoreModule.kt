/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.di.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import arun.com.chromer.di.hilt.ApplicationScope
import arun.com.chromer.di.hilt.DataStoreModule
import arun.com.chromer.di.hilt.PerAppRules
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.file.Files
import javax.inject.Singleton

/**
 * Each Hilt test component gets its own DataStore files, with the real migrations: DataStore
 * refuses two active instances on one file in a process, and Hilt builds a component per test.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {

  @Provides
  @Singleton
  @ApplicationScope
  fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Provides
  @Singleton
  fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    DataStoreModule.settingsDataStore(context) { tempStoreFile("user_preferences") }

  @Provides
  @Singleton
  @PerAppRules
  fun providePerAppRulesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    DataStoreModule.perAppRulesDataStore(context) { tempStoreFile("per_app_rules") }

  fun tempStoreFile(name: String): File =
    File(Files.createTempDirectory("datastore").toFile(), "$name.preferences_pb")
}
