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

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.ui.permission.PermissionExplainerDialog
import arun.com.chromer.util.Utils
import timber.log.Timber

/** Notifications are allowed below Android 13, and on 13 and later once the person granted them. */
fun notificationsAllowed(context: Context): Boolean =
  Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** The actions behind the settings dialogs; the host wires them to the system screens. */
class SettingsDialogActions(
  val onDismiss: () -> Unit,
  val onOpenOverlaySettings: () -> Unit,
  val onRequestNotifications: () -> Unit,
  val onOpenUsageAccess: () -> Unit,
  val onUsageAccessDeclined: () -> Unit,
  val onBubblesGuide: () -> Unit,
)

/** Shows the first queued dialog of [viewModel] with the system screens it can open. */
@Composable
fun SettingsDialogHost(dialog: SettingsDialog?, viewModel: SettingsViewModel) {
  val context = LocalContext.current
  val overlay = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    viewModel.onOverlayResult(Settings.canDrawOverlays(context), notificationsAllowed(context))
  }
  val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  val usageAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    viewModel.syncAppColour(Utils.canReadUsageStats(context))
  }
  SettingsDialogContent(
    dialog = dialog,
    actions = SettingsDialogActions(
      onDismiss = { viewModel.dismissDialog(notificationsAllowed(context)) },
      onOpenOverlaySettings = {
        viewModel.dismissDialog()
        try {
          overlay.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        } catch (e: ActivityNotFoundException) {
          Timber.e(e, "No overlay permission screen")
          Utils.openDrawOverlaySettings(context)
        }
      },
      onRequestNotifications = {
        viewModel.dismissDialog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      },
      onOpenUsageAccess = {
        viewModel.dismissDialog()
        try {
          usageAccess.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: ActivityNotFoundException) {
          Timber.e(e, "No usage access screen")
          viewModel.syncAppColour(Utils.canReadUsageStats(context))
        }
      },
      onUsageAccessDeclined = {
        viewModel.dismissDialog()
        viewModel.syncAppColour(Utils.canReadUsageStats(context))
      },
      onBubblesGuide = {
        viewModel.dismissDialog(notificationsAllowed(context))
        viewModel.openBubblesGuide(context)
      },
    ),
  )
}

/** The dialog for [dialog], or nothing. */
@Composable
fun SettingsDialogContent(dialog: SettingsDialog?, actions: SettingsDialogActions) {
  when (dialog) {
    null -> Unit
    SettingsDialog.DynamicColourHelp -> Info(R.string.dynamic_toolbar_color, R.string.dynamic_toolbar_help, actions.onDismiss)
    SettingsDialog.MergeTabsOff -> Info(R.string.merge_tabs_off_title, R.string.merget_tabs_off_content, actions.onDismiss)
    SettingsDialog.AggressiveLoadingNeedsMergeTabs ->
      Info(R.string.aggresive_dia_title, R.string.aggresive_dia_content, actions.onDismiss)
    SettingsDialog.AmpInSlideOver -> Info(R.string.amp_warning_title, R.string.amp_warning_content, actions.onDismiss, html = true)
    SettingsDialog.AmpAndArticle -> Info(R.string.attention, R.string.amp_article_combined_explanation, actions.onDismiss, html = true)
    SettingsDialog.UsageAccessForAppColour -> AlertDialog(
      onDismissRequest = actions.onUsageAccessDeclined,
      modifier = Modifier.testTag("settings-dialog"),
      title = { Text(stringResource(R.string.permission_required)) },
      text = { Text(stringResource(R.string.usage_permission_explanation_appcolor)) },
      confirmButton = { TextButton(onClick = actions.onOpenUsageAccess) { Text(stringResource(R.string.grant)) } },
      dismissButton = { TextButton(onClick = actions.onUsageAccessDeclined) { Text(stringResource(R.string.not_now)) } },
    )
    SettingsDialog.OverlayPermission -> PermissionExplainerDialog(
      icon = Icons.Outlined.Layers,
      title = stringResource(R.string.overlay_explainer_title),
      body = stringResource(R.string.overlay_explainer_body),
      onGrant = actions.onOpenOverlaySettings,
      onDismiss = actions.onDismiss,
    )
    is SettingsDialog.NotificationPermission -> PermissionExplainerDialog(
      icon = Icons.Outlined.Notifications,
      title = stringResource(
        if (dialog.forBubbles) R.string.bubbles_notification_explainer_title else R.string.web_heads_notification_explainer_title,
      ),
      body = stringResource(
        if (dialog.forBubbles) R.string.bubbles_notification_explainer_body else R.string.web_heads_notification_explainer_body,
      ),
      onGrant = actions.onRequestNotifications,
      onDismiss = actions.onDismiss,
    )
    SettingsDialog.NativeBubblesWarning -> AlertDialog(
      onDismissRequest = actions.onDismiss,
      modifier = Modifier.testTag("settings-dialog"),
      title = { Text(stringResource(R.string.browsing_mode_native_bubbles)) },
      text = { Text(stringResource(R.string.browsing_mode_native_bubbles_warning)) },
      confirmButton = {
        TextButton(onClick = actions.onBubblesGuide) { Text(stringResource(R.string.browsing_mode_native_bubbles_guide)) }
      },
      dismissButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
  }
}

@Composable
private fun Info(@StringRes title: Int, @StringRes body: Int, onDismiss: () -> Unit, html: Boolean = false) {
  val text = stringResource(body)
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("settings-dialog"),
    title = { Text(stringResource(title)) },
    text = { Text(if (html) AnnotatedString.fromHtml(text) else AnnotatedString(text)) },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
  )
}
