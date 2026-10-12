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

package arun.com.chromer.ui.providerselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.browsing.providerselection.ModernProviderSelectionViewModel
import arun.com.chromer.browsing.providerselection.ModernProviderSelectionViewModel.ProviderSelectionUiState
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.components.ErrorState
import arun.com.chromer.ui.components.LoadingList
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.settings.components.RowControl
import arun.com.chromer.ui.settings.components.RowIcon
import arun.com.chromer.ui.settings.components.SettingsGroup
import arun.com.chromer.ui.settings.components.SettingsRow
import arun.com.chromer.ui.settings.components.SettingsScaffold
import arun.com.chromer.ui.settings.components.settingsColumn
import arun.com.chromer.util.Utils
import coil.compose.AsyncImage

/**
 * Custom Tab provider: the System WebView first, then every known or installed provider, as on the
 * old screen. A provider opens its details with Use or Install; WebView asks first. With no
 * provider at all the screen offers WebView or Chrome.
 */
@Composable
fun ProviderSelectionScreen(
  navigator: LynketNavigator,
  viewModel: ModernProviderSelectionViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  ProviderSelectionContent(
    state = state,
    onBack = navigator::back,
    onUseProvider = { provider ->
      viewModel.selectProvider(provider)
      navigator.back()
    },
    onInstall = { provider -> Utils.openPlayStore(context, provider.packageName) },
    onUseWebView = {
      viewModel.selectWebView()
      navigator.back()
    },
    onGetChrome = { Utils.openPlayStore(context, Constants.CHROME_PACKAGE) },
    onRetry = viewModel::refresh,
  )
}

/** The stateless screen, for tests and goldens. */
@Composable
fun ProviderSelectionContent(
  state: ProviderSelectionUiState,
  onBack: () -> Unit,
  onUseProvider: (Provider) -> Unit,
  onInstall: (Provider) -> Unit,
  onUseWebView: () -> Unit,
  onGetChrome: () -> Unit,
  onRetry: () -> Unit,
) {
  var detailsFor by rememberSaveable { mutableStateOf<String?>(null) }
  var askWebView by rememberSaveable { mutableStateOf(false) }

  SettingsScaffold(title = stringResource(R.string.custom_tab_provider), onBack = onBack) {
    when (state) {
      ProviderSelectionUiState.Loading -> item("loading") { LoadingList(Modifier.settingsColumn()) }
      is ProviderSelectionUiState.Error -> item("error") {
        ErrorState(
          title = stringResource(R.string.no_provider_title),
          body = state.message,
          retryLabel = stringResource(R.string.retry),
          onRetry = onRetry,
          modifier = Modifier.settingsColumn(),
        )
      }
      is ProviderSelectionUiState.NoProvider -> item("no-provider") {
        ErrorState(
          title = stringResource(R.string.no_provider_title),
          body = stringResource(R.string.no_provider_body),
          retryLabel = stringResource(R.string.use_webview),
          onRetry = onUseWebView,
          actionLabel = stringResource(R.string.get_chrome),
          onAction = onGetChrome,
          modifier = Modifier.settingsColumn().testTag("no-provider"),
        )
      }
      is ProviderSelectionUiState.Success -> providerList(
        state = state,
        onWebView = { askWebView = true },
        onProvider = { detailsFor = it.packageName },
      )
    }
  }

  val providers = (state as? ProviderSelectionUiState.Success)?.providers.orEmpty()
  providers.firstOrNull { it.packageName == detailsFor }?.let { provider ->
    ProviderDialog(
      provider = provider,
      onConfirm = {
        detailsFor = null
        if (provider.installed) onUseProvider(provider) else onInstall(provider)
      },
      onDismiss = { detailsFor = null },
    )
  }
  if (askWebView) {
    AlertDialog(
      onDismissRequest = { askWebView = false },
      title = { Text(stringResource(R.string.are_you_sure)) },
      text = { Text(stringResource(R.string.webview_disadvantages)) },
      confirmButton = {
        TextButton(onClick = {
          askWebView = false
          onUseWebView()
        }) { Text(stringResource(android.R.string.yes)) }
      },
      dismissButton = { TextButton(onClick = { askWebView = false }) { Text(stringResource(android.R.string.cancel)) } },
      modifier = Modifier.testTag("webview-dialog"),
    )
  }
}

private fun LazyListScope.providerList(
  state: ProviderSelectionUiState.Success,
  onWebView: () -> Unit,
  onProvider: (Provider) -> Unit,
) {
  item("providers") {
    SettingsGroup(label = stringResource(R.string.tab_providers), modifier = Modifier.settingsColumn()) {
      row {
        SettingsRow(
          title = stringResource(R.string.system_webview),
          summary = androidx.compose.ui.text.AnnotatedString(stringResource(R.string.webViewPros)),
          icon = RowIcon(Icons.Outlined.Public),
          control = RowControl.Radio(state.usingWebView),
          modifier = Modifier.testTag("provider-webview"),
          onClick = onWebView,
        )
      }
      state.providers.forEach { provider ->
        row {
          ProviderRow(
            provider = provider,
            selected = !state.usingWebView && provider.packageName == state.selectedPackage,
            onClick = { onProvider(provider) },
          )
        }
      }
    }
  }
}

@Composable
private fun ProviderRow(provider: Provider, selected: Boolean, onClick: () -> Unit) {
  val status = when {
    selected -> R.string.provider_current
    provider.installed -> R.string.provider_installed
    else -> R.string.provider_not_installed
  }
  Row(
    modifier = Modifier
      .heightIn(min = 56.dp)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .testTag("provider-${provider.packageName}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    AsyncImage(model = provider.iconUri, contentDescription = null, modifier = Modifier.size(40.dp))
    Column(Modifier.weight(1f)) {
      Text(provider.appName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
      Text(
        stringResource(status),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (provider.installed) {
      RadioButton(selected = selected, onClick = null)
    } else {
      Text(stringResource(R.string.install), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
  }
}

/** The old provider details: icon, features when known, and Use or Install. */
@Composable
private fun ProviderDialog(provider: Provider, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { AsyncImage(model = provider.iconUri, contentDescription = null, modifier = Modifier.size(48.dp)) },
    title = { Text(provider.appName) },
    text = if (provider.features.isNotEmpty()) {
      {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.features), style = MaterialTheme.typography.titleSmall)
          Text(provider.features.toString())
        }
      }
    } else null,
    confirmButton = {
      TextButton(onClick = onConfirm, modifier = Modifier.testTag("provider-confirm")) {
        Text(stringResource(if (provider.installed) R.string.use else R.string.install))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    modifier = Modifier.testTag("provider-dialog"),
  )
}
