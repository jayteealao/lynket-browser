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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.provider.SearchProvider
import arun.com.chromer.search.suggestion.items.SuggestionItem

/**
 * The home's app bar: a collapsed 56 dp search pill with the menu at the start and the mic at the
 * end. Expanded, it lists copied text, Google and history suggestions (the old search view's
 * order) and the search engine choice.
 */
@Composable
fun HomeSearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  suggestions: Suggestions,
  searchEngines: List<SearchProvider>,
  selectedEngine: SearchProvider,
  onSelectEngine: (SearchProvider) -> Unit,
  onSubmit: (String) -> Unit,
  onSuggestion: (SuggestionItem) -> Unit,
  onMenu: () -> Unit,
  onVoiceUnavailable: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
        onQueryChange(it)
        onSubmit(it)
      }
    }
  }
  val menuLabel = stringResource(R.string.open_menu)
  val voiceLabel = stringResource(R.string.voice_search)
  val backLabel = stringResource(R.string.back)

  SearchBar(
    inputField = {
      SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSubmit(it) },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        placeholder = { Text(stringResource(R.string.search_or_type_url)) },
        leadingIcon = {
          if (expanded) {
            IconButton(onClick = { onExpandedChange(false) }) {
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
    },
    expanded = expanded,
    onExpandedChange = onExpandedChange,
    colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    modifier = modifier.fillMaxWidth(),
  ) {
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
}
