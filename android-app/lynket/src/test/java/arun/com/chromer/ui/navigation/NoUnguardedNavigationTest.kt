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

package arun.com.chromer.ui.navigation

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Every tap handler of a screen that opens or closes a screen drops the tap while a screen
 * transition runs: a lambda that calls the navigator straight away (`= { navigator.back() }`,
 * `-> navigator.navigateToTabs()`) sits inside a `dropUnlessResumed` or `dropUnlessResumedWith`
 * block. The display's own back handler is the one exception: system back may interrupt a
 * transition. The effects that hand a link on and pop their own key call the navigator outside a
 * tap handler, so these patterns do not match them.
 */
class NoUnguardedNavigationTest {

  private val sources = File("src/main/java/arun/com/chromer/ui")

  @Test
  fun everyNavigatingTapIsGuarded() {
    val files = sources.walk().filter { it.isFile && it.extension == "kt" }.toList()
    assertWithMessage("screen files scanned under %s", sources.absolutePath).that(files.size).isGreaterThan(30)

    val unguarded = files.flatMap { file -> unguardedCalls(file) }

    assertWithMessage("navigating taps outside dropUnlessResumed / dropUnlessResumedWith").that(unguarded).isEmpty()
  }

  /** The `path:line: text` of each navigating lambda in [file] with no guard block around it. */
  private fun unguardedCalls(file: File): List<String> {
    val path = file.relativeTo(sources).invariantSeparatorsPath
    val text = file.readText()
    val guardedRanges = guardBlocks(text)
    return NAVIGATING_LAMBDA.findAll(text)
      .filter { match -> guardedRanges.none { match.range.first in it } }
      .map { match ->
        val line = text.substring(0, match.range.first).count { it == '\n' } + 1
        "$path:$line: ${text.lines()[line - 1].trim()}"
      }
      .filterNot { it.substringAfter(": ") in ALLOWED["$path"].orEmpty() }
      .toList()
  }

  /** The character ranges of every `dropUnlessResumed { ... }` and `dropUnlessResumedWith { ... }` body. */
  private fun guardBlocks(text: String): List<IntRange> = GUARD_OPEN.findAll(text).map { match ->
    val open = match.range.last
    var depth = 0
    var end = text.length - 1
    for (i in open until text.length) {
      when (text[i]) {
        '{' -> depth++
        '}' -> if (--depth == 0) { end = i; break }
      }
    }
    open..end
  }.toList()

  private companion object {
    val NAVIGATING_LAMBDA = Regex("""(=\s*\{\s*navigator\.(navigate\w*|back)\()|(->\s*navigator\.navigate\w*\()""")
    val GUARD_OPEN = Regex("""dropUnlessResumed(With)?\s*(\([^)]*\))?\s*\{""")

    /** The display's back handler: Navigation 3 owns system back and predictive back. */
    val ALLOWED = mapOf("navigation/ChromerNavigation.kt" to setOf("onBack = { navigator.back() },"))
  }
}
