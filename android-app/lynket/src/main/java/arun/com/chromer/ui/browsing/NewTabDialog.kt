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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.suggestion.items.SuggestionItem

/**
 * New tab (board new-tab--default): a card at the bottom of the screen, above the keyboard where
 * the old dialog sat, with the "New tab" title, the search field with the mic, suggestions for
 * the typed text (copied text, Google, history), and Cancel and Open.
 */
@Composable
fun NewTabDialog(
  query: String,
  suggestions: Suggestions,
  onQueryChange: (String) -> Unit,
  onSubmit: (String) -> Unit,
  onVoiceUnavailable: () -> Unit,
  onDismiss: () -> Unit,
  requestFocus: Boolean = true,
) {
  BackHandler(onBack = onDismiss)
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
      // A tap outside the card closes it; screen readers close it with Back, so the scrim is no control.
      .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
      .imePadding()
      .navigationBarsPadding(),
    contentAlignment = Alignment.BottomCenter,
  ) {
    NewTabCard(query, suggestions, onQueryChange, onSubmit, onVoiceUnavailable, onDismiss, requestFocus)
  }
}

/** The card on its own, for screenshot tests. */
@Composable
fun NewTabCard(
  query: String,
  suggestions: Suggestions,
  onQueryChange: (String) -> Unit,
  onSubmit: (String) -> Unit,
  onVoiceUnavailable: () -> Unit,
  onDismiss: () -> Unit,
  requestFocus: Boolean = false,
) {
  val focusRequester = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onSubmit)
    }
  }
  if (requestFocus) {
    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
      keyboard?.show()
    }
  }
  Card(
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    modifier = Modifier
      .padding(16.dp)
      .widthIn(max = 680.dp)
      .fillMaxWidth()
      // Taps on the card stay on the card (no click semantics, so it is not read as a button).
      .pointerInput(Unit) { detectTapGestures { } }
      .testTag("new-tab-dialog"),
  ) {
    Column(Modifier.padding(vertical = 16.dp)) {
      Text(
        stringResource(R.string.new_tab_text),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 24.dp),
      )
      OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_or_type_url)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
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
            Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.voice_search))
          }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { if (query.isNotBlank()) onSubmit(query) }),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
          .padding(horizontal = 16.dp, vertical = 12.dp)
          .fillMaxWidth()
          .focusRequester(focusRequester)
          .testTag("new-tab-field"),
      )
      if (suggestions.all.isNotEmpty()) {
        LazyColumn(Modifier.heightIn(max = 240.dp).testTag("new-tab-suggestions")) {
          items(suggestions.all, key = { "${it.type}:${it.title}" }) { item ->
            ListItem(
              headlineContent = { Text(item.title, maxLines = 1) },
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
              modifier = Modifier.clickable(role = Role.Button) {
                onSubmit(if (item is SuggestionItem.HistorySuggestionItem) item.website.url else item.title)
              },
            )
          }
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      ) {
        TextButton(onClick = onDismiss, modifier = Modifier.testTag("new-tab-cancel")) {
          Text(stringResource(android.R.string.cancel))
        }
        Button(
          onClick = { onSubmit(query) },
          enabled = query.isNotBlank(),
          modifier = Modifier.testTag("new-tab-open"),
        ) {
          Text(stringResource(R.string.open))
        }
      }
    }
  }
}
