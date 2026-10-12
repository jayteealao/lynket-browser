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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.StringResource
import arun.com.chromer.home.CustomTabProviderInfo
import arun.com.chromer.ui.about.AboutContent
import arun.com.chromer.ui.changelog.ChangelogDialog
import arun.com.chromer.ui.home.HomeActions
import arun.com.chromer.ui.home.HomeContentState
import arun.com.chromer.ui.home.HomeScreenContent
import arun.com.chromer.ui.intro.IntroContent
import arun.com.chromer.ui.intro.IntroPages
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.ui.tips.TipsContent
import arun.com.chromer.util.glide.appicon.ApplicationIcon
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the home, drawer, first run, changelog, About and Tips, in the light, dark and black
 * themes on a phone (w411dp) and a fold (w841dp). Record with `./gradlew :lynket:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = PHONE)
class ShellGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val provider = CustomTabProviderInfo(
    iconUri = ApplicationIcon.createUri("com.android.chrome"),
    providerDescription = StringResource(R.string.tab_provider_status_message_home, listOf("Chrome")),
    providerReason = StringResource(0),
  )

  private val recents = listOf("github", "wikipedia", "reddit", "medium", "youtube", "news", "theverge", "android")
    .map { Website("https://$it.example/").apply { title = it.replaceFirstChar(Char::uppercase) } }

  private val default = HomeContentState(recents = recents, providerInfo = provider)

  private fun capture(theme: ThemeMode = ThemeMode.Light, content: @Composable () -> Unit) {
    composeRule.setContent {
      ChromerTheme(themeMode = theme) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) { content() }
      }
    }
    composeRule.waitForIdle()
    composeRule.onRoot().captureRoboImage()
  }

  @Test fun home_light_phone() = capture { HomeScreenContent(default, HomeActions()) }
  @Test fun home_dark_phone() = capture(ThemeMode.Dark) { HomeScreenContent(default, HomeActions()) }
  @Test fun home_black_phone() = capture(ThemeMode.Black) { HomeScreenContent(default, HomeActions()) }

  @Config(qualifiers = FOLD)
  @Test fun home_light_fold() = capture { HomeScreenContent(default, HomeActions()) }

  @Config(qualifiers = FOLD)
  @Test fun home_dark_fold() = capture(ThemeMode.Dark) { HomeScreenContent(default, HomeActions()) }

  @Config(qualifiers = FOLD)
  @Test fun home_black_fold() = capture(ThemeMode.Black) { HomeScreenContent(default, HomeActions()) }

  @Test fun home_empty_phone() = capture { HomeScreenContent(default.copy(recents = emptyList()), HomeActions()) }

  @Test fun home_offline_phone() = capture { HomeScreenContent(default.copy(isOnline = false), HomeActions()) }

  @Test fun home_providerError_phone() = capture {
    HomeScreenContent(default.copy(providerInfo = provider.copy(noProviderInstalled = true)), HomeActions())
  }

  @Test fun home_drawerOpen_phone() = capture {
    HomeScreenContent(default, HomeActions(), drawerState = rememberDrawerState(DrawerValue.Open))
  }

  @Test fun intro_first_phone() = capture {
    IntroContent(onClose = {}, onAction = {}, onSetDefaultBrowser = {})
  }

  @Test fun intro_permission_phone() = capture {
    IntroContent(
      onClose = {},
      onAction = {},
      onSetDefaultBrowser = {},
      pagerState = rememberPagerState(initialPage = IntroPages.lastIndex) { IntroPages.size },
    )
  }

  @Test fun changelog_phone() = capture {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ChangelogDialog(onOk = {}, onRate = {}) }
  }

  @Test fun about_light_phone() = capture { AboutContent(onBack = {}, onRow = {}) }
  @Test fun about_dark_phone() = capture(ThemeMode.Dark) { AboutContent(onBack = {}, onRow = {}) }
  @Test fun tips_light_phone() = capture { TipsContent(onClose = {}) }

  @Config(qualifiers = FOLD)
  @Test fun tips_light_fold() = capture { TipsContent(onClose = {}) }
}

private const val PHONE = "w411dp-h891dp-xxhdpi"
private const val FOLD = "w841dp-h701dp-xxhdpi"
