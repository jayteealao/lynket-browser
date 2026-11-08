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

package arun.com.chromer.browsing

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.testing.viewModelScenario
import arun.com.chromer.browsing.article.BrowsingArticleViewModel
import arun.com.chromer.browsing.article.ReaderState
import arun.com.chromer.browsing.newtab.NewTabViewModel
import arun.com.chromer.data.webarticle.WebArticleRepository
import arun.com.chromer.data.webarticle.model.WebArticle
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.MainDispatcherRule
import arun.com.chromer.search.provider.SearchProvider
import arun.com.chromer.search.provider.SearchProviders
import arun.com.chromer.search.suggestion.SuggestionsEngine
import arun.com.chromer.shortcuts.ShortcutCreatorViewModel
import arun.com.chromer.shortcuts.ShortcutState
import arun.com.chromer.tabs.TabsManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The New tab, shortcut and reader view models keep what the person set when Android kills the
 * process and builds them again: [androidx.lifecycle.viewmodel.testing.ViewModelScenario.recreate]
 * saves the state, drops the instance and makes a new one from the saved state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ViewModelRecreateTest {

  private val dispatcher = StandardTestDispatcher()

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(dispatcher)

  private val url = "https://www.theverge.com/2026/10/android"

  private val providers = mockk<SearchProviders> {
    every { selectedProvider } returns flowOf(SearchProvider("Duck Duck Go", Uri.EMPTY, "https://duckduckgo.com/?q="))
  }

  @Test
  fun theTypedNewTabAddressSurvivesRecreation() {
    viewModelScenario {
      NewTabViewModel(createSavedStateHandle(), mockk<SuggestionsEngine>(relaxed = true), providers, mockk<TabsManager>(relaxed = true))
    }.use { scenario ->
      val first = scenario.viewModel
      first.onQueryChange("exam")

      scenario.recreate()

      assertThat(scenario.viewModel).isNotSameInstanceAs(first)
      assertThat(scenario.viewModel.query.value).isEqualTo("exam")
    }
  }

  @Test
  fun theTypedShortcutNameSurvivesRecreationAndTheSiteLoadsAgainWithoutReplacingIt() = runTest(dispatcher) {
    val repo = mockk<WebsiteRepository> {
      every { getWebsite(url) } returns flowOf(Website("The Verge", url, null, null, null, null, false, 0, 1))
      every { getWebsiteIconWithPlaceholderAndColor(any()) } returns android.util.Pair(mockk<Bitmap>(), 0)
    }
    viewModelScenario {
      ShortcutCreatorViewModel(createSavedStateHandle(), repo).apply { ioDispatcher = dispatcher }
    }.use { scenario ->
      val first = scenario.viewModel
      first.onNameChange("Verge")

      scenario.recreate()

      val vm = scenario.viewModel
      assertThat(vm).isNotSameInstanceAs(first)
      assertThat(vm.name.value).isEqualTo("Verge")
      // The page address comes back from the launching intent, so the new instance loads it again.
      vm.start(url)
      advanceUntilIdle()
      assertThat((vm.state.value as ShortcutState.Ready).website.url).isEqualTo(url)
      assertThat(vm.name.value).isEqualTo("Verge")
    }
  }

  @Test
  fun theReaderTextSizeSurvivesRecreationAndTheArticleLoadsAgain() = runTest(dispatcher) {
    val article = WebArticle(url).apply {
      title = "A story"
      siteName = "Example"
      elements = Jsoup.parse("<p>One</p>").body().children()
    }
    val repo = mockk<WebArticleRepository> { every { getWebArticle(url) } returns flowOf(article) }
    viewModelScenario {
      BrowsingArticleViewModel(repo, providers, createSavedStateHandle(), dispatcher)
    }.use { scenario ->
      val first = scenario.viewModel
      first.initTextSize(4)
      first.setTextSize(7)
      first.load(url)
      advanceUntilIdle()
      assertThat(first.state.value).isInstanceOf(ReaderState.Content::class.java)

      scenario.recreate()

      val vm = scenario.viewModel
      assertThat(vm).isNotSameInstanceAs(first)
      assertThat(vm.textSizeIncrement.value).isEqualTo(7)
      vm.initTextSize(4)
      assertThat(vm.textSizeIncrement.value).isEqualTo(7)
      // The article is not saved: a real recreation starts the reader over.
      assertThat(vm.state.value).isEqualTo(ReaderState.Loading)
    }
  }
}
