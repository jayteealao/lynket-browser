/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.ui.settings

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.settings.SettingsKeyTable.AMP_MODE
import arun.com.chromer.ui.settings.SettingsKeyTable.ARTICLE_MODE
import arun.com.chromer.ui.settings.components.RowControl
import arun.com.chromer.ui.settings.components.RowIcon
import arun.com.chromer.ui.settings.components.SettingsGroup
import arun.com.chromer.ui.settings.components.SettingsRow
import arun.com.chromer.ui.settings.components.SettingsScaffold
import arun.com.chromer.ui.settings.components.settingsColumn
import arun.com.chromer.util.Utils

/** Browsing mode: slide over, web heads, native bubbles (Android 10+), then load pages faster. */
@Composable
fun BrowsingModeScreen(navigator: LynketNavigator, viewModel: SettingsViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val values by viewModel.values.collectAsStateWithLifecycle()
  val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
  BrowsingModeContent(
    values = values,
    showNativeBubbles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    onBack = { navigator.back() },
    onMode = { mode -> viewModel.chooseMode(mode, Utils.isOverlayGranted(context), notificationsAllowed(context)) },
    onSet = viewModel::set,
  )
  SettingsDialogHost(dialogs.firstOrNull(), viewModel)
}

@Composable
fun BrowsingModeContent(
  values: SettingsValues,
  showNativeBubbles: Boolean,
  onBack: () -> Unit,
  onMode: (BrowsingMode) -> Unit,
  onSet: (String, Any?) -> Unit,
) {
  val selected = SettingsRules.selectedMode(values)
  SettingsScaffold(
    title = stringResource(R.string.settings_browsing_mode),
    onBack = onBack,
    modifier = Modifier.testTag("browsing-mode"),
  ) {
    item(key = "modes") {
      SettingsGroup(label = null, modifier = Modifier.settingsColumn()) {
        row {
          SettingsRow(
            title = stringResource(R.string.browsing_mode_slide_over),
            summary = AnnotatedString(stringResource(R.string.browsing_mode_slide_over_explanation)),
            icon = RowIcon(Icons.AutoMirrored.Outlined.OpenInNew),
            control = RowControl.Radio(selected == BrowsingMode.SlideOver),
            modifier = Modifier.testTag("mode-slide-over"),
            onClick = { onMode(BrowsingMode.SlideOver) },
          )
        }
        row {
          SettingsRow(
            title = stringResource(R.string.browsing_mode_web_heads),
            summary = AnnotatedString(stringResource(R.string.browsing_mode_web_heads_explanation)),
            icon = RowIcon(Icons.Outlined.ChatBubble),
            control = RowControl.Radio(selected == BrowsingMode.WebHeads),
            modifier = Modifier.testTag("mode-web-heads"),
            onClick = { onMode(BrowsingMode.WebHeads) },
          )
        }
        if (showNativeBubbles) {
          row {
            SettingsRow(
              title = stringResource(R.string.browsing_mode_native_bubbles),
              summary = AnnotatedString(stringResource(R.string.browsing_mode_native_bubbles_explanation)),
              icon = RowIcon(
                Icons.Outlined.BubbleChart,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
              ),
              control = RowControl.Radio(selected == BrowsingMode.NativeBubbles),
              modifier = Modifier.testTag("mode-native-bubbles"),
              onClick = { onMode(BrowsingMode.NativeBubbles) },
            )
          }
        }
      }
    }
    item(key = "load-faster") {
      SettingsGroup(label = stringResource(R.string.load_faster_heading), modifier = Modifier.settingsColumn()) {
        row {
          SwitchRow(AMP_MODE, R.string.amp, R.string.amp_summary, values, onSet, Icons.Outlined.Bolt)
        }
        row {
          SwitchRow(ARTICLE_MODE, R.string.article_mode, R.string.article_mode_summary, values, onSet, Icons.AutoMirrored.Outlined.Article)
        }
      }
    }
  }
}
