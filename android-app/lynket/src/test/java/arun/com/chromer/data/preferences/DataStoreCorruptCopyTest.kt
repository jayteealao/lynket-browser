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

import android.content.Context
import arun.com.chromer.di.data.TestDataStoreModule
import arun.com.chromer.di.hilt.DataStoreModule
import arun.com.chromer.util.TestClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** A corrupt settings file starts as on a new install, and a copy named from the app clock keeps its bytes. */
@RunWith(RobolectricTestRunner::class)
class DataStoreCorruptCopyTest {

  private val context: Context = RuntimeEnvironment.getApplication()

  @Test
  fun aCorruptSettingsFileStartsEmptyAndIsKeptUnderTheClockTime() = runTest {
    val file = TestDataStoreModule.tempStoreFile("user_preferences")
    val garbage = "this is not a protobuf".toByteArray() + ByteArray(64) { 0xFF.toByte() }
    file.writeBytes(garbage)

    val settings = DataStoreModule.settingsDataStore(context, TestClock(5_000L)) { file }.data.first()
    val freshFile = TestDataStoreModule.tempStoreFile("user_preferences")
    val fresh = DataStoreModule.settingsDataStore(context, TestClock(5_000L)) { freshFile }.data.first()

    // Empty as a first start: only what the migrations write on a new install.
    assertThat(settings.asMap()).isEqualTo(fresh.asMap())
    val kept = File(context.filesDir, "${DataStoreModule.CORRUPT_FOLDER}/user_preferences-5000.preferences_pb")
    assertThat(kept.exists()).isTrue()
    assertThat(kept.readBytes()).isEqualTo(garbage)
  }
}
