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
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.browsing.providerselection.ModernProviderSelectionViewModel.ProviderSelectionUiState
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.ui.perapp.PerAppContent
import arun.com.chromer.ui.perapp.PerAppRow
import arun.com.chromer.ui.perapp.PerAppState
import arun.com.chromer.ui.providerselection.ProviderSelectionContent
import arun.com.chromer.ui.settings.AppLabels
import arun.com.chromer.ui.settings.BrowsingModeContent
import arun.com.chromer.ui.settings.BrowsingOptionsContent
import arun.com.chromer.ui.settings.LookAndFeelContent
import arun.com.chromer.ui.settings.SettingsDialog
import arun.com.chromer.ui.settings.SettingsDialogActions
import arun.com.chromer.ui.settings.SettingsDialogContent
import arun.com.chromer.ui.settings.SettingsKeyTable
import arun.com.chromer.ui.settings.SettingsRootContent
import arun.com.chromer.ui.settings.SettingsValues
import arun.com.chromer.ui.settings.components.ColorPickerContent
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the settings root, browsing mode, look and feel, browsing options, per app settings
 * and provider selection: a phone (w411dp) and a fold (w841dp, list in a 680 dp column), light and
 * black. Record with `./gradlew :lynket:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = SETTINGS_PHONE)
class SettingsGoldenTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val context: Application get() = ApplicationProvider.getApplicationContext()

  /** Values of a person who uses web heads with a coloured toolbar and dynamic colour from apps. */
  private val values: SettingsValues
    get() = SettingsValues.from(emptyPreferences(), context).with(
      SettingsKeyTable.WEB_HEAD_ENABLED to true,
      SettingsKeyTable.TOOLBAR_COLOR_PREF to true,
      SettingsKeyTable.DYNAMIC_COLOR to true,
      SettingsKeyTable.DYNAMIC_COLOR_APP to true,
      SettingsKeyTable.MERGE_TABS to true,
    )

  private val labels = AppLabels(
    providerPackage = "com.android.chrome",
    providerName = "Chrome",
    secondaryName = "Firefox",
    favShareName = "Messages",
  )

  private val apps = listOf(
    "Calendar", "Camera", "Chrome", "Clock", "Contacts", "Drive", "Files", "Gmail", "Keep", "Maps",
    "Messages", "Photos", "Play Store", "Settings", "YouTube",
  ).mapIndexed { n, name ->
    PerAppRow("com.example.${name.lowercase().replace(" ", "")}", name, blacklisted = n % 4 == 1, incognito = n % 5 == 2)
  }

  private val icon = Uri.parse("android.resource://com.android.chrome/0")
  private val providers = listOf(
    Provider("com.android.chrome", "Chrome", icon, installed = true),
    Provider("com.brave.browser", "Brave", icon, installed = true),
    Provider("org.mozilla.firefox", "Firefox", icon, installed = false),
    Provider("com.microsoft.emmx", "Edge", icon, installed = false),
  )

  private val noActions = SettingsDialogActions({}, {}, {}, {}, {}, {})

  private fun capture(theme: ThemeMode = ThemeMode.Light, dialog: Boolean = false, content: @Composable () -> Unit) {
    composeRule.setContent {
      ChromerTheme(themeMode = theme) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) { content() }
      }
    }
    composeRule.waitForIdle()
    if (dialog) composeRule.onNode(isDialog()).captureRoboImage() else composeRule.onRoot().captureRoboImage()
  }

  @Composable
  private fun root() = SettingsRootContent(showDefaultBrowserCard = true, version = "2.2.0", onBack = {}, onSetDefault = {}, onOpen = {})

  @Composable
  private fun perApp(state: PerAppState) = PerAppContent(state, enabled = true, onBack = {}, onSearch = {}, onRefresh = {}, onMaster = {}, onBlacklist = { _, _ -> }, onIncognito = { _, _ -> })

  @Composable
  private fun provider(state: ProviderSelectionUiState) =
    ProviderSelectionContent(state, onBack = {}, onUseProvider = {}, onInstall = {}, onUseWebView = {}, onGetChrome = {}, onRetry = {})

  @Test fun settings_default_phone() = capture { root() }

  @Config(qualifiers = SETTINGS_FOLD)
  @Test fun settings_default_fold() = capture { root() }

  @Test fun settings_default_black_phone() = capture(ThemeMode.Black) { root() }

  @Test fun browsing_mode_default_phone() = capture {
    BrowsingModeContent(values, showNativeBubbles = true, onBack = {}, onMode = {}, onSet = { _, _ -> })
  }

  @Test fun browsing_mode_permission_phone() = capture(dialog = true) {
    BrowsingModeContent(values.with(SettingsKeyTable.WEB_HEAD_ENABLED to false), showNativeBubbles = true, onBack = {}, onMode = {}, onSet = { _, _ -> })
    SettingsDialogContent(SettingsDialog.OverlayPermission, noActions)
  }

  @Test fun look_and_feel_default_phone() = capture {
    LookAndFeelContent(values, showWallpaperColours = true, onBack = {}, onSet = { _, _ -> })
  }

  @Test fun look_and_feel_colour_picker_phone() = capture {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
      Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
        ColorPickerContent(title = "Default toolbar color", initial = 0xFF3F51B5.toInt(), onDone = {})
      }
    }
  }

  @Test fun browsing_options_default_phone() = capture {
    BrowsingOptionsContent(values, labels, onBack = {}, onSet = { _, _ -> }, onOpen = {}, onPickApp = {})
  }

  @Test fun per_app_default_phone() = capture { perApp(PerAppState(apps = apps)) }

  @Test fun per_app_loading_phone() = capture { perApp(PerAppState(apps = null, refreshing = true)) }

  @Config(qualifiers = SETTINGS_FOLD)
  @Test fun per_app_default_fold() = capture { perApp(PerAppState(apps = apps)) }

  @Test fun provider_selection_default_phone() = capture {
    provider(ProviderSelectionUiState.Success(providers, selectedPackage = "com.android.chrome", usingWebView = false))
  }

  @Test fun provider_selection_error_phone() = capture {
    provider(ProviderSelectionUiState.NoProvider(emptyList(), usingWebView = false))
  }
}

private const val SETTINGS_PHONE = "w411dp-h891dp-xxhdpi"
private const val SETTINGS_FOLD = "w841dp-h701dp-xxhdpi"
