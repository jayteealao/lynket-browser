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

package arun.com.chromer.screenshot

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.ui.about.AboutScreen
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sample screenshot golden. Record with `./gradlew :lynket:recordRoborazziDebug`, compare with
 * `./gradlew :lynket:verifyRoborazziDebug`. Goldens live in `lynket/src/test/screenshots/`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SampleGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun aboutScreen_light() {
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        AboutScreen(navController = rememberNavController())
      }
    }
    composeRule.onRoot().captureRoboImage()
  }
}
