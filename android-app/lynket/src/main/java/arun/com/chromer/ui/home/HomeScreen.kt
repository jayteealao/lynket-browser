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

package arun.com.chromer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.resolveStringResource
import arun.com.chromer.home.CustomTabProviderInfo
import arun.com.chromer.home.ModernHomeViewModel
import arun.com.chromer.home.ModernHomeViewModel.HomeUiState
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.provider.SearchProvider
import arun.com.chromer.search.provider.SearchProviders
import arun.com.chromer.search.suggestion.items.SuggestionItem
import arun.com.chromer.ui.components.EmptyState
import arun.com.chromer.ui.components.ErrorState
import arun.com.chromer.ui.components.LoadingTiles
import arun.com.chromer.ui.components.showTransientError
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.navigation.navigateToAbout
import arun.com.chromer.ui.navigation.navigateToHistory
import arun.com.chromer.ui.navigation.navigateToIntro
import arun.com.chromer.ui.navigation.navigateToProviderSelection
import arun.com.chromer.ui.navigation.navigateToSettings
import arun.com.chromer.ui.navigation.navigateToTabs
import arun.com.chromer.ui.navigation.navigateToTips
import arun.com.chromer.util.appicon.ApplicationIcon
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** Everything the home shows, independent of where it comes from (view model or a test). */
data class HomeContentState(
  val recents: List<Website>? = null,
  val providerInfo: CustomTabProviderInfo? = null,
  val openTabs: Int = 0,
  val isOnline: Boolean = true,
  val query: String = "",
  val suggestions: Suggestions = Suggestions(),
  val searchEngines: List<SearchProvider> = emptyList(),
  val selectedEngine: SearchProvider = SearchProviders.GOOGLE_SEARCH_PROVIDER,
  val loadError: String? = null,
)

/** What the home's controls do. Defaults do nothing, for previews and screenshot tests. */
data class HomeActions(
  val onQueryChange: (String) -> Unit = {},
  val onSubmit: (String) -> Unit = {},
  val onSuggestion: (SuggestionItem) -> Unit = {},
  val onSelectEngine: (SearchProvider) -> Unit = {},
  val onVoiceUnavailable: () -> Unit = {},
  val onOpenWebsite: (Website) -> Unit = {},
  val onToggleBookmark: (Website) -> Unit = {},
  val onRemoveWebsite: (Website) -> Unit = {},
  val onHistory: () -> Unit = {},
  val onTabs: () -> Unit = {},
  val onChangeProvider: () -> Unit = {},
  val onRetryProvider: () -> Unit = {},
  val onRetryRecents: () -> Unit = {},
  val onRetryConnection: () -> Unit = {},
  val onTips: () -> Unit = {},
  val onDrawerItem: (DrawerItem) -> Unit = {},
)

/** Window width at which the home uses 8 recents columns and a centred column (medium size class). */
private const val MEDIUM_WIDTH_DP = 600

@Composable
fun HomeScreen(
  navigator: LynketNavigator,
  viewModel: ModernHomeViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val activeTabs by viewModel.activeTabs.collectAsStateWithLifecycle()
  val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
  val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
  val selectedEngine by viewModel.selectedSearchEngine.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  val snackbarHostState = remember { SnackbarHostState() }
  val noVoiceApps = stringResource(R.string.no_voice_rec_apps)
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) { viewModel.initializeDefaultProvider() }

  val state = HomeContentState(
    recents = (uiState as? HomeUiState.Success)?.recentWebsites,
    providerInfo = (uiState as? HomeUiState.Success)?.providerInfo,
    openTabs = activeTabs.size,
    isOnline = isOnline,
    query = query,
    suggestions = suggestions,
    searchEngines = viewModel.searchEngines,
    selectedEngine = selectedEngine,
    loadError = (uiState as? HomeUiState.Error)?.message,
  )
  val actions = HomeActions(
    onQueryChange = { query = it; viewModel.onQueryChange(it) },
    onSubmit = { viewModel.submitSearch(context, it) },
    onSuggestion = { item ->
      when (item) {
        is SuggestionItem.HistorySuggestionItem -> viewModel.openWebsite(context, item.website)
        else -> viewModel.submitSearch(context, item.title)
      }
    },
    onSelectEngine = viewModel::selectSearchEngine,
    onVoiceUnavailable = { scope.launch { snackbarHostState.showSnackbar(noVoiceApps) } },
    onOpenWebsite = { viewModel.openWebsite(context, it) },
    onToggleBookmark = viewModel::toggleBookmark,
    onRemoveWebsite = viewModel::deleteWebsite,
    onHistory = { navigator.navigateToHistory() },
    onTabs = { navigator.navigateToTabs() },
    onChangeProvider = { navigator.navigateToProviderSelection() },
    onRetryProvider = viewModel::rescanProviders,
    onRetryRecents = viewModel::refreshRecents,
    onRetryConnection = viewModel::retryConnection,
    onTips = { navigator.navigateToTips() },
    onDrawerItem = { item ->
      when (item) {
        DrawerItem.Home -> Unit
        DrawerItem.History -> navigator.navigateToHistory()
        DrawerItem.Tabs -> navigator.navigateToTabs()
        DrawerItem.Settings -> navigator.navigateToSettings()
        DrawerItem.Tips -> navigator.navigateToTips()
        DrawerItem.Intro -> navigator.navigateToIntro()
        DrawerItem.Feedback -> HomeDrawerIntents.feedback(context)
        DrawerItem.Rate -> HomeDrawerIntents.rate(context)
        DrawerItem.Beta -> HomeDrawerIntents.beta(context)
        DrawerItem.Share -> HomeDrawerIntents.share(context)
        DrawerItem.About -> navigator.navigateToAbout()
      }
    },
  )
  HomeScreenContent(state = state, actions = actions, snackbarHostState = snackbarHostState)
}

/**
 * The home, stateless: search pill as the app bar; the status section (open tabs, provider); the
 * Recents header with History; the recents grid; the tips card. A modal drawer holds the other
 * areas. While offline, a snackbar with Retry stays until the connection returns.
 */
@Composable
fun HomeScreenContent(
  state: HomeContentState,
  actions: HomeActions,
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
  searchExpanded: Boolean = false,
) {
  val scope = rememberCoroutineScope()
  var expanded by rememberSaveable { mutableStateOf(searchExpanded) }
  val offlineMessage = stringResource(R.string.offline_message)
  val retry = stringResource(R.string.retry)

  // Retry reads the home again; while the connection is still down, the message comes back.
  var connectionRetries by remember { mutableIntStateOf(0) }
  LaunchedEffect(state.isOnline, connectionRetries) {
    if (!state.isOnline) {
      snackbarHostState.showTransientError(
        offlineMessage,
        retry,
        onAction = {
          actions.onRetryConnection()
          connectionRetries++
        },
      )
    } else {
      snackbarHostState.currentSnackbarData?.dismiss()
    }
  }

  val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
  val wide = widthDp >= MEDIUM_WIDTH_DP
  val columns = if (wide) 8 else 4
  val gutter = if (wide) 24.dp else 16.dp

  // Back with the drawer open closes the drawer; the home stays (as the old menu sheet did).
  BackHandler(enabled = drawerState.isOpen) {
    scope.launch { drawerState.close() }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      HomeDrawerSheet(onItem = { item ->
        scope.launch { drawerState.close() }
        actions.onDrawerItem(item)
      })
    },
    modifier = modifier,
  ) {
    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.TopCenter,
      ) {
        Column(
          // The cap comes first: after fillMaxSize the minimum width is the window's, which
          // widthIn cannot lower.
          modifier = Modifier
            .widthIn(max = 680.dp)
            .fillMaxSize(),
        ) {
          HomeSearchBar(
            query = state.query,
            onQueryChange = actions.onQueryChange,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            suggestions = state.suggestions,
            searchEngines = state.searchEngines,
            selectedEngine = state.selectedEngine,
            onSelectEngine = actions.onSelectEngine,
            onSubmit = { expanded = false; actions.onSubmit(it) },
            onSuggestion = { expanded = false; actions.onSuggestion(it) },
            onMenu = { scope.launch { drawerState.open() } },
            onVoiceUnavailable = actions.onVoiceUnavailable,
            modifier = Modifier.padding(horizontal = if (expanded) 0.dp else gutter),
          )
          LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
              .fillMaxSize()
              .testTag("home-feed"),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            homeFeed(state, actions, columns)
          }
        }
      }
    }
  }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
  item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.homeFeed(state: HomeContentState, actions: HomeActions, columns: Int) {
  if (state.openTabs > 0) {
    fullWidth("tabs") { TabsCard(state.openTabs, actions.onTabs) }
  }
  state.providerInfo?.let { info ->
    fullWidth("provider") { ProviderCard(info, actions.onChangeProvider, actions.onRetryProvider) }
  }
  fullWidth("recents-header") { RecentsHeader(showHistory = !state.recents.isNullOrEmpty(), onHistory = actions.onHistory) }
  val recents = state.recents
  when {
    state.loadError != null -> fullWidth("recents-error") {
      ErrorState(
        title = stringResource(R.string.recents),
        body = state.loadError,
        retryLabel = stringResource(R.string.retry),
        onRetry = actions.onRetryRecents,
        compact = true,
      )
    }
    recents == null -> fullWidth("recents-loading") { LoadingTiles(columns = columns) }
    recents.isEmpty() -> fullWidth("recents-empty") {
      EmptyState(
        icon = Icons.Outlined.History,
        title = stringResource(R.string.recents_empty_title),
        body = stringResource(R.string.recents_empty_body),
      )
    }
    else -> items(recents, key = { "recent:${it.url}" }) { website ->
      RecentTile(website, actions)
    }
  }
  fullWidth("tips") { TipsCard(actions.onTips) }
}

@Composable
private fun TabsCard(openTabs: Int, onTabs: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("home-tabs-card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(
      modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Tab, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        text = stringResource(R.string.active_tabs_count, openTabs),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
      )
      TextButton(onClick = onTabs, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.title_tabs))
      }
    }
  }
}

@Composable
private fun ProviderCard(info: CustomTabProviderInfo, onChange: () -> Unit, onRetry: () -> Unit) {
  val context = LocalContext.current
  Card(
    modifier = Modifier.fillMaxWidth().testTag("home-provider-card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (info.noProviderInstalled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer,
    ),
  ) {
    if (info.noProviderInstalled) {
      ErrorState(
        title = stringResource(R.string.provider_missing_title),
        body = stringResource(R.string.provider_missing_body),
        retryLabel = stringResource(R.string.retry),
        onRetry = onRetry,
        actionLabel = stringResource(R.string.Change),
        onAction = onChange,
        compact = true,
        modifier = Modifier.testTag("home-provider-error"),
      )
      return@Card
    }
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      AppIcon(info, Modifier.size(40.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.custom_tab_provider),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          text = html(context.resolveStringResource(info.providerDescription)),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (info.providerReason.resource != 0) {
          Text(
            text = context.resolveStringResource(info.providerReason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
      if (info.allowChange) {
        FilledTonalButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp)) {
          Text(stringResource(R.string.Change))
        }
      }
    }
  }
}

private fun html(text: String): AnnotatedString = runCatching { AnnotatedString.fromHtml(text) }.getOrElse { AnnotatedString(text) }

/**
 * The provider's launcher icon, loaded by the image loader off the main thread; a globe for the
 * built-in web view and while the icon is missing or loading.
 */
@Composable
private fun AppIcon(info: CustomTabProviderInfo, modifier: Modifier) {
  var loaded by remember(info.iconUri) { mutableStateOf(false) }
  Box(modifier, contentAlignment = Alignment.Center) {
    if (!loaded) {
      Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      }
    }
    if (info.iconUri.scheme == ApplicationIcon.URI_SCHEME_APPLICATION_ICON) {
      AsyncImage(
        model = info.iconUri,
        contentDescription = null,
        modifier = Modifier.matchParentSize(),
        onSuccess = { loaded = true },
      )
    }
  }
}

@Composable
private fun RecentsHeader(showHistory: Boolean, onHistory: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.recents),
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
      modifier = Modifier.semantics { heading() },
    )
    if (showHistory) {
      TextButton(onClick = onHistory, modifier = Modifier.heightIn(min = 48.dp).testTag("home-history")) {
        Text(stringResource(R.string.title_history))
      }
    } else {
      Spacer(Modifier.heightIn(min = 48.dp))
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentTile(website: Website, actions: HomeActions) {
  var menuOpen by remember { mutableStateOf(false) }
  val label = website.safeLabel()
  // A page without a title is labelled by its URL; its letter comes from the site's name instead.
  val initial = remember(website.url, label) {
    val host = if (website.title.isNullOrBlank()) android.net.Uri.parse(website.preferredUrl()).host else null
    (host?.removePrefix("www.") ?: label).take(1).uppercase()
  }
  Box {
    Column(
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .combinedClickable(
          onClick = { actions.onOpenWebsite(website) },
          onLongClick = { menuOpen = true },
          onLongClickLabel = stringResource(R.string.more_options),
          role = Role.Button,
        )
        .padding(vertical = 4.dp)
        .semantics { contentDescription = label }
        .testTag("recent-tile"),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
      ) {
        // The letter stays until a favicon has loaded, so a missing or broken icon never leaves
        // the tile empty.
        var iconLoaded by remember(website.faviconUrl) { mutableStateOf(false) }
        if (!iconLoaded) {
          Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (!website.faviconUrl.isNullOrBlank()) {
          AsyncImage(
            model = website.faviconUrl,
            contentDescription = null,
            onSuccess = { iconLoaded = true },
            onError = { iconLoaded = false },
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
          )
        }
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(72.dp),
        textAlign = TextAlign.Center,
      )
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(if (website.bookmarked) R.string.remove_bookmark else R.string.add_bookmark)) },
        onClick = { menuOpen = false; actions.onToggleBookmark(website) },
      )
      DropdownMenuItem(
        text = { Text(stringResource(R.string.remove_from_history)) },
        onClick = { menuOpen = false; actions.onRemoveWebsite(website) },
      )
    }
  }
}

@Composable
private fun TipsCard(onTips: () -> Unit) {
  Card(
    onClick = onTips,
    modifier = Modifier.fillMaxWidth().testTag("home-tips-card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.home_tips_title), style = MaterialTheme.typography.titleMedium)
        Text(
          stringResource(R.string.home_tips_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

