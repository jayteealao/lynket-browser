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

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.CancelPresentation
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.navigation.LynketKey
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.settings.SettingsKeyTable.AGGRESSIVE_LOADING
import arun.com.chromer.ui.settings.SettingsKeyTable.BOTTOM_BAR
import arun.com.chromer.ui.settings.SettingsKeyTable.FAV_SHARE
import arun.com.chromer.ui.settings.SettingsKeyTable.MERGE_TABS
import arun.com.chromer.ui.settings.SettingsKeyTable.MINIMIZE_BEHAVIOR
import arun.com.chromer.ui.settings.SettingsKeyTable.PER_APP_LINK
import arun.com.chromer.ui.settings.SettingsKeyTable.PREFERRED_PACKAGE
import arun.com.chromer.ui.settings.SettingsKeyTable.SECONDARY_BROWSER
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_CLOSE_ON_OPEN
import arun.com.chromer.ui.settings.components.AppPickerSheet
import arun.com.chromer.ui.settings.components.ListChoiceDialog
import arun.com.chromer.ui.settings.components.RowControl
import arun.com.chromer.ui.settings.components.RowIcon
import arun.com.chromer.ui.settings.components.SettingsGroup
import arun.com.chromer.ui.settings.components.SettingsRow
import arun.com.chromer.ui.settings.components.SettingsScaffold
import arun.com.chromer.ui.settings.components.settingsColumn
import kotlinx.coroutines.launch

/**
 * Browsing options: app preferences, behaviour, web head options and the bottom bar with the
 * explanation of its five actions, in the legacy order.
 */
@Composable
fun BrowsingOptionsScreen(navigator: LynketNavigator, viewModel: SettingsViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val values by viewModel.values.collectAsStateWithLifecycle()
  val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
  val labels by viewModel.appLabels.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var picker by rememberSaveable { mutableStateOf<String?>(null) }
  LifecycleResumeEffect(Unit) {
    viewModel.refreshAppLabels()
    onPauseOrDispose { }
  }
  BrowsingOptionsContent(
    values = values,
    labels = labels,
    snackbarHostState = snackbar,
    onBack = { navigator.back() },
    onSet = viewModel::set,
    onOpen = { navigator.navigate(it) },
    onPickApp = { picker = it },
  )
  picker?.let { key ->
    val secondary = key == SECONDARY_BROWSER
    // One intent per picker: the sheet looks up the apps again whenever it gets a new intent.
    val pickerIntent = remember(secondary) {
      if (secondary) Constants.WEB_INTENT else Intent(Intent.ACTION_SEND).setType("text/plain")
    }
    AppPickerSheet(
      title = stringResource(if (secondary) R.string.choose_secondary_browser else R.string.choose_fav_share_app),
      intent = pickerIntent,
      onPick = { app ->
        picker = null
        viewModel.setApp(key, app.component)
        val message = context.getString(if (secondary) R.string.secondary_browser_success else R.string.fav_share_success, app.label)
        scope.launch { snackbar.showSnackbar(message) }
      },
      onDismiss = { picker = null },
    )
  }
  SettingsDialogHost(dialogs.firstOrNull(), viewModel)
}

/** The five bottom bar actions in the legacy order: new tab, share, tabs, minimize, article. */
private val BottomBarActions = listOf(
  Icons.Outlined.Add to R.string.new_tab_action_explanation,
  Icons.Outlined.Share to R.string.share_action_explanation,
  Icons.Outlined.ViewAgenda to R.string.bottom_bar_tabs_explanation,
  Icons.Outlined.ArrowDownward to R.string.minimize_action_explanation,
  Icons.AutoMirrored.Outlined.Article to R.string.bottom_bar_article_mode_explanation,
)

@Composable
fun BrowsingOptionsContent(
  values: SettingsValues,
  labels: AppLabels,
  onBack: () -> Unit,
  onSet: (String, Any?) -> Unit,
  onOpen: (LynketKey) -> Unit,
  onPickApp: (String) -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  openList: Boolean = false,
) {
  var minimizeDialog by rememberSaveable { mutableStateOf(openList) }
  val minimizeChoices = listChoices(MINIMIZE_BEHAVIOR, R.array.minimizePreferenceValues, R.array.minimizePreferenceKeys)
  val webHeadsNotice = if (SettingsRules.showsSlideOverNotice(values)) stringResource(R.string.not_available_in_slide_mode) else null
  SettingsScaffold(
    title = stringResource(R.string.settings_browsing_options),
    onBack = onBack,
    snackbarHostState = snackbarHostState,
    modifier = Modifier.testTag("browsing-options"),
  ) {
    item(key = "apps") {
      SettingsGroup(label = stringResource(R.string.app_preferences), modifier = Modifier.settingsColumn()) {
        row {
          AppRow(
            key = PREFERRED_PACKAGE,
            category = stringResource(R.string.default_provider),
            name = labels.providerName ?: stringResource(R.string.not_found),
            packageName = labels.providerPackage,
            fallback = Icons.Outlined.Web,
            missing = labels.providerName == null,
            onClick = { onOpen(LynketKey.ProviderSelection) },
          )
        }
        row {
          AppRow(
            key = SECONDARY_BROWSER,
            category = stringResource(R.string.choose_secondary_browser),
            name = labels.secondaryName ?: stringResource(R.string.not_set),
            packageName = labels.secondaryPackage,
            fallback = Icons.AutoMirrored.Outlined.OpenInNew,
            onClick = { onPickApp(SECONDARY_BROWSER) },
          )
        }
        row {
          AppRow(
            key = FAV_SHARE,
            category = stringResource(R.string.fav_share_app),
            name = labels.favShareName ?: stringResource(R.string.not_set),
            packageName = labels.favSharePackage,
            fallback = Icons.Outlined.Share,
            onClick = { onPickApp(FAV_SHARE) },
          )
        }
      }
    }
    item(key = "behaviour") {
      SettingsGroup(label = stringResource(R.string.behavior_preferences), modifier = Modifier.settingsColumn()) {
        row { SwitchRow(MERGE_TABS, R.string.merge_tabs, R.string.merge_tabs_explanation, values, onSet, Icons.Outlined.Animation) }
        row {
          SettingsRow(
            title = stringResource(R.string.per_app_settings),
            summary = AnnotatedString(stringResource(R.string.per_app_settings_explanation)),
            icon = RowIcon(Icons.Outlined.FilterList),
            control = RowControl.Chevron,
            modifier = Modifier.testTag("row-$PER_APP_LINK"),
            onClick = { onOpen(LynketKey.PerApp) },
          )
        }
      }
    }
    item(key = "web-heads") {
      SettingsGroup(label = stringResource(R.string.web_heads), notice = webHeadsNotice, modifier = Modifier.settingsColumn()) {
        row {
          CheckboxRow(
            AGGRESSIVE_LOADING,
            R.string.aggressive_background_loading,
            R.string.aggressive_background_loading_desc,
            values,
            onSet,
            Icons.Outlined.FastForward,
          )
        }
        row {
          CheckboxRow(
            WEB_HEAD_CLOSE_ON_OPEN,
            R.string.web_close_on_open_title,
            R.string.web_close_on_open_desc,
            values,
            onSet,
            Icons.Outlined.CancelPresentation,
          )
        }
      }
    }
    item(key = "bottom-bar") {
      SettingsGroup(label = stringResource(R.string.bottom_bar), modifier = Modifier.settingsColumn()) {
        row { SwitchRow(BOTTOM_BAR, R.string.bottom_bar_pref_title, R.string.bottom_bar_pref_summary, values, onSet, Icons.Outlined.MoreHoriz) }
        row {
          ListRow(MINIMIZE_BEHAVIOR, R.string.minimize_behavior, minimizeChoices, values, Icons.Outlined.ArrowDownward) {
            minimizeDialog = true
          }
        }
        BottomBarActions.forEach { (icon, text) -> row { ActionExplanation(icon, text) } }
      }
    }
  }
  if (minimizeDialog) {
    ListChoiceDialog(
      title = stringResource(R.string.minimize_behavior),
      choices = minimizeChoices,
      selected = values.values[MINIMIZE_BEHAVIOR] ?: Unit,
      onSelect = { onSet(MINIMIZE_BEHAVIOR, it); minimizeDialog = false },
      onDismiss = { minimizeDialog = false },
    )
  }
}

/** An app preference row: the category above the app's name, with the app's icon. */
@Composable
private fun AppRow(
  key: String,
  category: String,
  name: String,
  packageName: String?,
  fallback: ImageVector,
  onClick: () -> Unit,
  missing: Boolean = false,
) {
  val context = LocalContext.current
  var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(packageName) {
    icon = packageName?.let {
      runCatching { context.packageManager.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
  }
  androidx.compose.material3.ListItem(
    modifier = Modifier
      .testTag("row-$key")
      .clickable(role = Role.Button, onClick = onClick),
    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    overlineContent = { Text(category) },
    headlineContent = {
      Text(name, color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    },
    leadingContent = {
      val bitmap = icon
      if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
      } else {
        Icon(fallback, contentDescription = null, tint = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
  )
}

@Composable
private fun ActionExplanation(icon: ImageVector, text: Int) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
    Text(AnnotatedString.fromHtml(stringResource(text)), style = MaterialTheme.typography.bodyMedium)
  }
}
