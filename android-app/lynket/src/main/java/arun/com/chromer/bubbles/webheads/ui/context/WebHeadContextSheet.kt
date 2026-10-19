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

package arun.com.chromer.bubbles.webheads.ui.context

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.ui.components.SiteAvatar
import arun.com.chromer.util.Utils

/** How "Share all" sends the list, in the order the choice dialog shows them. */
enum class ShareAllFormat { CommaSeparated, NewLineSeparated, List }

/** What the sheet asks its activity to do. */
interface WebHeadContextActions {
  fun open(website: Website)
  fun copy(website: Website)
  fun share(website: Website)
  fun close(website: Website)
  fun copyAll()
  fun shareAll(format: ShareAllFormat)
  fun dismiss()
}

/**
 * The list of open web heads, in a bottom sheet: the "Websites" title, a row per site (favicon,
 * title, address, share, close; tap opens, long press copies), then "Copy all" and "Share all".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebHeadContextSheet(websites: List<Website>, actions: WebHeadContextActions) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var choosingShareFormat by rememberSaveable { mutableStateOf(false) }
  ModalBottomSheet(onDismissRequest = actions::dismiss, sheetState = sheetState) {
    WebHeadContextContent(websites, actions, onShareAll = { choosingShareFormat = true })
  }
  if (choosingShareFormat) {
    ShareAllDialog(
      onChoose = {
        choosingShareFormat = false
        actions.shareAll(it)
      },
      onDismiss = { choosingShareFormat = false },
    )
  }
}

/** The sheet's content, without the sheet, so screenshot tests can draw it on its own. */
@Composable
fun WebHeadContextContent(websites: List<Website>, actions: WebHeadContextActions, onShareAll: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
    Text(
      text = stringResource(R.string.web_sites),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
    )
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
      items(websites, key = { it.url }) { website ->
        WebsiteRow(website, actions)
      }
    }
    HorizontalDivider(
      modifier = Modifier.padding(top = 8.dp),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.End,
    ) {
      TextButton(onClick = actions::copyAll) { Text(stringResource(R.string.copy_all)) }
      Spacer(Modifier.width(8.dp))
      TextButton(onClick = onShareAll) { Text(stringResource(R.string.share_all)) }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebsiteRow(website: Website, actions: WebHeadContextActions) {
  val label = website.safeLabel()
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 64.dp)
      .combinedClickable(
        role = Role.Button,
        onLongClickLabel = stringResource(R.string.copy_link),
        onLongClick = { actions.copy(website) },
        onClick = { actions.open(website) },
      )
      .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FaviconTile(website)
    Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = website.preferredUrl(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    IconButton(onClick = { actions.share(website) }) {
      Icon(
        Icons.Filled.Share,
        contentDescription = stringResource(R.string.share_website, label),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
    IconButton(onClick = { actions.close(website) }) {
      Icon(
        Icons.Filled.Close,
        contentDescription = stringResource(R.string.close_website, label),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun FaviconTile(website: Website) {
  SiteAvatar(Utils.getFirstLetter(website.preferredUrl()), website.faviconUrl)
}

/** Picks how the links are shared; a tap on a format shares at once, so the rows are buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareAllDialog(onChoose: (ShareAllFormat) -> Unit, onDismiss: () -> Unit) {
  val labels = listOf(
    stringResource(R.string.comma_separated),
    stringResource(R.string.new_line_separated),
    stringResource(R.string.share_all_list),
  )
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(Modifier.padding(bottom = 16.dp)) {
      Text(
        stringResource(R.string.choose_share_method),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
      )
      labels.forEachIndexed { index, label ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button) { onChoose(ShareAllFormat.entries[index]) }
            .padding(horizontal = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
        }
      }
    }
  }
}
