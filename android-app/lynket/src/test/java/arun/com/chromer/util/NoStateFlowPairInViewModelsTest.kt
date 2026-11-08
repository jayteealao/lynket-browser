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

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * View models expose their state as one property with an explicit backing field
 * (`val state: StateFlow<S>` then `field = MutableStateFlow(...)`), never as a private mutable flow
 * paired with a public `asStateFlow()` copy.
 */
class NoStateFlowPairInViewModelsTest {

  private val sources = File("src/main/java")

  private val viewModels = sources.walk()
    .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
    .associateBy { it.nameWithoutExtension }

  @Test
  fun noViewModelPairsAMutableFlowWithAsStateFlow() {
    assertWithMessage("view models found under %s", sources.absolutePath)
      .that(viewModels.keys)
      .containsAtLeastElementsIn(WITH_BACKING_FIELD)

    val pairs = viewModels.values.flatMap { file ->
      file.readLines().mapIndexedNotNull { index, line ->
        if ("asStateFlow()" in line) "${file.relativeTo(sources).invariantSeparatorsPath}:${index + 1}: ${line.trim()}" else null
      }
    }

    assertWithMessage("asStateFlow() in a view model; declare the property with an explicit backing field instead")
      .that(pairs)
      .isEmpty()
  }

  @Test
  fun eachStateHoldingViewModelUsesAnExplicitBackingField() {
    val without = WITH_BACKING_FIELD.filterNot { name ->
      viewModels.getValue(name).readLines().any { it.trim().startsWith("field = ") }
    }

    assertWithMessage("view models with no explicit backing field").that(without).isEmpty()
  }

  private companion object {
    val WITH_BACKING_FIELD = listOf(
      "AmpResolverViewModel",
      "BrowsingArticleViewModel",
      "ModernProviderSelectionViewModel",
      "ModernHistoryViewModel",
      "ShortcutCreatorViewModel",
      "ModernTabsViewModel",
      "PerAppViewModel",
      "SettingsViewModel",
      "NewTabViewModel",
    )
  }
}
