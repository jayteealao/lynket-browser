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

package arun.com.chromer.ui.browsing

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.ui.theme.RecordedTable
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The TalkBack label of the reader and web view overflow button reads as before in every
 * language it had: the kept table holds the support library's label per language, recorded while
 * the app still used it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class OverflowLabelTest {

  private val context: Application get() = ApplicationProvider.getApplicationContext()

  @Test
  fun the_overflow_label_matches_the_recorded_translations() {
    val libraryLabel = context.resources.getIdentifier(
      "abc_action_menu_overflow_description",
      "string",
      context.resources.getResourcePackageName(R.string.app_name),
    )
    val rows = qualifiers().map { qualifier ->
      val localised = context.createConfigurationContext(Configuration().apply { setLocale(localeOf(qualifier)) })
      val label = localised.getString(R.string.overflow_menu_description)
      // While the library is still on the classpath, its own label is the reference as well.
      if (libraryLabel != 0) assertThat(label).isEqualTo(localised.getString(libraryLabel))
      "${qualifier.ifEmpty { "default" }} $label"
    }
    assertThat(rows).hasSize(86)
    assertThat(context.getString(R.string.overflow_menu_description)).isEqualTo("More options")
    RecordedTable.check("overflow-labels.txt", rows)
  }

  /** The resource qualifiers that hold the label: "" for the default, else "de", "en-rGB", "b+sr+Latn". */
  private fun qualifiers(): List<String> =
    File("src/main/res").listFiles().orEmpty()
      .filter { File(it, "strings_overflow.xml").exists() }
      .map { it.name.removePrefix("values").removePrefix("-") }
      .sorted()

  private fun localeOf(qualifier: String): Locale = when {
    qualifier.isEmpty() -> Locale.ROOT
    qualifier.startsWith("b+") -> Locale.forLanguageTag(qualifier.removePrefix("b+").replace('+', '-'))
    "-r" in qualifier -> Locale(qualifier.substringBefore("-r"), qualifier.substringAfter("-r"))
    else -> Locale(qualifier)
  }
}
