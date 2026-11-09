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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
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
  val onOpenBubbleSettings: () -> Unit = {},
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
  val bubbleSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    viewModel.refreshBubbleSetting()
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
      onOpenBubbleSettings = {
        viewModel.dismissDialog()
        try {
          bubbleSettings.launch(viewModel.bubbleSettingsIntent())
        } catch (e: ActivityNotFoundException) {
          Timber.e(e, "No bubble settings screen")
          bubbleSettings.launch(viewModel.appDetailsIntent())
        }
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
    SettingsDialog.AmpOutsideWebHeads -> Info(R.string.amp_warning_title, R.string.amp_warning_content, actions.onDismiss, html = true)
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
    is SettingsDialog.BubbleSettingExplainer -> PermissionExplainerDialog(
      icon = Icons.Outlined.BubbleChart,
      title = stringResource(R.string.bubble_setting_explainer_title),
      body = stringResource(R.string.bubble_setting_explainer_body),
      onGrant = actions.onOpenBubbleSettings,
      onDismiss = actions.onDismiss,
      confirmLabel = stringResource(R.string.open_settings),
    )
    SettingsDialog.NativeBubblesWarning -> AlertDialog(
      onDismissRequest = actions.onDismiss,
      modifier = Modifier.testTag("settings-dialog"),
      title = { Text(stringResource(R.string.browsing_mode_native_bubbles)) },
      text = { NativeBubblesWarningText() },
      confirmButton = {
        TextButton(onClick = actions.onBubblesGuide) { Text(stringResource(R.string.browsing_mode_native_bubbles_guide)) }
      },
      dismissButton = { TextButton(onClick = actions.onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
  }
}

/** The known issues of native bubbles, in the order the warning lists them. */
private val nativeBubblesIssues = listOf(
  R.string.browsing_mode_native_bubbles_issue_web_view,
  R.string.browsing_mode_native_bubbles_issue_limit,
  R.string.browsing_mode_native_bubbles_issue_menu,
  R.string.browsing_mode_native_bubbles_issue_refused,
)

/**
 * The body of the Native Bubbles (Beta) warning: a lead line that names Android's bubble setting,
 * a "Known issues" heading and one row per issue. The body scrolls on its own, so at the largest
 * font the dialog keeps its title and its buttons in view.
 */
@Composable
private fun NativeBubblesWarningText() {
  val colours = MaterialTheme.colorScheme
  val body = MaterialTheme.typography.bodyMedium
  Column(Modifier.verticalScroll(rememberScrollState()).testTag("native-bubbles-warning-text")) {
    Text(stringResource(R.string.browsing_mode_native_bubbles_warning), style = body, color = colours.onSurface)
    Spacer(Modifier.height(16.dp))
    Text(
      stringResource(R.string.browsing_mode_native_bubbles_known_issues),
      style = body,
      fontWeight = FontWeight.SemiBold,
      color = colours.onSurface,
      modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      nativeBubblesIssues.forEach { issue ->
        Row(Modifier.padding(start = 20.dp)) {
          Text("•", style = body, color = colours.onSurfaceVariant, modifier = Modifier.width(16.dp).clearAndSetSemantics { })
          Text(stringResource(issue), style = body, color = colours.onSurfaceVariant)
        }
      }
    }
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
