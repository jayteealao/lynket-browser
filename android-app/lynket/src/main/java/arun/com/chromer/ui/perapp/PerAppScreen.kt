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

package arun.com.chromer.ui.perapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.ui.components.LoadingList
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.settings.components.RowControl
import arun.com.chromer.ui.settings.components.SettingsGroup
import arun.com.chromer.ui.settings.components.SettingsRow
import arun.com.chromer.ui.settings.components.settingsColumn
import arun.com.chromer.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Per-app settings: the master switch, then every app with its secondary browser and incognito rules. */
@Composable
fun PerAppScreen(navigator: LynketNavigator, viewModel: PerAppViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val enabled by viewModel.perAppEnabled.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var askUsage by rememberSaveable { mutableStateOf(false) }
  LifecycleResumeEffect(Unit) {
    viewModel.syncWithUsageAccess(Utils.canReadUsageStats(context))
    onPauseOrDispose { }
  }
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        PerAppEvent.NeedsUsageAccess -> askUsage = true
        is PerAppEvent.MasterSwitched ->
          snackbar.showSnackbar(context.getString(if (event.on) R.string.per_apps_on else R.string.per_apps_off))
      }
    }
  }
  PerAppContent(
    state = state,
    enabled = enabled,
    snackbarHostState = snackbar,
    onBack = { navigator.back() },
    onSearch = viewModel::search,
    onRefresh = viewModel::load,
    onMaster = { viewModel.setPerAppEnabled(it, Utils.canReadUsageStats(context)) },
    onBlacklist = viewModel::setBlacklisted,
    onIncognito = viewModel::setIncognito,
  )
  if (askUsage) {
    AlertDialog(
      onDismissRequest = { askUsage = false },
      modifier = Modifier.testTag("usage-dialog"),
      title = { Text(stringResource(R.string.permission_required)) },
      text = { Text(stringResource(R.string.usage_permission_explanation_per_apps)) },
      confirmButton = {
        TextButton(onClick = {
          askUsage = false
          try {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
          } catch (e: ActivityNotFoundException) {
            Timber.e(e, "No usage access screen")
          }
        }) { Text(stringResource(R.string.grant)) }
      },
      dismissButton = { TextButton(onClick = { askUsage = false }) { Text(stringResource(R.string.not_now)) } },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppContent(
  state: PerAppState,
  enabled: Boolean,
  onBack: () -> Unit,
  onSearch: (String) -> Unit,
  onRefresh: () -> Unit,
  onMaster: (Boolean) -> Unit,
  onBlacklist: (String, Boolean) -> Unit,
  onIncognito: (String, Boolean) -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).testTag("per-app"),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      LargeTopAppBar(
        title = { Text(stringResource(R.string.per_app_settings), fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
          }
        },
        actions = {
          IconButton(onClick = { searching = !searching; if (!searching) onSearch("") }, modifier = Modifier.testTag("per-app-search-toggle")) {
            Icon(
              if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
              contentDescription = stringResource(if (searching) R.string.clear_search else R.string.search_apps),
            )
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { padding ->
    PullToRefreshBox(
      isRefreshing = state.refreshing && state.apps != null,
      onRefresh = onRefresh,
      modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("per-app-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        // No gap between items: the app rows form one container; the items above it pad themselves.
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (searching) {
          item(key = "search") {
            OutlinedTextField(
              value = state.query,
              onValueChange = onSearch,
              singleLine = true,
              label = { Text(stringResource(R.string.search_apps)) },
              leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
              modifier = Modifier.settingsColumn().padding(bottom = 16.dp).testTag("per-app-search"),
            )
          }
        }
        item(key = "master") {
          SettingsGroup(label = null, modifier = Modifier.settingsColumn().padding(bottom = 16.dp)) {
            row {
              SettingsRow(
                title = stringResource(R.string.per_app_enable),
                summary = AnnotatedString(stringResource(R.string.per_app_enable_summary)),
                control = RowControl.Switch(enabled),
                modifier = Modifier.testTag("row-blacklist_preference"),
                onClick = { onMaster(!enabled) },
              )
            }
          }
        }
        val visible = state.visible
        when {
          visible == null -> item(key = "loading") {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.settingsColumn()) {
              LoadingList(modifier = Modifier.padding(vertical = 12.dp), rows = 8, loadingLabel = stringResource(R.string.loading))
            }
          }
          visible.isEmpty() && state.query.isNotBlank() -> item(key = "no-match") {
            Text(
              stringResource(R.string.per_app_no_match, state.query.trim()),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.settingsColumn().padding(16.dp).testTag("per-app-no-match"),
            )
          }
          else -> itemsIndexed(visible, key = { _, app -> app.packageName }) { index, app ->
            AppRuleRow(app, index, visible.lastIndex, onBlacklist, onIncognito)
          }
        }
      }
    }
  }
}

/**
 * One app: icon, name and package, then the secondary browser and incognito toggles of the old
 * row. Rows sit in one rounded container; the first and last row carry its corners.
 */
@Composable
private fun AppRuleRow(
  app: PerAppRow,
  index: Int,
  last: Int,
  onBlacklist: (String, Boolean) -> Unit,
  onIncognito: (String, Boolean) -> Unit,
) {
  val context = LocalContext.current
  val icon by produceState<ImageBitmap?>(null, app.packageName) {
    value = withContext(Dispatchers.IO) {
      runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
  }
  val top = if (index == 0) 16.dp else 0.dp
  val bottom = if (index == last) 16.dp else 0.dp
  Surface(
    shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.settingsColumn().testTag("app-${app.packageName}"),
  ) {
    Column {
      if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
        ) {
          val bitmap = icon
          if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
          } else {
            Text(app.appName.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
          }
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
          Text(app.appName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            app.packageName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        RuleToggle(
          checked = app.blacklisted,
          icon = Icons.Outlined.Public,
          description = stringResource(R.string.per_app_secondary_browser, app.appName),
          tag = "blacklist-${app.packageName}",
        ) { onBlacklist(app.packageName, it) }
        RuleToggle(
          checked = app.incognito,
          icon = Icons.Outlined.VisibilityOff,
          description = stringResource(R.string.per_app_incognito, app.appName),
          tag = "incognito-${app.packageName}",
        ) { onIncognito(app.packageName, it) }
      }
    }
  }
}

@Composable
private fun RuleToggle(
  checked: Boolean,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  tag: String,
  onChange: (Boolean) -> Unit,
) {
  IconToggleButton(
    checked = checked,
    onCheckedChange = onChange,
    modifier = Modifier.testTag(tag).semantics { contentDescription = description },
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    )
  }
}
