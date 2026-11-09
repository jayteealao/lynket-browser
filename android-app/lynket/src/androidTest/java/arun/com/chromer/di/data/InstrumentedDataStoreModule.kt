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
import java.util.UUID
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Each Hilt test component of the instrumented tests gets its own DataStore files in the app's
 * cache, with the real migrations: DataStore refuses two active instances on one file in a process,
 * and Hilt builds a component per test. The app's own settings on the device stay untouched.
 *
 * The JVM tests replace the same module with `TestDataStoreModule` (src/test). Only the place of the
 * files differs, so a store, qualifier or migration added to `DataStoreModule` goes in both.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object InstrumentedDataStoreModule {

  @Provides
  @Singleton
  @ApplicationScope
  fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Provides
  @Singleton
  fun provideDataStore(@ApplicationContext context: Context, clock: Clock): DataStore<Preferences> =
    DataStoreModule.settingsDataStore(context, clock) { storeFile(context, "user_preferences") }

  @Provides
  @Singleton
  @PerAppRules
  fun providePerAppRulesDataStore(@ApplicationContext context: Context, clock: Clock): DataStore<Preferences> =
    DataStoreModule.perAppRulesDataStore(context, clock) { storeFile(context, "per_app_rules") }

  private fun storeFile(context: Context, name: String): File =
    File(File(testStoreRoot(context), UUID.randomUUID().toString()), "$name.preferences_pb")
}

private fun testStoreRoot(context: Context) = File(context.cacheDir, "test-datastore")

/** Deletes the store files of the instrumented tests, so repeated runs leave none on the device. */
internal fun deleteTestStores(context: Context) {
  testStoreRoot(context).deleteRecursively()
}
