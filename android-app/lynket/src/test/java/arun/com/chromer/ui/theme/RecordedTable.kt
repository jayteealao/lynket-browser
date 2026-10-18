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

package arun.com.chromer.ui.theme

import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.fail

/**
 * A table of values recorded once and kept in `src/test/resources/theme/`. Every run writes what
 * it computed to `build/theme-records/` and compares it with the kept table line by line. To record
 * a table again, copy the written file over the kept one. A name that ends in `.gz` is gzipped.
 */
internal object RecordedTable {

  fun check(name: String, actual: List<String>) {
    val text = actual.joinToString("\n", postfix = "\n")
    val written = File("build/theme-records/$name").apply { parentFile.mkdirs() }
    write(written, text)
    val kept = File("src/test/resources/theme/$name")
    if (!kept.exists()) fail("No kept table $name: copy ${written.absolutePath} to ${kept.path} to record it")
    // A checkout may turn line ends into CRLF.
    val expected = read(kept).replace("\r\n", "\n").trimEnd('\n').split('\n')
    val mismatches = (0 until maxOf(expected.size, actual.size))
      .filter { expected.getOrNull(it) != actual.getOrNull(it) }
    if (mismatches.isNotEmpty()) {
      val shown = mismatches.take(20).joinToString("\n") {
        "line ${it + 1}\n  kept:     ${expected.getOrNull(it)}\n  computed: ${actual.getOrNull(it)}"
      }
      fail("$name: ${mismatches.size} of ${expected.size} lines differ (computed table: ${written.absolutePath})\n$shown")
    }
  }

  private fun write(file: File, text: String) {
    if (file.name.endsWith(".gz")) {
      GZIPOutputStream(file.outputStream()).use { it.write(text.toByteArray()) }
    } else {
      file.writeText(text)
    }
  }

  private fun read(file: File): String =
    if (file.name.endsWith(".gz")) {
      GZIPInputStream(file.inputStream()).use { String(it.readBytes()) }
    } else {
      file.readText()
    }
}
