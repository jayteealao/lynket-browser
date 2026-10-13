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

package arun.com.chromer.ui.history

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.history.HistoryDialog
import arun.com.chromer.history.HistoryEvent
import arun.com.chromer.history.HistoryUiState
import arun.com.chromer.history.ModernHistoryViewModel
import arun.com.chromer.ui.components.SiteAvatar
import arun.com.chromer.ui.components.EmptyState
import arun.com.chromer.ui.components.ErrorState
import arun.com.chromer.ui.components.LoadingList
import arun.com.chromer.ui.settings.components.RowControl
import arun.com.chromer.ui.settings.components.RowIcon
import arun.com.chromer.ui.settings.components.SettingsGroup
import arun.com.chromer.ui.settings.components.SettingsRow
import arun.com.chromer.ui.settings.components.settingsColumn
import arun.com.chromer.util.Utils
import arun.com.chromer.ui.components.ScreenTitle
import kotlinx.coroutines.launch
import java.util.TimeZone

/** What a person can do on the History screen; the screen itself holds no state. */
class HistoryActions(
  val onBack: () -> Unit = {},
  val onHistoryEnabled: (Boolean) -> Unit = {},
  val onIncognito: (Boolean) -> Unit = {},
  val onOpenSearch: () -> Unit = {},
  val onCloseSearch: () -> Unit = {},
  val onQuery: (String) -> Unit = {},
  val onOpen: (Website) -> Unit = {},
  val onOpenAmp: (Website) -> Unit = {},
  val onDelete: (Website) -> Unit = {},
  val onToggleBookmark: (Website) -> Unit = {},
  val onClearAll: () -> Unit = {},
  val onConfirmClearAll: () -> Unit = {},
  val onDismissDialog: () -> Unit = {},
)

/**
 * History, shown by the main navigation and by HistoryActivity (the Custom Tab menu and the
 * launcher shortcut): the History and Incognito browsing switches, then the visited sites by day,
 * then the floating delete-all button, as on the old screen.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: ModernHistoryViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val items = viewModel.items.collectAsLazyPagingItems()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  LifecycleResumeEffect(viewModel) {
    viewModel.syncSettings()
    onPauseOrDispose { }
  }
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      snackbar.currentSnackbarData?.dismiss()
      scope.launch {
        when (event) {
          is HistoryEvent.Deleted -> {
            val result = snackbar.showSnackbar(
              message = context.getString(R.string.history_removed),
              actionLabel = context.getString(R.string.undo),
              duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.website)
          }
          is HistoryEvent.Cleared -> snackbar.showSnackbar(context.getString(R.string.deleted_items, event.count))
        }
      }
    }
  }
  val providerName = remember(state.providerPackage) {
    state.providerPackage?.let { runCatching { Utils.getAppNameWithPackage(context, it) }.getOrNull() }
  }
  // One instance per screen, so a keystroke or a switch does not recompose every row with new actions.
  val actions = remember(viewModel, context, onBack) {
    HistoryActions(
      onBack = onBack,
      onHistoryEnabled = viewModel::setHistoryEnabled,
      onIncognito = viewModel::setIncognito,
      onOpenSearch = viewModel::openSearch,
      onCloseSearch = viewModel::closeSearch,
      onQuery = viewModel::setQuery,
      onOpen = { viewModel.open(context, it) },
      onOpenAmp = { viewModel.openAmp(context, it) },
      onDelete = viewModel::delete,
      onToggleBookmark = viewModel::toggleBookmark,
      onClearAll = viewModel::requestClearAll,
      onConfirmClearAll = viewModel::confirmClearAll,
      onDismissDialog = viewModel::dismissDialog,
    )
  }
  HistoryContent(
    state = state,
    items = items,
    providerName = providerName,
    snackbarHostState = snackbar,
    actions = actions,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
  state: HistoryUiState,
  items: LazyPagingItems<HistoryListItem>,
  providerName: String?,
  actions: HistoryActions,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  now: Long = System.currentTimeMillis(),
  timeZone: TimeZone = TimeZone.getDefault(),
) {
  val context = LocalContext.current
  val listState = rememberLazyListState()
  // The search field is the first item; without this scroll it would open above the screen.
  LaunchedEffect(state.searchOpen) { if (state.searchOpen) listState.scrollToItem(0) }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
  val refresh = items.loadState.refresh
  val hasRows = items.itemCount > 0
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).testTag("history"),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      LargeTopAppBar(
        title = { ScreenTitle(stringResource(R.string.title_history)) },
        navigationIcon = {
          IconButton(onClick = actions.onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
          }
        },
        actions = {
          IconButton(
            onClick = if (state.searchOpen) actions.onCloseSearch else actions.onOpenSearch,
            modifier = Modifier.testTag("history-search-toggle"),
          ) {
            Icon(
              if (state.searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
              contentDescription = stringResource(if (state.searchOpen) R.string.clear_search else R.string.history_search_hint),
            )
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
    floatingActionButton = {
      if (hasRows) {
        FloatingActionButton(onClick = actions.onClearAll, modifier = Modifier.testTag("history-clear-all")) {
          Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.history_clear_all))
        }
      }
    },
  ) { padding ->
    PullToRefreshBox(
      isRefreshing = refresh is LoadState.Loading && hasRows,
      onRefresh = { items.refresh() },
      modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("history-list"),
        // Room for the floating button under the last row.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (state.searchOpen) {
          item(key = "search", contentType = "search") {
            OutlinedTextField(
              value = state.query,
              onValueChange = actions.onQuery,
              singleLine = true,
              label = { Text(stringResource(R.string.history_search_hint)) },
              leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
              modifier = Modifier.settingsColumn().padding(bottom = 16.dp).testTag("history-search"),
            )
          }
        }
        item(key = "switches", contentType = "switches") {
          HistorySwitches(state, providerName, actions, Modifier.settingsColumn().padding(bottom = 8.dp))
        }
        when {
          refresh is LoadState.Error && !hasRows -> item(key = "error", contentType = "state") {
            ErrorState(
              title = stringResource(R.string.history_error_title),
              body = stringResource(R.string.history_error_body),
              retryLabel = stringResource(R.string.retry),
              onRetry = { items.retry() },
              modifier = Modifier.settingsColumn(),
            )
          }
          refresh is LoadState.Loading && !hasRows -> item(key = "loading", contentType = "state") {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.settingsColumn().padding(top = 8.dp),
            ) {
              LoadingList(modifier = Modifier.padding(vertical = 12.dp), rows = 8, loadingLabel = stringResource(R.string.loading))
            }
          }
          !hasRows && state.query.isNotBlank() -> item(key = "no-match", contentType = "state") {
            EmptyState(
              icon = Icons.Outlined.SearchOff,
              title = stringResource(R.string.history_search_empty, state.query.trim()),
              body = stringResource(R.string.history_empty_body),
              modifier = Modifier.settingsColumn().testTag("history-no-match"),
            )
          }
          !hasRows -> item(key = "empty", contentType = "state") {
            EmptyState(
              icon = Icons.Outlined.History,
              title = stringResource(R.string.history_empty_title),
              body = stringResource(R.string.history_empty_body),
              modifier = Modifier.settingsColumn(),
            )
          }
          else -> items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.key ?: "placeholder-$index" },
            contentType = { index -> if (items.peek(index) is HistoryListItem.DayHeader) "day" else "row" },
          ) { index ->
            when (val item = items[index]) {
              is HistoryListItem.DayHeader -> DayLabel(dayLabel(context, item.dayStart, now, timeZone))
              is HistoryListItem.Row -> {
                val first = index == 0 || items.peek(index - 1) !is HistoryListItem.Row
                val last = index == items.itemCount - 1 || items.peek(index + 1) !is HistoryListItem.Row
                HistoryRow(item.website, first, last, actions)
              }
              null -> Unit
            }
          }
        }
      }
    }
  }
  when (state.dialog) {
    HistoryDialog.ClearConfirm -> AlertDialog(
      onDismissRequest = actions.onDismissDialog,
      modifier = Modifier.testTag("clear-dialog").semantics { testTagsAsResourceId = true },
      icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
      title = { Text(stringResource(R.string.are_you_sure)) },
      text = { Text(stringResource(R.string.history_deletion_confirmation_content)) },
      confirmButton = {
        TextButton(onClick = actions.onConfirmClearAll, modifier = Modifier.testTag("clear-confirm")) {
          Text(stringResource(R.string.yes_delete), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = actions.onDismissDialog, modifier = Modifier.testTag("clear-cancel")) {
          Text(stringResource(android.R.string.no))
        }
      },
    )
    HistoryDialog.IncognitoExplanation -> AlertDialog(
      onDismissRequest = actions.onDismissDialog,
      modifier = Modifier.testTag("incognito-dialog").semantics { testTagsAsResourceId = true },
      icon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
      title = { Text(stringResource(R.string.incognito_mode)) },
      text = { Text(stringResource(R.string.full_incognito_mode_explanation)) },
      confirmButton = { TextButton(onClick = actions.onDismissDialog) { Text(stringResource(android.R.string.ok)) } },
    )
    HistoryDialog.None -> Unit
  }
}

/** The two switches of the old screen, in its order: History, then Incognito browsing. */
@Composable
private fun HistorySwitches(state: HistoryUiState, providerName: String?, actions: HistoryActions, modifier: Modifier) {
  val historySummary = if (providerName == null) {
    AnnotatedString(stringResource(R.string.enable_history_subtitle))
  } else {
    val html = stringResource(R.string.enable_history_subtitle_custom_tab, providerName)
    remember(html) { runCatching { AnnotatedString.fromHtml(html) }.getOrElse { AnnotatedString(html) } }
  }
  SettingsGroup(label = null, modifier = modifier) {
    row {
      SettingsRow(
        title = stringResource(R.string.title_history),
        summary = historySummary,
        icon = RowIcon(Icons.Outlined.History),
        control = RowControl.Switch(state.historyEnabled),
        modifier = Modifier.testTag("history-switch"),
        onClick = { actions.onHistoryEnabled(!state.historyEnabled) },
      )
    }
    row {
      SettingsRow(
        title = stringResource(R.string.full_incognito_mode),
        summary = AnnotatedString(stringResource(R.string.full_incognito_mode_explanation)),
        icon = RowIcon(Icons.Outlined.VisibilityOff),
        control = RowControl.Switch(state.incognito),
        modifier = Modifier.testTag("incognito-switch"),
        onClick = { actions.onIncognito(!state.incognito) },
      )
    }
  }
}

/** A day header: the section label of the board, in the primary colour. */
@Composable
private fun DayLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
      .settingsColumn()
      .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
      .semantics { heading() }
      .testTag("history-day"),
  )
}

/**
 * One visited site: favicon or first letter, title, address, the AMP action when the site has
 * one, and a menu with bookmark and remove. A sideways swipe removes it too. Rows of one day sit
 * in one rounded container; the first and last row carry its corners.
 */
@Composable
private fun HistoryRow(website: Website, first: Boolean, last: Boolean, actions: HistoryActions) {
  val top = if (first) 16.dp else 0.dp
  val bottom = if (last) 16.dp else 0.dp
  val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
  val dismissState = rememberSwipeToDismissBoxState()
  SwipeToDismissBox(
    state = dismissState,
    modifier = Modifier.settingsColumn().testTag("history-row"),
    backgroundContent = {
      Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, shape).padding(horizontal = 24.dp),
        // The icon sits on the side the row uncovers, for either swipe direction.
        contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
      ) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
      }
    },
    onDismiss = { actions.onDelete(website) },
  ) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
      Column {
        if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { actions.onOpen(website) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
          SiteAvatar(website.safeLabel(), website.faviconUrl)
          Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(website.safeLabel(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              website.preferredUrl(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (website.hasAmp()) {
            IconButton(onClick = { actions.onOpenAmp(website) }, modifier = Modifier.testTag("history-amp")) {
              Icon(Icons.Outlined.Bolt, contentDescription = stringResource(R.string.history_open_amp, website.safeLabel()))
            }
          }
          RowMenu(website, actions)
        }
      }
    }
  }
}

@Composable
private fun RowMenu(website: Website, actions: HistoryActions) {
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }, modifier = Modifier.testTag("history-row-menu")) {
      Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.history_more_options, website.safeLabel()))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(if (website.bookmarked) R.string.remove_bookmark else R.string.add_bookmark)) },
        onClick = { open = false; actions.onToggleBookmark(website) },
        modifier = Modifier.testTag("history-menu-bookmark"),
      )
      DropdownMenuItem(
        text = { Text(stringResource(R.string.remove_from_history)) },
        onClick = { open = false; actions.onDelete(website) },
        modifier = Modifier.testTag("history-menu-remove"),
      )
    }
  }
}

