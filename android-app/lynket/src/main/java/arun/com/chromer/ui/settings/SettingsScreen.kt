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
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import arun.com.chromer.BuildConfig
import arun.com.chromer.R
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.navigation.LynketKey
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.settings.components.RowControl
import arun.com.chromer.ui.settings.components.RowIcon
import arun.com.chromer.ui.settings.components.SettingsGroup
import arun.com.chromer.ui.settings.components.SettingsRow
import arun.com.chromer.ui.settings.components.SettingsScaffold
import arun.com.chromer.ui.settings.components.settingsColumn
import arun.com.chromer.util.Utils
import timber.log.Timber

/** The settings root: the default browser card, the three groups and About, in the legacy order. */
@Composable
fun SettingsScreen(navigator: LynketNavigator) {
  val context = LocalContext.current
  var isDefault by remember { mutableStateOf(true) }
  LifecycleResumeEffect(Unit) {
    isDefault = Utils.isDefaultBrowser(context)
    onPauseOrDispose { }
  }
  SettingsRootContent(
    showDefaultBrowserCard = !isDefault,
    version = BuildConfig.VERSION_NAME,
    onBack = { navigator.back() },
    onSetDefault = { openDefaultBrowserSettings(context) },
    onOpen = { navigator.navigate(it) },
  )
}

/** The default browser card's click, as the old settings screen did it. */
private fun openDefaultBrowserSettings(context: Context) {
  val defaultBrowser = Utils.getDefaultBrowserPackage(context)
  try {
    if (defaultBrowser.equals("android", ignoreCase = true) ||
      defaultBrowser.startsWith("org.cyanogenmod") ||
      defaultBrowser.equals("com.huawei.android.internal.app", ignoreCase = true)
    ) {
      // No default yet: opening a link shows the chooser, where Lynket can be made the default.
      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.GOOGLE_URL)))
    } else {
      Toast.makeText(
        context,
        "${Utils.getAppNameWithPackage(context, defaultBrowser)} ${context.getString(R.string.default_clear_msg)}",
        Toast.LENGTH_LONG,
      ).show()
      context.startActivity(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$defaultBrowser")),
      )
    }
  } catch (e: ActivityNotFoundException) {
    Timber.e(e, "Cannot open the default browser settings")
  }
}

@Composable
fun SettingsRootContent(
  showDefaultBrowserCard: Boolean,
  version: String,
  onBack: () -> Unit,
  onSetDefault: () -> Unit,
  onOpen: (LynketKey) -> Unit,
) {
  SettingsScaffold(title = stringResource(R.string.settings), onBack = onBack, modifier = Modifier.testTag("settings")) {
    if (showDefaultBrowserCard) {
      item(key = "default-browser") { DefaultBrowserCard(onSetDefault) }
    }
    item(key = "groups") {
      SettingsGroup(label = null, modifier = Modifier.settingsColumn()) {
        row {
          SettingsRow(
            title = stringResource(R.string.settings_browsing_mode),
            icon = RowIcon(Icons.Outlined.Language),
            modifier = Modifier.testTag("row-browsing-mode"),
            onClick = { onOpen(LynketKey.BrowsingMode) },
          )
        }
        row {
          SettingsRow(
            title = stringResource(R.string.settings_look_and_feel),
            icon = RowIcon(Icons.Outlined.Palette),
            modifier = Modifier.testTag("row-look-and-feel"),
            onClick = { onOpen(LynketKey.LookAndFeel) },
          )
        }
        row {
          SettingsRow(
            title = stringResource(R.string.settings_browsing_options),
            icon = RowIcon(Icons.Outlined.Settings),
            modifier = Modifier.testTag("row-browsing-options"),
            onClick = { onOpen(LynketKey.BrowsingOptions) },
          )
        }
      }
    }
    item(key = "about") {
      SettingsGroup(label = null, modifier = Modifier.settingsColumn()) {
        row {
          SettingsRow(
            title = stringResource(R.string.about),
            summary = AnnotatedString(stringResource(R.string.settings_about_version, version)),
            icon = RowIcon(
              Icons.Outlined.Info,
              container = MaterialTheme.colorScheme.tertiaryContainer,
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier.testTag("row-about"),
            onClick = { onOpen(LynketKey.About) },
          )
        }
      }
    }
  }
}

@Composable
private fun DefaultBrowserCard(onSetDefault: () -> Unit) {
  Card(
    onClick = onSetDefault,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    modifier = Modifier.settingsColumn().testTag("default-browser-card"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      androidx.compose.material3.Icon(
        Icons.Outlined.AutoFixHigh,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Column(Modifier.weight(1f)) {
        Text(
          stringResource(R.string.set_default_chromer),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          stringResource(R.string.default_pref_xpln),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      Button(onClick = onSetDefault) { Text(stringResource(R.string.settings_set)) }
    }
  }
}

/** A switch row for [key], enabled as the rules say; a tap sets the opposite value. */
@Composable
internal fun SwitchRow(
  key: String,
  @androidx.annotation.StringRes title: Int,
  @androidx.annotation.StringRes summary: Int?,
  values: SettingsValues,
  onSet: (String, Any?) -> Unit,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
  val checked = values.bool(key)
  SettingsRow(
    title = stringResource(title),
    summary = summary?.let { AnnotatedString(stringResource(it)) },
    icon = icon?.let { RowIcon(it) },
    control = RowControl.Switch(checked),
    enabled = SettingsRules.isEnabled(key, values),
    modifier = Modifier.testTag("row-$key"),
    onClick = { onSet(key, !checked) },
  )
}

/** A checkbox row for [key]; [indent] for the dynamic colour boxes under their switch. */
@Composable
internal fun CheckboxRow(
  key: String,
  @androidx.annotation.StringRes title: Int,
  @androidx.annotation.StringRes summary: Int?,
  values: SettingsValues,
  onSet: (String, Any?) -> Unit,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  indent: Boolean = false,
) {
  val checked = values.bool(key)
  SettingsRow(
    title = stringResource(title),
    summary = summary?.let { AnnotatedString(stringResource(it)) },
    icon = icon?.let { RowIcon(it) },
    control = RowControl.Checkbox(checked),
    enabled = SettingsRules.isEnabled(key, values),
    indent = indent,
    modifier = Modifier.testTag("row-$key"),
    onClick = { onSet(key, !checked) },
  )
}

/** The choices of a list setting from its label and key arrays, typed as the store keeps the key. */
@Composable
internal fun listChoices(
  key: String,
  @androidx.annotation.ArrayRes labels: Int,
  @androidx.annotation.ArrayRes keys: Int,
): List<arun.com.chromer.ui.settings.components.ListChoice<Any>> {
  val names = androidx.compose.ui.res.stringArrayResource(labels)
  val stored = androidx.compose.ui.res.stringArrayResource(keys)
  val asInt = arun.com.chromer.data.preferences.SettingsSchema[key]?.type == arun.com.chromer.data.preferences.SettingType.INT
  return names.zip(stored).map { (label, value) ->
    arun.com.chromer.ui.settings.components.ListChoice<Any>(if (asInt) value.toInt() else value, label)
  }
}

/** A list row: the title and the label of the stored choice; a tap opens the choice dialog. */
@Composable
internal fun ListRow(
  key: String,
  @androidx.annotation.StringRes title: Int,
  choices: List<arun.com.chromer.ui.settings.components.ListChoice<Any>>,
  values: SettingsValues,
  icon: androidx.compose.ui.graphics.vector.ImageVector?,
  onClick: () -> Unit,
) {
  val current = values.values[key]
  SettingsRow(
    title = stringResource(title),
    summary = choices.firstOrNull { it.value == current }?.label?.let(::AnnotatedString),
    icon = icon?.let { RowIcon(it) },
    enabled = SettingsRules.isEnabled(key, values),
    modifier = Modifier.testTag("row-$key"),
    onClick = onClick,
  )
}
