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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.browsing.article.ReaderState
import arun.com.chromer.browsing.menu.BrowsingScreen
import arun.com.chromer.browsing.menu.browsingBottomBarItems
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.reader.ReaderScreen
import arun.com.chromer.ui.reader.readerMenu
import arun.com.chromer.ui.reader.sampleReaderArticle
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the reader's text-size card at the smallest, a middle and the largest size, in the
 * light and the dark theme, and the whole reader at the largest size (the card keeps one line).
 * The card opens from the menu as a person opens it. Record with `./gradlew :lynket:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TextSizeCardParityGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val text = { id: Int -> context.getString(id) }

  private fun open(size: Int, theme: ThemeMode = ThemeMode.Light) {
    composeRule.setContent {
      ChromerTheme(themeMode = theme) {
        ReaderScreen(
          state = ReaderState.Content(sampleReaderArticle()),
          menuItems = readerMenu(text),
          bottomBarItems = browsingBottomBarItems(BrowsingScreen.Article, true, text),
          textSizeIncrement = size,
          savedTextSizeIncrement = size,
          accent = Color(0xFF3F51B5),
          onClose = {},
          onMenuItem = {},
          onRetry = {},
          onOpenInBrowser = {},
          onImage = {},
          onKeyword = {},
          onTextSizeChange = {},
          onSaveTextSize = {},
        )
      }
    }
    composeRule.onNodeWithTag("menu-menu_text_size").performClick()
    composeRule.waitForIdle()
  }

  private fun card(size: Int, theme: ThemeMode = ThemeMode.Light) {
    open(size, theme)
    composeRule.onNodeWithTag("text-size-card").captureRoboImage()
  }

  @Test fun textSize_card_plus0_light_phone() = card(0)
  @Test fun textSize_card_plus0_dark_phone() = card(0, ThemeMode.Dark)
  @Test fun textSize_card_plus4_light_phone() = card(4)
  @Test fun textSize_card_plus15_light_phone() = card(15)

  @Test
  fun textSize_screen_plus15_light_phone() {
    open(15)
    composeRule.onRoot().captureRoboImage()
  }
}
