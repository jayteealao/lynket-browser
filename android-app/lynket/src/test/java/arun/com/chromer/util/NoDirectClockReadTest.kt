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

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * The app reads the current time only through the injected clock: no class of the app reads the
 * system time itself, and the clock module is the one place that names the system clock.
 */
class NoDirectClockReadTest {

  private val sources = File("src/main/java")
  private val clockModule = "arun/com/chromer/di/hilt/ClockModule.kt"

  @Test
  fun noAppCodeReadsTheSystemTimeOutsideTheClockModule() {
    val files = sources.walk().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
    assertWithMessage("source files scanned under %s", sources.absolutePath).that(files.size).isGreaterThan(100)

    val reads = files
      .filter { it.relativeTo(sources).invariantSeparatorsPath != clockModule }
      .flatMap { file ->
        file.readLines().mapIndexedNotNull { index, line ->
          if (DIRECT_READS.any { it in line }) "${file.relativeTo(sources).invariantSeparatorsPath}:${index + 1}: ${line.trim()}" else null
        }
      }

    assertWithMessage("direct reads of the system time; take kotlin.time.Clock from the app instead").that(reads).isEmpty()
  }

  @Test
  fun theClockModuleNamesTheSystemClockOnce() {
    val text = File(sources, clockModule).readText()

    assertThat(Regex("""Clock\.System""").findAll(text).count()).isEqualTo(1)
  }

  private companion object {
    val DIRECT_READS = listOf("System.currentTimeMillis", "System::currentTimeMillis", "Clock.System")
  }
}
