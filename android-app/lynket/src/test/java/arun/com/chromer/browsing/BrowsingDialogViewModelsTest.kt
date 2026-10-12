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
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import arun.com.chromer.browsing.amp.AmpResolverViewModel
import arun.com.chromer.browsing.amp.AmpState
import arun.com.chromer.browsing.newtab.NewTabViewModel
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.MainDispatcherRule
import arun.com.chromer.search.provider.SearchProviders
import arun.com.chromer.search.suggestion.SuggestionsEngine
import arun.com.chromer.shortcuts.ShortcutCreatorViewModel
import arun.com.chromer.shortcuts.ShortcutState
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.network.NetworkMonitor
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The AMP lookup, New tab and shortcut view models (Robolectric for the Android types they carry). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrowsingDialogViewModelsTest {

  private val dispatcher = StandardTestDispatcher()

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(dispatcher)

  private val url = "https://www.theverge.com/2026/10/android"

  private class FakeNetwork(var online: Boolean) : NetworkMonitor {
    override val isOnline: Flow<Boolean> get() = MutableStateFlow(online)
    override fun isOnlineNow(): Boolean = online
  }

  private fun site(amp: String? = null) = Website("The Verge", url, null, null, null, amp, false, 0, 1)

  // AMP lookup

  @Test
  fun aPageWithAnAmpVersionIsFound() = runTest(dispatcher) {
    val repo = mockk<WebsiteRepository> { every { getWebsite(url) } returns flowOf(site(amp = "$url/amp")) }
    val vm = AmpResolverViewModel(repo, FakeNetwork(online = true))
    vm.start(url, incognito = false)
    assertThat(vm.state.value).isEqualTo(AmpState.Loading(url))
    advanceUntilIdle()
    assertThat((vm.state.value as AmpState.Found).website.ampUrl).isEqualTo("$url/amp")
  }

  @Test
  fun aPageWithoutAmpOnlineIsNotFound() = runTest(dispatcher) {
    val repo = mockk<WebsiteRepository> { every { getWebsite(url) } returns flowOf(site()) }
    val vm = AmpResolverViewModel(repo, FakeNetwork(online = true))
    vm.start(url, incognito = false)
    advanceUntilIdle()
    assertThat(vm.state.value).isInstanceOf(AmpState.NotFound::class.java)
  }

  @Test
  fun aLookupOfflineIsAnErrorAndTryAgainRunsItAgain() = runTest(dispatcher) {
    val network = FakeNetwork(online = false)
    var calls = 0
    val repo = mockk<WebsiteRepository> {
      every { getWebsite(url) } answers { calls++; if (network.online) flowOf(site(amp = "$url/amp")) else flowOf(site()) }
    }
    val vm = AmpResolverViewModel(repo, network)
    vm.start(url, incognito = false)
    advanceUntilIdle()
    assertThat(vm.state.value).isEqualTo(AmpState.Error(url))

    vm.retry()
    advanceUntilIdle()
    assertThat(vm.state.value).isEqualTo(AmpState.Error(url))

    network.online = true
    vm.retry()
    advanceUntilIdle()
    assertThat(vm.state.value).isInstanceOf(AmpState.Found::class.java)
    assertThat(calls).isEqualTo(3)
  }

  @Test
  fun aFailingLookupOnlineOpensTheNormalPage() = runTest(dispatcher) {
    val repo = mockk<WebsiteRepository> { every { getWebsite(url) } returns flow { throw IllegalStateException("parse") } }
    val vm = AmpResolverViewModel(repo, FakeNetwork(online = true))
    vm.start(url, incognito = false)
    advanceUntilIdle()
    assertThat((vm.state.value as AmpState.NotFound).website.url).isEqualTo(url)
  }

  @Test
  fun anIncognitoLookupDoesNotWriteHistory() = runTest(dispatcher) {
    val repo = mockk<WebsiteRepository> { every { getWebsiteReadOnly(url) } returns flowOf(site()) }
    val vm = AmpResolverViewModel(repo, FakeNetwork(online = true))
    vm.start(url, incognito = true)
    advanceUntilIdle()
    verify(exactly = 0) { repo.getWebsite(any()) }
    assertThat(vm.state.value).isInstanceOf(AmpState.NotFound::class.java)
  }

  @Test
  fun aSecondStartAfterRecreationKeepsTheResult() = runTest(dispatcher) {
    val repo = mockk<WebsiteRepository> { every { getWebsite(url) } returns flowOf(site()) }
    val vm = AmpResolverViewModel(repo, FakeNetwork(online = true))
    vm.start(url, incognito = false)
    advanceUntilIdle()
    vm.start(url, incognito = false)
    verify(exactly = 1) { repo.getWebsite(url) }
  }

  // New tab

  private fun newTab(tabs: TabsManager, state: SavedStateHandle = SavedStateHandle()): NewTabViewModel {
    val providers = mockk<SearchProviders> { every { selectedProvider } returns flowOf(SearchProviders.GOOGLE_SEARCH_PROVIDER) }
    return NewTabViewModel(state, mockk<SuggestionsEngine>(relaxed = true), providers, tabs)
  }

  @Test
  fun anAddressOpensAsANewTabFromTheApp() = runTest(dispatcher) {
    val tabs = mockk<TabsManager>(relaxed = true)
    val opened = slot<Website>()
    var closed = false
    newTab(tabs).submit(mockk<Context>(), "  https://example.org/  ") { closed = true }
    advanceUntilIdle()
    verify { tabs.openUrl(any(), capture(opened), fromApp = true, fromWebHeads = false, fromNewTab = true, fromAmp = false, incognito = false) }
    assertThat(opened.captured.url).isEqualTo("https://example.org/")
    assertThat(closed).isTrue()
  }

  @Test
  fun wordsOpenAsASearch() = runTest(dispatcher) {
    val tabs = mockk<TabsManager>(relaxed = true)
    val opened = slot<Website>()
    newTab(tabs).submit(mockk<Context>(), "compose navigation") {}
    advanceUntilIdle()
    verify { tabs.openUrl(any(), capture(opened), any(), any(), fromNewTab = true, any(), any()) }
    assertThat(opened.captured.url).isEqualTo(SearchProviders.GOOGLE_SEARCH_PROVIDER.getSearchUrl("compose navigation"))
  }

  @Test
  fun blankTextOpensNothingAndTheQuerySurvivesRecreation() = runTest(dispatcher) {
    val tabs = mockk<TabsManager>(relaxed = true)
    val state = SavedStateHandle()
    newTab(tabs, state).apply {
      submit(mockk<Context>(), "   ") {}
      onQueryChange("exam")
    }
    advanceUntilIdle()
    verify(exactly = 0) { tabs.openUrl(any(), any(), any(), any(), any(), any(), any()) }
    assertThat(newTab(tabs, state).query.value).isEqualTo("exam")
  }

  // Shortcut creator

  @Test
  fun aFailedFaviconStillGivesAnIconAndTheSiteName() = runTest(dispatcher) {
    val placeholder = mockk<Bitmap>()
    val repo = mockk<WebsiteRepository> {
      every { getWebsite(url) } returns flowOf(site())
      every { getWebsiteIconWithPlaceholderAndColor(any()) } returns android.util.Pair(placeholder, 0)
    }
    val vm = ShortcutCreatorViewModel(SavedStateHandle(), repo).apply { ioDispatcher = dispatcher }
    assertThat(vm.canCreate(vm.state.value, "x")).isFalse()
    vm.start(url)
    advanceUntilIdle()
    val ready = vm.state.value as ShortcutState.Ready
    assertThat(ready.icon).isSameInstanceAs(placeholder)
    assertThat(vm.name.value).isEqualTo("The Verge")
    assertThat(vm.canCreate(ready, vm.name.value)).isTrue()
    vm.onNameChange("  ")
    assertThat(vm.canCreate(ready, vm.name.value)).isFalse()
  }

  @Test
  fun aTypedNameIsKeptAcrossRecreation() = runTest(dispatcher) {
    val repo = mockk<WebsiteRepository> {
      every { getWebsite(url) } returns flowOf(site())
      every { getWebsiteIconWithPlaceholderAndColor(any()) } returns android.util.Pair(mockk<Bitmap>(), 0)
    }
    val state = SavedStateHandle()
    ShortcutCreatorViewModel(state, repo).onNameChange("Verge")
    val vm = ShortcutCreatorViewModel(state, repo).apply { ioDispatcher = dispatcher }
    vm.start(url)
    advanceUntilIdle()
    assertThat(vm.name.value).isEqualTo("Verge")
  }
}
