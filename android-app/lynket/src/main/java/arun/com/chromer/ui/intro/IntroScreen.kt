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

package arun.com.chromer.ui.intro

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.components.StateBlobShape
import arun.com.chromer.ui.navigation.LynketKey
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.navigation.navigateToProviderSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The action button a page carries, as the old intro pages had them. */
enum class IntroAction(@StringRes val label: Int) {
  TrySlideOver(R.string.try_it),
  ChooseProvider(R.string.choose_provider),
  WatchDemo(R.string.see_demo),
  TryArticle(R.string.try_it),
}

/** One intro page. [title] is a string resource unless [titleText] is given (the app name page). */
data class IntroPage(
  @StringRes val title: Int,
  @StringRes val body: Int,
  @DrawableRes val image: Int,
  val action: IntroAction? = null,
  val isDefaultBrowserPage: Boolean = false,
)

/** The old intro's eight pages in their order, then the default-browser page of the first-run board. */
val IntroPages: List<IntroPage> = listOf(
  IntroPage(R.string.app_name, R.string.intro_1, R.drawable.chromer_hd_icon),
  IntroPage(R.string.browse_seamlessly, R.string.slide_over_explanation, R.drawable.chromer_hd_icon, IntroAction.TrySlideOver),
  IntroPage(R.string.chromer_flexible, R.string.provider_intro_explanation, R.drawable.tutorial_choose_browser, IntroAction.ChooseProvider),
  IntroPage(R.string.web_heads, R.string.intro_4_new, R.drawable.tutorial_web_heads, IntroAction.WatchDemo),
  IntroPage(R.string.amp, R.string.tutorial_amp_intro, R.drawable.tutorial_amp_mode),
  IntroPage(R.string.article_mode, R.string.article_mode_summary, R.drawable.tutorial_article_mode, IntroAction.TryArticle),
  IntroPage(R.string.merge_tabs, R.string.merge_tabs_explanation_intro, R.drawable.tutorial_merge_tabs_and_apps),
  IntroPage(R.string.per_app_settings, R.string.per_app_settings_explanation, R.drawable.tutorial_per_app_settings),
  IntroPage(
    R.string.default_browser_title,
    R.string.default_browser_body,
    R.drawable.chromer_hd_icon,
    isDefaultBrowserPage = true,
  ),
)

@HiltViewModel
class IntroViewModel @Inject constructor(private val tabsManager: TabsManager) : ViewModel() {
  fun trySlideOver(context: Context) {
    tabsManager.openBrowsingTab(context, Website("https://goo.gl/search/lynket"), smart = false, fromNewTab = false)
    Toast.makeText(context, R.string.slide_over_fragment_close_prompt, Toast.LENGTH_SHORT).show()
  }

  fun tryArticle(context: Context) {
    tabsManager.openArticle(context, Website("https://en.wikipedia.org/wiki/Web_browser"))
  }
}

/** The first-run intro destination. Skip, Done and Later close it; back goes to the previous page. */
@Composable
fun IntroScreen(navigator: LynketNavigator, viewModel: IntroViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val close = { navigator.remove(LynketKey.Intro) }
  val requestDefaultBrowser = rememberDefaultBrowserRequest(onDone = close)
  IntroContent(
    onClose = close,
    onAction = { action ->
      when (action) {
        IntroAction.TrySlideOver -> viewModel.trySlideOver(context)
        IntroAction.ChooseProvider -> navigator.navigateToProviderSelection()
        IntroAction.WatchDemo -> context.startActivity(
          Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=3gbz8PI8BVI&feature=youtu.be".toUri()),
        )
        IntroAction.TryArticle -> viewModel.tryArticle(context)
      }
    },
    onSetDefaultBrowser = requestDefaultBrowser,
  )
}

@Composable
fun IntroContent(
  onClose: () -> Unit,
  onAction: (IntroAction) -> Unit,
  onSetDefaultBrowser: () -> Unit,
  pagerState: PagerState = rememberPagerState { IntroPages.size },
) {
  val scope = rememberCoroutineScope()
  // AppIntro went back one page per back press and closed on the first page.
  BackHandler(enabled = pagerState.currentPage > 0) {
    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
  }
  Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize().testTag("intro")) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
        IntroPageContent(
          page = IntroPages[index],
          onAction = onAction,
          onSetDefaultBrowser = onSetDefaultBrowser,
          onLater = onClose,
        )
      }
      val last = pagerState.currentPage == IntroPages.lastIndex
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.widthIn(min = 96.dp)) {
          if (!last) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("intro-skip")) {
              Text(stringResource(R.string.skip))
            }
          }
        }
        PageDots(pagerState.currentPage, IntroPages.size, Modifier.weight(1f))
        // On the last page Set as default is the one filled button; Done is a text button there.
        Box(Modifier.widthIn(min = 96.dp), contentAlignment = Alignment.CenterEnd) {
          if (last) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("intro-done")) {
              Text(stringResource(R.string.done))
            }
          } else {
            Button(
              onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
              modifier = Modifier.heightIn(min = 48.dp).testTag("intro-next"),
            ) {
              Text(stringResource(R.string.intro_next))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier) {
  val description = stringResource(R.string.intro_page_indicator, current + 1, count)
  Row(
    modifier = modifier.semantics { contentDescription = description },
    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(count) { index ->
      val selected = index == current
      Box(
        Modifier
          .height(8.dp)
          .width(if (selected) 24.dp else 8.dp)
          .background(
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            CircleShape,
          ),
      )
    }
  }
}

@Composable
private fun IntroPageContent(
  page: IntroPage,
  onAction: (IntroAction) -> Unit,
  onSetDefaultBrowser: () -> Unit,
  onLater: () -> Unit,
) {
  val body = stringResource(page.body)
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(32.dp))
    Box(
      modifier = Modifier
        .size(240.dp)
        .background(MaterialTheme.colorScheme.primaryContainer, StateBlobShape),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        painter = painterResource(page.image),
        contentDescription = null,
        modifier = Modifier.size(176.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
      )
    }
    Spacer(Modifier.height(32.dp))
    Column(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = stringResource(page.title),
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = if ('<' in body) runCatching { AnnotatedString.fromHtml(body) }.getOrElse { AnnotatedString(body) } else AnnotatedString(body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (page.action != null) {
        OutlinedButton(onClick = { onAction(page.action) }, modifier = Modifier.heightIn(min = 48.dp)) {
          Text(stringResource(page.action.label))
        }
      }
      if (page.isDefaultBrowserPage) {
        Button(
          onClick = onSetDefaultBrowser,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("intro-set-default"),
        ) {
          Text(stringResource(R.string.default_browser_set))
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
          Text(stringResource(R.string.later))
        }
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}
