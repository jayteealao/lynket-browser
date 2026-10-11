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

package arun.com.chromer.data.preferences

import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import timber.log.Timber
import java.io.File
import java.nio.file.Files

/** A DataStore on its own file and scope; [close] releases the file for a new instance. */
class TestStore(
  val file: File = File(Files.createTempDirectory("datastore").toFile(), "test.preferences_pb"),
  migrations: List<DataMigration<Preferences>> = emptyList(),
) {
  private val job = Job()
  val scope = CoroutineScope(job + Dispatchers.IO)
  val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
    migrations = migrations,
    scope = scope,
    produceFile = { file },
  )

  suspend fun close() = job.cancelAndJoin()
}

/** Collects the error logs of a test. */
class RecordingTree : Timber.Tree() {
  val errors = mutableListOf<String>()

  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    if (priority >= Log.ERROR) errors += message
  }
}

/** Reads a file from the test resources. */
fun resourceBytes(path: String): ByteArray =
  requireNotNull(TestStore::class.java.classLoader!!.getResourceAsStream(path)) { "missing resource $path" }
    .use { it.readBytes() }
