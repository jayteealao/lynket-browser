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

package arun.com.chromer.ui.tips

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.ui.navigation.LynketNavigator

/** One tip of the old Tips screen: its title, text, picture and icon. */
data class Tip(@StringRes val title: Int, @StringRes val body: Int, @DrawableRes val image: Int, val icon: ImageVector)

/** The old Tips screen's six tips in its order (every tip shows on minSdk 24). */
val Tips: List<Tip> = listOf(
  Tip(R.string.choose_provider, R.string.choose_provider_tip, R.drawable.tips_providers, Icons.Outlined.Style),
  Tip(R.string.choose_secondary_browser, R.string.tips_secondary_browser, R.drawable.tip_secondary_browser, Icons.Outlined.Public),
  Tip(R.string.per_app_settings, R.string.per_app_settings_explanation, R.drawable.tips_per_app_settings, Icons.Outlined.Apps),
  Tip(R.string.bottom_bar, R.string.tips_bottom_bar, R.drawable.tips_bottom_bar, Icons.Outlined.DragHandle),
  Tip(R.string.article_mode, R.string.tips_article_mode, R.drawable.tips_article_keywords, Icons.AutoMirrored.Outlined.Article),
  Tip(R.string.quick_settings, R.string.quick_settings_tip, R.drawable.tips_quick_settings, Icons.Outlined.Settings),
)

@Composable
fun TipsScreen(navigator: LynketNavigator) {
  TipsContent(onClose = { navigator.back() })
}

/** Tips, stateless: a large title under a small bar with a close icon, then one card per tip. */
@Composable
fun TipsContent(onClose: () -> Unit) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).testTag("tips"),
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      LargeTopAppBar(
        title = { Text(stringResource(R.string.tips), fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      items(Tips, key = { it.title }) { tip -> TipCard(tip) }
    }
  }
}

@Composable
private fun TipCard(tip: Tip) {
  Card(
    modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Box(
        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(tip.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(tip.title), style = MaterialTheme.typography.titleMedium)
        Text(
          stringResource(tip.body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Image(
          painter = painterResource(tip.image),
          contentDescription = null,
          contentScale = ContentScale.FillWidth,
          modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)),
        )
      }
    }
  }
}
