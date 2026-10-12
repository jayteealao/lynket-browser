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

package arun.com.chromer.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.tabs.ARTICLE
import arun.com.chromer.tabs.CUSTOM_TAB
import arun.com.chromer.tabs.TabsManager.Tab
import arun.com.chromer.tabs.WEB_VIEW
import arun.com.chromer.tabs.WEB_VIEW_EMBEDDED
import arun.com.chromer.tabs.ui.ModernTabsViewModel
import arun.com.chromer.tabs.ui.TabsUiState
import arun.com.chromer.ui.components.EmptyState
import arun.com.chromer.ui.components.ErrorState
import arun.com.chromer.ui.components.LoadingList
import arun.com.chromer.ui.history.SiteAvatar
import arun.com.chromer.ui.settings.components.settingsColumn

/** What a person can do on the Tabs screen. */
class TabsActions(
  val onBack: () -> Unit = {},
  val onOpen: (Tab) -> Unit = {},
  val onClose: (Tab) -> Unit = {},
  val onRefresh: () -> Unit = {},
  val onCloseAll: () -> Unit = {},
  val onConfirmCloseAll: () -> Unit = {},
  val onDismissCloseAll: () -> Unit = {},
)

/**
 * Tabs, shown by the main navigation and by TabsActivity (home, the Custom Tab menu and the
 * launcher shortcut): the open browsing tasks, then the floating close-all button, as on the old
 * screen. The list reloads whenever the screen comes back.
 */
@Composable
fun TabsScreen(onBack: () -> Unit, closeIcon: Boolean, viewModel: ModernTabsViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LifecycleResumeEffect(viewModel) {
    viewModel.load()
    onPauseOrDispose { }
  }
  TabsContent(
    state = state,
    closeIcon = closeIcon,
    actions = TabsActions(
      onBack = onBack,
      onOpen = { viewModel.open(context, it) },
      onClose = { viewModel.close(context, it) },
      onRefresh = viewModel::load,
      onCloseAll = viewModel::requestCloseAll,
      onConfirmCloseAll = viewModel::confirmCloseAll,
      onDismissCloseAll = viewModel::dismissCloseAll,
    ),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsContent(state: TabsUiState, closeIcon: Boolean, actions: TabsActions) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
  val tabs = (state as? TabsUiState.Loaded)?.tabs.orEmpty()
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).testTag("tabs"),
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      LargeTopAppBar(
        title = { Text(stringResource(R.string.title_tabs), fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = actions.onBack) {
            if (closeIcon) {
              Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
            } else {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
    floatingActionButton = {
      if (tabs.isNotEmpty()) {
        FloatingActionButton(onClick = actions.onCloseAll, modifier = Modifier.testTag("tabs-close-all")) {
          Icon(Icons.Filled.ClearAll, contentDescription = stringResource(R.string.tabs_close_all))
        }
      }
    },
  ) { padding ->
    PullToRefreshBox(
      isRefreshing = (state as? TabsUiState.Loaded)?.refreshing == true,
      onRefresh = actions.onRefresh,
      modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("tabs-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        when (state) {
          TabsUiState.Loading -> item(key = "loading") {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.settingsColumn()) {
              LoadingList(modifier = Modifier.padding(vertical = 12.dp), rows = 4, loadingLabel = stringResource(R.string.loading))
            }
          }
          is TabsUiState.Error -> item(key = "error") {
            ErrorState(
              title = stringResource(R.string.tabs_error_title),
              body = state.message,
              retryLabel = stringResource(R.string.retry),
              onRetry = actions.onRefresh,
              modifier = Modifier.settingsColumn(),
            )
          }
          is TabsUiState.Loaded -> if (tabs.isEmpty()) {
            item(key = "empty") {
              EmptyState(
                icon = Icons.Outlined.Tab,
                title = stringResource(R.string.tabs_empty_title),
                body = stringResource(R.string.tabs_empty_body),
                modifier = Modifier.settingsColumn(),
              )
            }
          } else {
            itemsIndexed(tabs, key = { _, tab -> "${tab.type}-${tab.url}" }) { index, tab ->
              TabRow(tab, first = index == 0, last = index == tabs.lastIndex, actions)
            }
            item(key = "hint") {
              Text(
                stringResource(R.string.tabs_swipe_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.settingsColumn().padding(start = 4.dp, top = 16.dp),
              )
            }
          }
        }
      }
    }
  }
  if ((state as? TabsUiState.Loaded)?.confirmCloseAll == true) {
    AlertDialog(
      onDismissRequest = actions.onDismissCloseAll,
      modifier = Modifier.testTag("close-all-dialog"),
      title = { Text(stringResource(R.string.are_you_sure)) },
      text = { Text(stringResource(R.string.tab_deletion_confirmation_content)) },
      confirmButton = {
        TextButton(onClick = actions.onConfirmCloseAll, modifier = Modifier.testTag("close-all-confirm")) {
          Text(stringResource(R.string.yes_close), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = actions.onDismissCloseAll, modifier = Modifier.testTag("close-all-cancel")) {
          Text(stringResource(android.R.string.no))
        }
      },
    )
  }
}

/** The tab type label of the old row: Custom Tab, Web View or Article mode. */
@Composable
private fun typeLabel(tab: Tab): String = stringResource(
  when (tab.type) {
    WEB_VIEW, WEB_VIEW_EMBEDDED -> R.string.web_view
    ARTICLE -> R.string.article_mode
    CUSTOM_TAB -> R.string.custom_tab
    else -> R.string.custom_tab
  },
)

/** One open tab: favicon or first letter, title, "type · address" and a close icon. */
@Composable
private fun TabRow(tab: Tab, first: Boolean, last: Boolean, actions: TabsActions) {
  val top = if (first) 16.dp else 0.dp
  val bottom = if (last) 16.dp else 0.dp
  val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
  val title = tab.website?.safeLabel() ?: tab.url
  val dismissState = rememberSwipeToDismissBoxState()
  SwipeToDismissBox(
    state = dismissState,
    modifier = Modifier.settingsColumn().testTag("tab-row"),
    backgroundContent = {
      Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, shape).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Icon(Icons.Outlined.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
      }
    },
    onDismiss = { actions.onClose(tab) },
  ) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
      Column {
        if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { actions.onOpen(tab) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
          SiteAvatar(title, tab.website?.faviconUrl)
          Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              "${typeLabel(tab)} · ${tab.url}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          IconButton(onClick = { actions.onClose(tab) }, modifier = Modifier.testTag("tab-close")) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close_website, title))
          }
        }
      }
    }
  }
}
