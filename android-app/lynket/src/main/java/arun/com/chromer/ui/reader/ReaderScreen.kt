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

package arun.com.chromer.ui.reader

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arun.com.chromer.R
import arun.com.chromer.browsing.article.BrowsingArticleViewModel
import arun.com.chromer.browsing.article.ReaderArticle
import arun.com.chromer.browsing.article.ReaderState
import arun.com.chromer.browsing.menu.BrowsingMenuItem
import arun.com.chromer.ui.browsing.BrowsingBottomBar
import arun.com.chromer.ui.browsing.BrowsingTopBarActions
import arun.com.chromer.ui.components.ErrorState
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import arun.com.chromer.ui.browsing.rememberHideOnScrollState
import arun.com.chromer.ui.components.skeletonAlpha
import arun.com.chromer.ui.components.skeletonPulse
import arun.com.chromer.ui.theme.readableContentColor
import coil3.compose.AsyncImage

/** Widest the reader column grows on medium and expanded windows (visual contract). */
val ReaderMaxWidth = 680.dp

/**
 * The article reader: a top bar with Close and the browsing actions, then the header image, the
 * title, the site name, the keyword chips and the article blocks; the text-size card and the
 * bottom bar at the bottom. Loading shows a skeleton, a page with no article the error state with
 * Retry and Open in browser. On medium and expanded windows the column is centred over a scrim
 * that closes the reader on tap. Dragging past the top or bottom edge closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
  state: ReaderState,
  menuItems: List<BrowsingMenuItem>,
  bottomBarItems: List<BrowsingMenuItem>,
  textSizeIncrement: Int,
  savedTextSizeIncrement: Int,
  accent: Color,
  onClose: () -> Unit,
  onMenuItem: (BrowsingMenuItem) -> Unit,
  onRetry: () -> Unit,
  onOpenInBrowser: () -> Unit,
  onImage: (String) -> Unit,
  onKeyword: (String) -> Unit,
  onTextSizeChange: (Int) -> Unit,
  onSaveTextSize: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var textSizeOpen by rememberSaveable { mutableStateOf(false) }
  var askSave by rememberSaveable { mutableStateOf(false) }
  val drag = rememberReaderDragDismissState(onClose)

  BoxWithConstraints(modifier.fillMaxSize().testTag("reader")) {
    val wide = maxWidth >= 600.dp
    if (wide) {
      Box(
        Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
          .testTag("reader-scrim"),
      )
    }
    val topBarBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Surface(
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .widthIn(max = ReaderMaxWidth)
        .fillMaxWidth()
        .fillMaxHeight()
        .graphicsLayer { translationY = drag.offset }
        .testTag("reader-column"),
    ) {
      Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
          TopAppBar(
            title = {},
            navigationIcon = {
              IconButton(onClick = onClose, modifier = Modifier.testTag("reader-close")) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
              }
            },
            actions = {
              BrowsingTopBarActions(menuItems, MaterialTheme.colorScheme.onSurfaceVariant) { item ->
                if (item.id == R.id.menu_text_size) textSizeOpen = true else onMenuItem(item)
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
              scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
            scrollBehavior = topBarBehavior,
            modifier = Modifier.testTag("reader-top-bar"),
          )
        },
      ) { padding ->
        val gutter = if (wide) 24.dp else 20.dp
        val density = LocalDensity.current
        val bottomHide = rememberHideOnScrollState()
        // Opening the text size card brings the bars back with a slide, not a jump.
        LaunchedEffect(textSizeOpen) { if (textSizeOpen) bottomHide.animateShow() }
        var bottomHeight by remember { mutableIntStateOf(0) }
        Box(Modifier.fillMaxSize()) {
          when (state) {
            ReaderState.Loading -> ReaderSkeleton(Modifier.padding(padding).padding(horizontal = gutter))
            ReaderState.Error -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
              ErrorState(
                title = stringResource(R.string.reader_error_title),
                body = stringResource(R.string.reader_error_body),
                icon = Icons.AutoMirrored.Outlined.Article,
                retryLabel = stringResource(R.string.retry),
                onRetry = onRetry,
                actionLabel = stringResource(R.string.reader_open_in_browser),
                onAction = onOpenInBrowser,
                modifier = Modifier.padding(top = 48.dp),
              )
            }
            is ReaderState.Content -> ArticleContent(
              article = state.article,
              increment = textSizeIncrement,
              accent = accent,
              top = padding.calculateTopPadding(),
              bottom = with(density) { bottomHeight.toDp() } + padding.calculateBottomPadding(),
              gutter = gutter,
              onImage = onImage,
              onKeyword = onKeyword,
              modifier = Modifier
                .nestedScroll(topBarBehavior.nestedScrollConnection)
                .nestedScroll(bottomHide.connection)
                .nestedScroll(drag.connection),
            )
          }
          // The text-size card and the bottom bar sit over the article's end and slide away while it
          // scrolls down, as the legacy bottom bar did.
          Column(
            Modifier
              .align(Alignment.BottomCenter)
              .onSizeChanged {
                bottomHeight = it.height
                bottomHide.limit = it.height.toFloat()
              }
              .graphicsLayer { translationY = -bottomHide.offset },
          ) {
            if (textSizeOpen) {
              TextSizeCard(
                increment = textSizeIncrement,
                onChange = onTextSizeChange,
                onDismiss = {
                  textSizeOpen = false
                  if (textSizeIncrement != savedTextSizeIncrement) askSave = true
                },
              )
            }
            BrowsingBottomBar(
              bottomBarItems,
              containerColour = MaterialTheme.colorScheme.surfaceContainer,
              contentColour = MaterialTheme.colorScheme.onSurfaceVariant,
              onItem = onMenuItem,
            )
            if (bottomBarItems.isEmpty()) Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
          }
        }
      }
    }
  }

  if (askSave) {
    AlertDialog(
      onDismissRequest = { askSave = false },
      title = { Text(stringResource(R.string.save_size_dialog_title)) },
      text = { Text(stringResource(R.string.save_size_dialog_content)) },
      confirmButton = {
        TextButton(onClick = {
          askSave = false
          onSaveTextSize(textSizeIncrement)
        }, modifier = Modifier.testTag("save-size-yes")) { Text(stringResource(android.R.string.ok)) }
      },
      dismissButton = {
        TextButton(onClick = { askSave = false }, modifier = Modifier.testTag("save-size-no")) {
          Text(stringResource(android.R.string.cancel))
        }
      },
      modifier = Modifier.testTag("save-size-dialog"),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleContent(
  article: ReaderArticle,
  increment: Int,
  accent: Color,
  top: Dp,
  bottom: Dp,
  gutter: Dp,
  onImage: (String) -> Unit,
  onKeyword: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val bodyStyle = readerBodyStyle(MaterialTheme.typography.bodyLarge, increment)
  val measure = rememberReaderMeasure(bodyStyle)
  val titleStyle = MaterialTheme.typography.displaySmall.let {
    it.copy(fontWeight = FontWeight.SemiBold, fontSize = (it.fontSize.value + increment).sp, lineHeight = (it.lineHeight.value + increment).sp)
  }
  LazyColumn(
    state = rememberLazyListState(),
    contentPadding = PaddingValues(
      start = gutter,
      end = gutter,
      top = top + 8.dp,
      bottom = bottom + 48.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = modifier.fillMaxSize().testTag("article"),
  ) {
    if (article.imageUrl.isNotBlank()) {
      item(key = "header-image") {
        AsyncImage(
          model = article.imageUrl,
          contentDescription = article.title.ifBlank { stringResource(R.string.reader_article_image) },
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .heightIn(max = 288.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = stringResource(R.string.reader_open_image), role = Role.Button) { onImage(article.imageUrl) }
            .testTag("reader-header-image"),
        )
      }
    }
    if (article.title.isNotBlank() || article.siteName.isNotBlank()) {
      item(key = "title") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (article.title.isNotBlank()) {
            Text(article.title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("reader-title"))
          }
          if (article.siteName.isNotBlank()) {
            Text(
              article.siteName,
              style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 + increment).sp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("reader-site"),
            )
          }
        }
      }
    }
    if (article.keywords.isNotEmpty()) {
      item(key = "keywords") {
        val onAccent = accent.readableContentColor()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("reader-keywords")) {
          article.keywords.forEach { keyword ->
            SuggestionChip(
              onClick = { onKeyword(keyword) },
              label = { Text(keyword) },
              colors = SuggestionChipDefaults.suggestionChipColors(containerColor = accent, labelColor = onAccent),
              border = null,
              modifier = Modifier.testTag("keyword-$keyword"),
            )
          }
        }
      }
    }
    itemsIndexed(article.blocks) { _, block ->
      ArticleBlockView(block, bodyStyle, increment, accent, measure)
    }
  }
}

/** The text-size card above the bottom bar: icon, a slider from 0 to 15 sp extra, and close. */
@Composable
private fun TextSizeCard(increment: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("text-size-card"),
  ) {
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      val label = stringResource(R.string.text_size)
      val value = stringResource(R.string.text_size_state, increment)
      Icon(Icons.Outlined.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Slider(
        value = increment.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = 0f..BrowsingArticleViewModel.MAX_TEXT_SIZE_INCREMENT.toFloat(),
        steps = BrowsingArticleViewModel.MAX_TEXT_SIZE_INCREMENT - 1,
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 8.dp)
          .semantics {
            contentDescription = label
            stateDescription = value
          }
          .testTag("text-size-slider"),
      )
      IconButton(onClick = onDismiss, modifier = Modifier.testTag("text-size-close")) {
        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
      }
    }
  }
}

/** Loading: a header block, two title bars and six text lines with the shared pulse, then the label. */
@Composable
fun ReaderSkeleton(modifier: Modifier = Modifier) {
  val alpha = skeletonAlpha()
  val block = MaterialTheme.colorScheme.surfaceContainerHighest
  val label = stringResource(R.string.reader_loading)
  Column(
    modifier
      .fillMaxWidth()
      .padding(top = 8.dp)
      .semantics { contentDescription = label }
      .testTag("reader-loading"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(Modifier.skeletonPulse(alpha), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Box(Modifier.fillMaxWidth().aspectRatio(2f).background(block, MaterialTheme.shapes.large))
      Spacer(Modifier.height(8.dp))
      Box(Modifier.fillMaxWidth(0.9f).height(32.dp).background(block, MaterialTheme.shapes.small))
      Box(Modifier.fillMaxWidth(0.6f).height(32.dp).background(block, MaterialTheme.shapes.small))
      Spacer(Modifier.height(12.dp))
      listOf(1f, 0.96f, 1f, 0.88f, 1f, 0.7f).forEach { width ->
        Box(Modifier.fillMaxWidth(width).height(16.dp).background(block, MaterialTheme.shapes.small))
      }
    }
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
