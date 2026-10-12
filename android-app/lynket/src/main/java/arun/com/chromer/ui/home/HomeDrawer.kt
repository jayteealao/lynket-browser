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

package arun.com.chromer.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import arun.com.chromer.R
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.Utils
import timber.log.Timber

/** Drawer destinations, in the order of the confirmed drawer board (legacy menu order below "More"). */
enum class DrawerItem(@StringRes val label: Int, val icon: ImageVector, val secondary: Boolean) {
  Home(R.string.home, Icons.Outlined.Home, false),
  History(R.string.title_history, Icons.Outlined.History, false),
  Tabs(R.string.title_tabs, Icons.Outlined.Tab, false),
  Settings(R.string.settings, Icons.Outlined.Settings, false),
  Tips(R.string.tips, Icons.Outlined.Lightbulb, false),
  Intro(R.string.intro, Icons.Outlined.Assignment, true),
  Feedback(R.string.feedback, Icons.AutoMirrored.Outlined.Chat, true),
  Rate(R.string.rate_play_store, Icons.Outlined.RateReview, true),
  Beta(R.string.join_beta, Icons.Outlined.Science, true),
  Share(R.string.share, Icons.Outlined.Share, true),
  About(R.string.about, Icons.Outlined.Info, true),
}

/** The modal drawer's sheet: the app name, the main areas, then "More" and the legacy menu items. */
@Composable
fun HomeDrawerSheet(onItem: (DrawerItem) -> Unit, modifier: Modifier = Modifier) {
  ModalDrawerSheet(modifier = modifier.testTag("home-drawer")) {
    Column(
      Modifier
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp),
    ) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp),
      )
      DrawerItem.entries.filterNot { it.secondary }.forEach { DrawerRow(it, selected = it == DrawerItem.Home, onItem) }
      HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
      Text(
        text = stringResource(R.string.drawer_more),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
      )
      DrawerItem.entries.filter { it.secondary }.forEach { DrawerRow(it, selected = false, onItem) }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun DrawerRow(item: DrawerItem, selected: Boolean, onItem: (DrawerItem) -> Unit) {
  val label = stringResource(item.label)
  NavigationDrawerItem(
    label = { Text(label) },
    icon = { Icon(item.icon, contentDescription = null) },
    selected = selected,
    onClick = { onItem(item) },
    modifier = Modifier.testTag("drawer-item-${item.name}"),
  )
}

/** The outside actions of the drawer, as the old home menu ran them. */
object HomeDrawerIntents {
  fun feedback(context: Context) {
    val email = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", Constants.MAILID, null))
      .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
    start(context, Intent.createChooser(email, context.getString(R.string.send_email)))
  }

  fun rate(context: Context) = Utils.openPlayStore(context, context.packageName)

  fun beta(context: Context) =
    start(context, Intent(Intent.ACTION_VIEW, "https://play.google.com/apps/testing/arun.com.chromer".toUri()))

  fun share(context: Context) {
    val share = Intent(Intent.ACTION_SEND)
      .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
      .setType("text/plain")
    start(context, Intent.createChooser(share, context.getString(R.string.share_via)))
  }

  fun start(context: Context, intent: Intent) {
    try {
      context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      Timber.e(e, "No app for %s", intent)
    }
  }
}
