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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.bubbles.webheads.motion.TrashState
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadState
import arun.com.chromer.bubbles.webheads.ui.TrashTarget
import arun.com.chromer.bubbles.webheads.ui.WebHeadBubble
import arun.com.chromer.bubbles.webheads.ui.context.ShareAllFormat
import arun.com.chromer.bubbles.webheads.ui.context.WebHeadContextActions
import arun.com.chromer.bubbles.webheads.ui.context.WebHeadContextContent
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.ui.permission.PermissionExplainerDialog
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Goldens of the web head, the trash, the list sheet and the permission explainer. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WebHeadsGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun head(url: String, count: Int = 1, badgeOnLeft: Boolean = true, colour: Color? = null) =
    WebHeadState(url).apply {
      isMaster = true
      revealed = true
      badgeCount = count
      this.badgeOnLeft = badgeOnLeft
      this.colour = colour
    }

  private fun website(url: String, title: String) = Website(url).apply { this.title = title }

  private val noActions = object : WebHeadContextActions {
    override fun open(website: Website) = Unit
    override fun copy(website: Website) = Unit
    override fun share(website: Website) = Unit
    override fun close(website: Website) = Unit
    override fun copyAll() = Unit
    override fun shareAll(format: ShareAllFormat) = Unit
    override fun dismiss() = Unit
  }

  @Composable
  private fun OnSurface(content: @Composable () -> Unit) {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceDim).padding(16.dp)) { content() }
  }

  private fun captureHead(mode: ThemeMode, state: WebHeadState, size: Float = WebHeadMotion.HEAD_DP) {
    composeRule.setContent {
      ChromerTheme(themeMode = mode) {
        OnSurface { WebHeadBubble(state, size.dp, gestures = null) }
      }
    }
    composeRule.onRoot().captureRoboImage()
  }

  @Test
  fun webHead_rest_light() = captureHead(ThemeMode.Light, head("https://example.com"))

  @Test
  fun webHead_rest_dark() = captureHead(ThemeMode.Dark, head("https://example.com"))

  @Test
  fun webHead_rest_black() = captureHead(ThemeMode.Black, head("https://example.com"))

  @Test
  fun webHead_badge_site_colour() =
    captureHead(ThemeMode.Light, head("https://wikipedia.org", count = 4, badgeOnLeft = false, colour = Color(0xFF00796B)))

  @Test
  fun webHead_small() = captureHead(ThemeMode.Light, head("https://example.com", count = 2), size = WebHeadMotion.SMALL_HEAD_DP)

  @Test
  fun trash_locked_light() {
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        OnSurface { TrashTarget(TrashState().apply { scale = 1f; locked = true }) }
      }
    }
    composeRule.onRoot().captureRoboImage()
  }

  private fun captureSheet(mode: ThemeMode) {
    val sites = listOf(
      website("https://www.android.com", "Android"),
      website("https://en.wikipedia.org/wiki/Web_browser", "Web browser - Wikipedia"),
      website("https://example.com", "Example Domain"),
    )
    composeRule.setContent {
      ChromerTheme(themeMode = mode) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
          WebHeadContextContent(sites, noActions, onShareAll = {})
        }
      }
    }
    composeRule.onRoot().captureRoboImage()
  }

  @Test
  fun contextSheet_light() = captureSheet(ThemeMode.Light)

  @Test
  fun contextSheet_dark() = captureSheet(ThemeMode.Dark)

  @Test
  fun permissionExplainer_light() {
    composeRule.setContent {
      ChromerTheme(themeMode = ThemeMode.Light) {
        PermissionExplainerDialog(
          icon = Icons.Outlined.Layers,
          title = "Allow web heads to float",
          body = "To draw web heads over other apps, Lynket needs the \"Display over other apps\" permission. Android opens the setting next.",
          onGrant = {},
          onDismiss = {},
        )
      }
    }
    composeRule.onNode(isDialog()).captureRoboImage()
  }
}
