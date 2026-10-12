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

package arun.com.chromer.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Shop
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import arun.com.chromer.BuildConfig
import arun.com.chromer.R
import arun.com.chromer.shared.Constants
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.navigation.navigateToChangelog
import timber.log.Timber

/** One About row: a title, an optional line under it, and where a tap goes (null: no action). */
data class AboutRow(
  val title: String,
  val subtitle: String? = null,
  val icon: ImageVector? = null,
  @DrawableRes val image: Int? = null,
  val link: String? = null,
  val opensChangelog: Boolean = false,
)

data class AboutSection(val title: String, val rows: List<AboutRow>)

/** The rows of the old About screen, in its order: the app, the author, the credits. */
@Composable
fun aboutSections(): List<AboutSection> = listOf(
  AboutSection(
    stringResource(R.string.app_name),
    listOf(
      AboutRow(stringResource(R.string.version), BuildConfig.VERSION_NAME, Icons.Outlined.Info),
      AboutRow(stringResource(R.string.changelog), stringResource(R.string.see_whats_new), Icons.Outlined.ShowChart, opensChangelog = true),
      AboutRow(stringResource(R.string.follow_twitter), icon = Icons.Outlined.AlternateEmail, link = "https://twitter.com/LynketApp"),
      AboutRow(stringResource(R.string.discuss_on_reddit), icon = Icons.Outlined.Forum, link = "https://www.reddit.com/r/lynket/"),
      AboutRow(
        stringResource(R.string.licenses),
        icon = Icons.Outlined.Badge,
        link = "https://htmlpreview.github.io/?https://github.com/arunkumar9t2/lynket-browser/blob/main/notices.html",
      ),
      AboutRow(
        stringResource(R.string.translations),
        stringResource(R.string.help_translations),
        Icons.Outlined.Translate,
        link = "http://os0l2aw.oneskyapp.com/collaboration/project/62112",
      ),
      AboutRow(
        stringResource(R.string.source),
        stringResource(R.string.contribute_to_chromer),
        Icons.Outlined.AccountTree,
        link = "https://github.com/arunkumar9t2/lynket-browser",
      ),
    ),
  ),
  AboutSection(
    stringResource(R.string.author),
    listOf(
      AboutRow(Constants.ME, Constants.LOCATION, image = R.drawable.arun),
      AboutRow(stringResource(R.string.add_to_circles), icon = Icons.Outlined.AddCircleOutline, link = "http://google.com/+arunkumar5592"),
      AboutRow(stringResource(R.string.follow_twitter), icon = Icons.Outlined.AlternateEmail, link = "https://twitter.com/arunkumar_9t2"),
      AboutRow(stringResource(R.string.connect_linkedIn), icon = Icons.Outlined.Work, link = "http://in.linkedin.com/in/arunkumar9t2"),
      AboutRow(stringResource(R.string.fork_on_github), icon = Icons.Outlined.Code, link = "https://github.com/arunkumar9t2/"),
      AboutRow(
        stringResource(R.string.more_apps),
        icon = Icons.Outlined.Shop,
        link = "https://play.google.com/store/apps/dev?id=9082544673727889961",
      ),
    ),
  ),
  AboutSection(
    stringResource(R.string.credits),
    listOf(
      AboutRow("Patryk Goworowski", stringResource(R.string.icon_design), Icons.Outlined.Palette, link = "https://plus.google.com/+PatrykGoworowski"),
      AboutRow(
        "Max Patchs",
        stringResource(R.string.illustrations_and_video),
        Icons.Outlined.Brush,
        link = "https://plus.google.com/+Windows10-tutorialsBlogspot",
      ),
      AboutRow(
        stringResource(R.string.beta_testers),
        icon = Icons.Outlined.Groups,
        link = "https://plus.google.com/communities/109754631011301174504",
      ),
    ),
  ),
)

@Composable
fun AboutScreen(navigator: LynketNavigator) {
  val context = LocalContext.current
  AboutContent(
    onBack = { navigator.back() },
    onRow = { row ->
      when {
        row.opensChangelog -> navigator.navigateToChangelog()
        row.link != null -> openLink(context, row.link)
      }
    },
  )
}

private fun openLink(context: Context, link: String) {
  try {
    context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
  } catch (e: ActivityNotFoundException) {
    Timber.e(e, "No app for %s", link)
  }
}

/** About, stateless: a large title under a small top bar, then one grouped card per section. */
@Composable
fun AboutContent(onBack: () -> Unit, onRow: (AboutRow) -> Unit) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
  val sections = aboutSections()
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).testTag("about"),
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      LargeTopAppBar(
        title = { Text(stringResource(R.string.about), fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      sections.forEach { section ->
        item(key = section.title) { AboutSectionCard(section, onRow) }
      }
    }
  }
}

@Composable
private fun AboutSectionCard(section: AboutSection, onRow: (AboutRow) -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column {
      Text(
        text = section.title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
      )
      section.rows.forEachIndexed { index, row ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 56.dp))
        ListItem(
          headlineContent = { Text(row.title) },
          supportingContent = row.subtitle?.let { { Text(it) } },
          leadingContent = {
            when {
              row.image != null -> Image(
                painter = painterResource(row.image),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
              )
              row.icon != null -> Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
          },
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          modifier = if (row.link != null || row.opensChangelog) {
            Modifier.clickable { onRow(row) }
          } else {
            Modifier
          },
        )
      }
    }
  }
}
