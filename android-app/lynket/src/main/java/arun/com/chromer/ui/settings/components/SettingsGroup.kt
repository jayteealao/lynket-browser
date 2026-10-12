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

package arun.com.chromer.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.ui.theme.ChromerTheme

/** Collects the rows of one [SettingsGroup]; dividers go between the rows. */
class SettingsGroupScope internal constructor() {
  internal val rows = mutableListOf<@Composable () -> Unit>()

  fun row(content: @Composable () -> Unit) {
    rows += content
  }
}

/**
 * A settings group: a section label in the primary colour, an optional notice line, and the rows
 * in one 16 dp rounded container with 1 dp dividers between them.
 */
@Composable
fun SettingsGroup(
  label: String?,
  modifier: Modifier = Modifier,
  notice: String? = null,
  build: SettingsGroupScope.() -> Unit,
) {
  val rows = SettingsGroupScope().apply(build).rows
  Column(modifier = modifier.fillMaxWidth()) {
    if (label != null) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
      )
    }
    if (notice != null) {
      Text(
        text = notice,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).testTag("slide-over-notice"),
      )
    }
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column {
        rows.forEachIndexed { index, row ->
          if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          row()
        }
      }
    }
  }
}

/**
 * The frame of every settings screen: a large title that collapses on scroll, a back arrow, and
 * one list centred in a column of at most 680 dp on wide windows. Content draws edge to edge and
 * the list keeps clear of the system bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  listState: LazyListState = rememberLazyListState(),
  actions: @Composable RowScope.() -> Unit = {},
  content: LazyListScope.() -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      LargeTopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
          }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
      )
    },
  ) { padding ->
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().padding(padding).testTag("settings-list"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      content = content,
    )
  }
}

/** One item of a settings list, as wide as the screen up to the 680 dp column. */
fun Modifier.settingsColumn(): Modifier = fillMaxWidth().widthIn(max = 680.dp)

@Preview
@Composable
private fun SettingsGroupPreview() {
  ChromerTheme {
    SettingsGroup(label = "Browsing mode", notice = "Not available during slide-over mode") {
      row { SettingsRow("Slide over", icon = RowIcon(Icons.Outlined.Layers), control = RowControl.Radio(true), onClick = {}) }
      row { SettingsRow("Web heads", control = RowControl.Radio(false), onClick = {}) }
    }
  }
}
