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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.provider.SearchProvider
import arun.com.chromer.search.suggestion.items.SuggestionItem
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The home's app bar: a collapsed 56 dp search pill with the menu at the start and the mic at the
 * end. Expanded, it lists copied text, Google and history suggestions (the old search view's
 * order) and the search engine choice.
 */
@Composable
fun HomeSearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  searchBarState: SearchBarState,
  suggestions: Suggestions,
  searchEngines: List<SearchProvider>,
  selectedEngine: SearchProvider,
  onSelectEngine: (SearchProvider) -> Unit,
  onSubmit: (String) -> Unit,
  onSuggestion: (SuggestionItem) -> Unit,
  onMenu: () -> Unit,
  onVoiceUnavailable: () -> Unit,
  modifier: Modifier = Modifier,
  /** The screen's messages; the open search shows them too, since it covers the screen. */
  snackbarHostState: SnackbarHostState? = null,
) {
  // The field owns the text: typing flows up through onQueryChange, and nothing flows back down,
  // so a query that arrives late from the screen can never overwrite newer typing. A spoken query
  // is written into the field here.
  val text = rememberTextFieldState(query)
  val currentQuery by rememberUpdatedState(query)
  val currentOnQueryChange by rememberUpdatedState(onQueryChange)
  LaunchedEffect(text) {
    snapshotFlow { text.text.toString() }.collect { if (it != currentQuery) currentOnQueryChange(it) }
  }
  val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
        text.setTextAndPlaceCursorAtEnd(it)
        onQueryChange(it)
        onSubmit(it)
      }
    }
  }
  val menuLabel = stringResource(R.string.open_menu)
  val voiceLabel = stringResource(R.string.voice_search)
  val backLabel = stringResource(R.string.back)
  val scope = rememberCoroutineScope()
  val colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

  // One field, drawn in the closed bar and again at the top of the open search.
  val inputField = @Composable {
    SearchBarDefaults.InputField(
      textFieldState = text,
      searchBarState = searchBarState,
      onSearch = { onSubmit(it) },
      placeholder = { Text(stringResource(R.string.search_or_type_url)) },
      leadingIcon = {
        if (searchBarState.targetValue == SearchBarValue.Expanded) {
          IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
          }
        } else {
          IconButton(onClick = onMenu, modifier = Modifier.testTag("home-menu")) {
            Icon(Icons.Outlined.Menu, contentDescription = menuLabel)
          }
        }
      },
      trailingIcon = {
        IconButton(
          onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
              .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            try {
              voice.launch(intent)
            } catch (e: ActivityNotFoundException) {
              onVoiceUnavailable()
            }
          },
        ) {
          Icon(Icons.Outlined.Mic, contentDescription = voiceLabel)
        }
      },
      modifier = Modifier.testTag("home-search-field"),
    )
  }

  // The closed bar draws its pill under the field, outside any clip, as the bar always has: a
  // clipped pill would change the pixels of its rounded edge.
  val pill = rememberPillShape()
  SearchBar(
    state = searchBarState,
    inputField = { Box(Modifier.background(colors.containerColor, pill), propagateMinConstraints = true) { inputField() } },
    shape = RectangleShape,
    colors = SearchBarDefaults.colors(containerColor = Color.Transparent),
    // The space above the pill: the status bar, when nothing above took it, and 8 dp.
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsPadding(SearchBarDefaults.windowInsets)
      .padding(top = 8.dp),
  )
  ExpandedFullScreenSearchBar(
    state = searchBarState,
    inputField = inputField,
    colors = colors,
    // The open search covers what the home's content covers: it stops at the navigation bar and the
    // keyboard, and its field stays where the closed one is, under the home's safe drawing area and
    // the closed bar's own status bar space, then 8 dp. It is a window of its own, so the home's ids
    // for device checks are switched on here too.
    windowInsets = { WindowInsets.safeDrawing.add(WindowInsets.statusBars).only(WindowInsetsSides.Top) },
    modifier = Modifier
      .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
      .centredColumn(searchBarState)
      .semantics { testTagsAsResourceId = true },
  ) {
    // The open search is a layer over the whole screen, so the screen's message shows on it too,
    // where the screen shows it.
    Box(Modifier.fillMaxSize()) {
      Column {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          searchEngines.forEach { engine ->
            FilterChip(
              selected = engine.name == selectedEngine.name,
              onClick = { onSelectEngine(engine) },
              label = { Text(engine.name) },
            )
          }
        }
        LazyColumn(modifier = Modifier.testTag("home-suggestions")) {
          items(suggestions.all, key = { "${it.type}:${it.title}" }) { item ->
            ListItem(
              headlineContent = { Text(item.title, maxLines = 2) },
              supportingContent = item.subTitle?.let { { Text(it, maxLines = 1) } },
              leadingContent = {
                Icon(
                  imageVector = when (item) {
                    is SuggestionItem.CopySuggestionItem -> Icons.Outlined.ContentPaste
                    is SuggestionItem.GoogleSuggestionItem -> Icons.Outlined.Search
                    is SuggestionItem.HistorySuggestionItem -> Icons.Outlined.History
                  },
                  contentDescription = null,
                )
              },
              colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
              modifier = Modifier.clickable(role = Role.Button) { onSuggestion(item) },
            )
          }
        }
      }
      snackbarHostState?.let { SnackbarHost(it, Modifier.align(Alignment.BottomCenter)) }
    }
  }
}

/**
 * The closed bar's pill: a rounded rectangle with 28 dp corners drawn as a path, the outline the
 * bar has always had, so its edge pixels stay as they were.
 */
@Composable
private fun rememberPillShape(): Shape {
  val density = LocalDensity.current
  return remember(density) {
    val radius = with(density) { (SearchBarDefaults.InputFieldHeight / 2).toPx() }
    GenericShape { size, _ -> addRoundRect(RoundRect(size.toRect(), CornerRadius(radius))) }
  }
}

/** The widest the open search gets: the home's centred column on a fold or a tablet. */
private val OpenSearchMaxWidth = HomeColumnMaxWidth

/**
 * On a wide window the open search fills the home's centred 680 dp column, as the home's column
 * does. The open search grows from the closed bar's place in the window, so the column slides in
 * from the window's start edge with the same progress: the motion starts at the bar and ends
 * centred.
 */
private fun Modifier.centredColumn(state: SearchBarState) = layout { measurable, constraints ->
  val width = min(constraints.maxWidth, OpenSearchMaxWidth.roundToPx())
  val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
  // Centred as the home's column is centred, with the same rounding.
  val shift = Alignment.CenterHorizontally.align(width, constraints.maxWidth, layoutDirection)
  layout(constraints.maxWidth, placeable.height) {
    placeable.place((shift * state.progress).roundToInt(), 0)
  }
}
