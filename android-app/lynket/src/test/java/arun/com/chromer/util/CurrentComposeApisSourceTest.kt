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
 * Composables find their activity through the composition and read resources from it, and the
 * home search bar and the reader's text-size slider use the state-based Material 3 forms.
 */
class CurrentComposeApisSourceTest {

  private val sources = File("src/main/java")
  private val ui = "arun/com/chromer/ui"

  private fun read(path: String) = File(sources, path).readText()

  private fun lines(files: List<File>, pattern: Regex) = files.flatMap { file ->
    file.readLines().mapIndexedNotNull { index, line ->
      if (pattern.containsMatchIn(line)) "${file.relativeTo(sources).invariantSeparatorsPath}:${index + 1}: ${line.trim()}" else null
    }
  }

  @Test
  fun noComposableUnwrapsAContextToFindItsActivityOrResources() {
    val composables = sources.walk()
      .filter { it.isFile && it.extension == "kt" && "@Composable" in it.readText() }
      .toList()
    assertWithMessage("files with composables under %s", sources.absolutePath).that(composables.size).isGreaterThan(50)

    val unwraps = lines(composables, Regex("""ContextWrapper|\.baseContext|findComponentActivity|LocalContext\.current\.(resources|getString)"""))
    assertWithMessage("take LocalActivity and LocalResources in a composable").that(unwraps).isEmpty()
  }

  @Test
  fun theScreenMessagesReadNoStringThroughAContext() {
    val files = listOf(
      "$ui/browsing/BrowsingBars.kt",
      "$ui/reader/ArticleBlocks.kt",
      "$ui/history/HistoryScreen.kt",
      "$ui/perapp/PerAppScreen.kt",
      "$ui/settings/BrowsingOptionsScreen.kt",
    ).map { File(sources, it) }

    assertThat(lines(files, Regex("""context\.getString\(|context\.resources"""))).isEmpty()
  }

  @Test
  fun theHomeSearchBarUsesTheStateBasedSearchBar() {
    val text = read("$ui/home/HomeSearchBar.kt")

    assertThat(text).doesNotContain("expanded =")
    assertThat(text).doesNotContain("onExpandedChange")
    assertThat(text).containsMatch("""SearchBar\(\s*state = """)
    assertThat(text).contains("ExpandedFullScreenSearchBar(")
    assertThat(text).containsMatch("""InputField\(\s*textFieldState = \w+,\s*searchBarState = """)
  }

  @Test
  fun theTextSizeSliderUsesASliderState() {
    val text = read("$ui/reader/ReaderScreen.kt")

    assertThat(text).contains("rememberSliderState(")
    assertThat(text).doesNotContainMatch("""Slider\(\s*value = """)
  }
}
