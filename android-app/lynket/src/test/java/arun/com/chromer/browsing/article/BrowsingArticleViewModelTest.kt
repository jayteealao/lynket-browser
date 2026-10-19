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

package arun.com.chromer.browsing.article

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import arun.com.chromer.data.webarticle.WebArticleRepository
import arun.com.chromer.data.webarticle.model.WebArticle
import arun.com.chromer.home.MainDispatcherRule
import arun.com.chromer.search.provider.SearchProvider
import arun.com.chromer.search.provider.SearchProviders
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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

/** The reader's view model: loading, content, error (never a silent close), retry, text size, keyword search. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrowsingArticleViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(dispatcher)

  private val url = "https://example.com/story"

  private val providers = mockk<SearchProviders> {
    every { selectedProvider } returns flowOf(SearchProvider("Duck Duck Go", Uri.EMPTY, "https://duckduckgo.com/?q="))
  }

  private fun article(body: String) = WebArticle(url).apply {
    title = "A story"
    siteName = "Example"
    keywords = mutableListOf("android", " ")
    elements = Jsoup.parse(body).body().children()
  }

  private fun vm(repo: WebArticleRepository, handle: SavedStateHandle = SavedStateHandle()) =
    BrowsingArticleViewModel(repo, providers, handle, dispatcher)

  @Test
  fun anArticleLoadsIntoContent() = runTest(dispatcher) {
    val repo = mockk<WebArticleRepository> { every { getWebArticle(url) } returns flowOf(article("<p>One</p><h2>Two</h2>")) }
    val vm = vm(repo)
    vm.load(url)
    assertThat(vm.state.value).isEqualTo(ReaderState.Loading)
    advanceUntilIdle()
    val content = (vm.state.value as ReaderState.Content).article
    assertThat(content.title).isEqualTo("A story")
    assertThat(content.keywords).containsExactly("android")
    assertThat(content.blocks).hasSize(2)
  }

  @Test
  fun noArticleIsTheErrorState() = runTest(dispatcher) {
    val repo = mockk<WebArticleRepository> { every { getWebArticle(url) } returns emptyFlow() }
    val vm = vm(repo)
    vm.load(url)
    advanceUntilIdle()
    assertThat(vm.state.value).isEqualTo(ReaderState.Error)
  }

  @Test
  fun anArticleWithoutTextIsTheErrorState() = runTest(dispatcher) {
    val repo = mockk<WebArticleRepository> { every { getWebArticle(url) } returns flowOf(article("<div><img src='a.png'></div>")) }
    val vm = vm(repo)
    vm.load(url)
    advanceUntilIdle()
    assertThat(vm.state.value).isEqualTo(ReaderState.Error)
  }

  @Test
  fun aFailingExtractionIsTheErrorStateAndRetryRunsItAgain() = runTest(dispatcher) {
    var calls = 0
    val repo = mockk<WebArticleRepository> {
      every { getWebArticle(url) } answers {
        calls++
        if (calls == 1) flow { throw IllegalStateException("parse") } else flowOf(article("<p>Back</p>"))
      }
    }
    val vm = vm(repo)
    vm.load(url)
    advanceUntilIdle()
    assertThat(vm.state.value).isEqualTo(ReaderState.Error)

    vm.retry()
    assertThat(vm.state.value).isEqualTo(ReaderState.Loading)
    advanceUntilIdle()
    assertThat(vm.state.value).isInstanceOf(ReaderState.Content::class.java)
    assertThat(calls).isEqualTo(2)
  }

  @Test
  fun loadingTheSameAddressAgainDoesNotExtractAgain() = runTest(dispatcher) {
    var calls = 0
    val repo = mockk<WebArticleRepository> { every { getWebArticle(url) } answers { calls++; flowOf(article("<p>One</p>")) } }
    val vm = vm(repo)
    vm.load(url)
    advanceUntilIdle()
    vm.load(url)
    advanceUntilIdle()
    assertThat(calls).isEqualTo(1)
  }

  @Test
  fun textSizeStartsFromTheSettingAndSurvivesRecreation() {
    val repo = mockk<WebArticleRepository>(relaxed = true)
    val handle = SavedStateHandle()
    val vm = vm(repo, handle)
    vm.initTextSize(4)
    assertThat(vm.textSizeIncrement.value).isEqualTo(4)
    vm.setTextSize(20)
    assertThat(vm.textSizeIncrement.value).isEqualTo(BrowsingArticleViewModel.MAX_TEXT_SIZE_INCREMENT)
    vm.setTextSize(7)

    val recreated = vm(repo, handle)
    recreated.initTextSize(4)
    assertThat(recreated.textSizeIncrement.value).isEqualTo(7)
  }

  @Test
  fun aKeywordIsSearchedWithTheSelectedProvider() = runTest(dispatcher) {
    val vm = vm(mockk(relaxed = true))
    assertThat(vm.searchUrl("large screens")).isEqualTo("https://duckduckgo.com/?q=large+screens")
  }
}
