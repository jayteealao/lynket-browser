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

import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.bubbles.system.BubbleSetting
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
import timber.log.Timber

/** Browsing mode: slide over, web heads, native bubbles (Android 10+), then load pages faster. */
@Composable
fun BrowsingModeScreen(navigator: LynketNavigator, viewModel: SettingsViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val values by viewModel.values.collectAsStateWithLifecycle()
  val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
  val bubbleSetting by viewModel.bubbleSetting.collectAsStateWithLifecycle()
  RefreshBubbleSettingOnResume(viewModel)
  val bubbleSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    viewModel.refreshBubbleSetting()
  }
  BrowsingModeContent(
    values = values,
    showNativeBubbles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    onBack = { navigator.back() },
    onMode = { mode -> viewModel.chooseMode(mode, Utils.isOverlayGranted(context), notificationsAllowed(context)) },
    onSet = viewModel::set,
    bubbleSetting = bubbleSetting,
    onBubbleSetting = { setting ->
      try {
        bubbleSettings.launch(viewModel.bubbleSettingsIntent(setting))
      } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No settings screen for %s", setting)
        bubbleSettings.launch(viewModel.appDetailsIntent())
      }
    },
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
  bubbleSetting: BubbleSetting? = null,
  onBubbleSetting: (BubbleSetting) -> Unit = {},
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
          // Android's bubble setting for Lynket, while native bubbles are chosen.
          if (selected == BrowsingMode.NativeBubbles && bubbleSetting != null && bubbleSetting != BubbleSetting.UNSUPPORTED) {
            row {
              SettingsRow(
                title = stringResource(R.string.bubble_setting_row_title),
                summary = AnnotatedString(stringResource(bubbleSettingSummary(bubbleSetting))),
                control = RowControl.Chevron,
                indent = true,
                modifier = Modifier.testTag("bubble-setting"),
                onClick = { onBubbleSetting(bubbleSetting) },
              )
            }
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

/** The person may change the bubble setting in Android settings and come back by any route. */
@Composable
fun RefreshBubbleSettingOnResume(viewModel: SettingsViewModel) {
  LifecycleResumeEffect(viewModel) {
    viewModel.refreshBubbleSetting()
    onPauseOrDispose { }
  }
}

/** The summary of the bubble setting row: the setting, and what to choose when it is not "all". */
@StringRes
fun bubbleSettingSummary(setting: BubbleSetting): Int = when (setting) {
  BubbleSetting.ALL -> R.string.bubble_setting_all
  BubbleSetting.SELECTED -> R.string.bubble_setting_selected
  BubbleSetting.NONE -> R.string.bubble_setting_none
  BubbleSetting.DEVICE_OFF -> R.string.bubble_setting_device_off
  BubbleSetting.NOTIFICATIONS_OFF, BubbleSetting.UNSUPPORTED -> R.string.bubble_setting_notifications_off
}
