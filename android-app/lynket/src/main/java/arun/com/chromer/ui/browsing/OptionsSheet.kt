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

package arun.com.chromer.ui.browsing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import arun.com.chromer.R

/** The Lynket options items, in the order the old options dialog listed them. */
enum class OptionItem(val label: Int, val needsUrl: Boolean, val tag: String) {
  Settings(R.string.settings, needsUrl = false, tag = "option-settings"),
  Tabs(R.string.title_tabs, needsUrl = false, tag = "option-tabs"),
  NewTab(R.string.new_tab_text, needsUrl = false, tag = "option-new-tab"),
  History(R.string.title_history, needsUrl = false, tag = "option-history"),
  AddToHomeScreen(R.string.add_to_homescreen, needsUrl = true, tag = "option-add-to-home-screen"),
  OpenWith(R.string.open_with, needsUrl = true, tag = "option-open-with"),
  ArticleView(R.string.open_article_view, needsUrl = true, tag = "option-article"),
}

/** The items to show: all of them, without the article item when the page is already an article. */
fun optionItems(fromArticle: Boolean): List<OptionItem> =
  OptionItem.entries.filterNot { fromArticle && it == OptionItem.ArticleView }

/**
 * The Lynket options over the Custom Tab (board options-sheet--default): a sheet with a drag handle,
 * the "Lynket" header and one row per item. Items that need the page address are disabled when no
 * address is known, instead of failing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(
  items: List<OptionItem>,
  urlKnown: Boolean,
  onPick: (OptionItem) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier = Modifier.testTag("options-sheet"),
  ) {
    OptionsSheetContent(items, urlKnown, onPick)
  }
}

/** The sheet's content without the sheet, for screenshot tests. */
@Composable
fun OptionsSheetContent(items: List<OptionItem>, urlKnown: Boolean, onPick: (OptionItem) -> Unit) {
  Column(
    Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .navigationBarsPadding()
      .padding(bottom = 16.dp),
  ) {
    Text(
      text = stringResource(R.string.app_name),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    items.forEach { item ->
      val enabled = urlKnown || !item.needsUrl
      ListItem(
        headlineContent = { Text(stringResource(item.label), style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { OptionIcon(item) },
        colors = ListItemDefaults.colors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          leadingIconColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 56.dp)
          .alpha(if (enabled) 1f else 0.38f)
          .clickable(enabled = enabled, role = Role.Button) { onPick(item) }
          .padding(horizontal = 8.dp)
          .testTag(item.tag),
      )
    }
  }
}

@Composable
private fun OptionIcon(item: OptionItem) {
  val vector: ImageVector = when (item) {
    OptionItem.Settings -> Icons.Outlined.Settings
    OptionItem.Tabs -> Icons.Outlined.Tab
    OptionItem.NewTab -> Icons.Outlined.Add
    OptionItem.History -> Icons.Outlined.History
    OptionItem.AddToHomeScreen -> Icons.Outlined.AddToHomeScreen
    OptionItem.OpenWith -> Icons.AutoMirrored.Outlined.OpenInNew
    OptionItem.ArticleView -> Icons.AutoMirrored.Outlined.Article
  }
  Icon(vector, contentDescription = null)
}
