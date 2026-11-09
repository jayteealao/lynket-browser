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

package arun.com.chromer.util

import android.app.Application
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The colour of a source app is found on the app's own coroutine scope, not by a queued background
 * job: no app code names the deprecated job service, and the merged manifest declares no job service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NoJobIntentServiceTest {

  private val sources = File("src/main/java")

  @Test
  fun noAppCodeNamesTheJobServiceOrTheOldColourJob() {
    val files = sources.walk().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
    assertWithMessage("source files scanned under %s", sources.absolutePath).that(files.size).isGreaterThan(100)

    val uses = files.flatMap { file ->
      file.readLines().mapIndexedNotNull { index, line ->
        if (NAMES.any { it in line }) "${file.relativeTo(sources).invariantSeparatorsPath}:${index + 1}: ${line.trim()}" else null
      }
    }

    assertWithMessage("uses of the job service; run the work on the app's coroutine scope instead").that(uses).isEmpty()
  }

  @Test
  fun theMergedManifestDeclaresNoJobService() {
    val application = RuntimeEnvironment.getApplication()
    val services = application.packageManager
      .getPackageInfo(application.packageName, PackageManager.GET_SERVICES)
      .services.orEmpty()
    assertWithMessage("services read from the merged manifest").that(services.map { it.name }).isNotEmpty()

    assertWithMessage("services named after the old colour job")
      .that(services.map { it.name }.filter { it.endsWith("AppColorExtractorJob") })
      .isEmpty()
    assertWithMessage("services that only the job scheduler may bind")
      .that(services.filter { it.permission == BIND_JOB_SERVICE }.map { it.name })
      .isEmpty()
  }

  private companion object {
    val NAMES = listOf("JobIntentService", "AppColorExtractorJob")
    const val BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE"
  }
}
