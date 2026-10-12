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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import arun.com.chromer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app that can handle the picker's intent. */
data class PickableApp(val component: ComponentName, val label: String, val icon: ImageBitmap?)

/** The apps that handle [intent], without Lynket itself, sorted by label. */
fun pickableApps(context: Context, intent: Intent): List<PickableApp> {
  val pm = context.packageManager
  return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    .filterNot { it.activityInfo.packageName.equals(context.packageName, ignoreCase = true) }
    .map { info ->
      PickableApp(
        component = ComponentName(info.activityInfo.packageName, info.activityInfo.name),
        label = info.loadLabel(pm).toString(),
        icon = runCatching { info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
      )
    }
    .sortedBy { it.label.lowercase() }
}

/**
 * The app picker of the secondary browser and favourite share rows: a sheet with a grid of the
 * apps that handle [intent], as the old picker sheet showed them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
  title: String,
  intent: Intent,
  onPick: (PickableApp) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var apps by remember { mutableStateOf<List<PickableApp>?>(null) }
  LaunchedEffect(intent) { apps = withContext(Dispatchers.IO) { pickableApps(context, intent) } }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier = Modifier.testTag("app-picker"),
  ) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
      val loaded = apps
      when {
        loaded == null -> Text(
          stringResource(R.string.loading),
          modifier = Modifier.padding(24.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        loaded.isEmpty() -> Text(
          stringResource(R.string.no_apps_for_choice),
          modifier = Modifier.padding(24.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> LazyVerticalGrid(
          columns = GridCells.Fixed(4),
          modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(loaded, key = { it.component.flattenToString() }) { app ->
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clickable(role = Role.Button) { onPick(app) }
                .padding(8.dp),
            ) {
              app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
              Text(
                app.label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
          }
        }
      }
    }
  }
}
