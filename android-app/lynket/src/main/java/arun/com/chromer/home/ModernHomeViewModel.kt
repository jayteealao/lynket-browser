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

package arun.com.chromer.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.R
import arun.com.chromer.browsing.customtabs.CustomTabs
import arun.com.chromer.data.history.ModernHistoryRepository
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.StringResource
import arun.com.chromer.extenstions.appName
import arun.com.chromer.search.provider.SearchProvider
import arun.com.chromer.search.provider.SearchProviders
import arun.com.chromer.search.suggestion.SuggestionsEngine
import arun.com.chromer.search.suggestion.items.SuggestionItem
import arun.com.chromer.search.suggestion.items.SuggestionType
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.appicon.ApplicationIcon
import arun.com.chromer.util.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * State and actions of the home: recents, the provider card, open tabs, the connection, and the
 * search bar's suggestions. Links open through [TabsManager.openUrl], the old home's path, so the
 * person's browsing mode (Custom Tab, web heads, web view) is honoured.
 */
@HiltViewModel
class ModernHomeViewModel @Inject constructor(
  private val application: Application,
  private val historyRepository: ModernHistoryRepository,
  private val preferencesRepository: UserPreferencesRepository,
  private val tabsManager: TabsManager,
  private val networkMonitor: NetworkMonitor,
  private val suggestionsEngine: SuggestionsEngine,
  private val searchProviders: SearchProviders,
) : ViewModel() {

  sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
      val recentWebsites: List<Website>,
      val providerInfo: CustomTabProviderInfo,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
  }

  /** Suggestions for the search bar, grouped as the old search view listed them. */
  data class Suggestions(
    val query: String = "",
    val copy: List<SuggestionItem> = emptyList(),
    val google: List<SuggestionItem> = emptyList(),
    val history: List<SuggestionItem> = emptyList(),
  ) {
    val all: List<SuggestionItem> get() = copy + google + history
  }

  /** Bumped by the provider card's Retry, so the installed browsers are looked up again. */
  private val providerScan = MutableStateFlow(0)

  /** Bumped by the recents card's Retry, so the recents are read again after an error. */
  private val recentsRetry = MutableStateFlow(0)

  /** The settings the provider card reads; other settings changes do not look the provider up again. */
  private data class ProviderSettings(val customTabPackage: String?, val fullIncognito: Boolean, val useWebView: Boolean)

  // The package manager lookups run once per change of the provider settings, not on every settings edit.
  private val providerCard = combine(
    preferencesRepository.userPreferencesFlow
      .map { ProviderSettings(it.preferredCustomTabPackage, it.fullIncognitoMode, it.useWebView) }
      .distinctUntilChanged(),
    providerScan,
  ) { settings, _ -> providerInfoFor(settings) }

  // One flat combine, so the first recents and the provider arrive in the same pass. The retry
  // counter restarts the whole read, so Retry recovers after an error.
  val uiState: StateFlow<HomeUiState> = recentsRetry
    .flatMapLatest {
      combine(historyRepository.getRecents(), providerCard) { recents, provider ->
        HomeUiState.Success(recentWebsites = recents, providerInfo = provider) as HomeUiState
      }
        .catch { exception ->
          Timber.e(exception, "Error loading home screen data")
          emit(HomeUiState.Error(exception.message ?: "Unknown error"))
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState.Loading)

  /** Open browsing tabs, read while the home is on screen (the old home polled every 750 ms). */
  val activeTabs: StateFlow<List<TabsManager.Tab>> = flow {
    while (true) {
      emit(runCatching { tabsManager.getActiveTabs() }.getOrElse { emptyList() })
      delay(ACTIVE_TABS_POLL_MS)
    }
  }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    .catch { emit(true) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

  private val query = MutableStateFlow("")

  val suggestions: StateFlow<Suggestions> = query
    .debounce(SUGGESTIONS_DEBOUNCE_MS)
    .flatMapLatest { text ->
      if (text.isBlank()) {
        flowOf(Suggestions(query = text))
      } else {
        with(suggestionsEngine) {
          flowOf(text)
            .suggestionsTransformer()
            .distinctSuggestionsPublish()
            .runningFold(Suggestions(query = text)) { acc, (type, items) ->
              when (type) {
                SuggestionType.COPY -> acc.copy(copy = items)
                SuggestionType.GOOGLE -> acc.copy(google = items)
                SuggestionType.HISTORY -> acc.copy(history = items)
              }
            }
        }
      }
    }
    .catch { e ->
      Timber.e(e, "Search suggestions failed")
      emit(Suggestions())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Suggestions())

  val searchEngines: List<SearchProvider> get() = searchProviders.availableProviders

  val selectedSearchEngine: StateFlow<SearchProvider> = searchProviders.selectedProvider
    .catch { emit(SearchProviders.GOOGLE_SEARCH_PROVIDER) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchProviders.GOOGLE_SEARCH_PROVIDER)

  fun onQueryChange(text: String) {
    query.value = text
  }

  fun selectSearchEngine(provider: SearchProvider) {
    searchProviders.select(provider)
  }

  /** Opens typed text: a web address as is, anything else as a search with the chosen engine. */
  fun submitSearch(context: Context, text: String) {
    if (text.isBlank()) return
    viewModelScope.launch {
      val url = runCatching { searchProviders.selectedProvider.first() }
        .getOrDefault(SearchProviders.GOOGLE_SEARCH_PROVIDER)
        .getSearchUrl(text.trim())
      openUrl(context, url)
    }
  }

  fun openUrl(context: Context, url: String) {
    tabsManager.openUrl(context, Website(url))
  }

  fun openWebsite(context: Context, website: Website) {
    tabsManager.openUrl(context, website)
  }

  fun showTabs() {
    tabsManager.showTabsActivity()
  }

  private fun webViewProviderInfo(incognito: Boolean, noProvider: Boolean = false) = CustomTabProviderInfo(
    iconUri = ApplicationIcon.createUri(Constants.SYSTEM_WEBVIEW),
    providerDescription = StringResource(
      R.string.tab_provider_status_message_home,
      resourceArgs = listOf(R.string.system_webview),
    ),
    providerReason = if (incognito) StringResource(R.string.provider_web_view_incognito_reason) else StringResource(0),
    allowChange = !incognito,
    noProviderInstalled = noProvider,
  )

  private fun providerInfoFor(settings: ProviderSettings): CustomTabProviderInfo {
    val customTabPackage = settings.customTabPackage
    // Full incognito forces the web view; incognito_mode is the History off switch.
    val isIncognito = settings.fullIncognito
    return when {
      isIncognito || settings.useWebView -> webViewProviderInfo(incognito = isIncognito)
      customTabPackage == null -> webViewProviderInfo(
        incognito = false,
        noProvider = !hasCustomTabProvider(),
      )
      else -> CustomTabProviderInfo(
        iconUri = ApplicationIcon.createUri(customTabPackage),
        providerDescription = StringResource(
          R.string.tab_provider_status_message_home,
          listOf(application.appName(customTabPackage)),
        ),
        providerReason = StringResource(0),
        allowChange = true,
      )
    }
  }

  private fun hasCustomTabProvider(): Boolean =
    runCatching { CustomTabs.getCustomTabSupportingPackages(application).isNotEmpty() }
      .getOrDefault(true)

  // ========== Actions ==========

  /** The recents card's Retry: reads the recents again. */
  fun refreshRecents() {
    recentsRetry.value++
  }

  /** The offline message's Retry: the home is read again; the screen shows the message again while offline. */
  fun retryConnection() {
    recentsRetry.value++
  }

  /** The provider card's Retry: picks a provider if one is now installed and looks again. */
  fun rescanProviders() {
    initializeDefaultProvider()
    providerScan.value++
  }

  fun deleteWebsite(website: Website) {
    viewModelScope.launch {
      val success = historyRepository.delete(website)
      if (!success) Timber.e("Failed to delete website: ${website.url}")
    }
  }

  fun clearAllHistory() {
    viewModelScope.launch {
      try {
        historyRepository.deleteAll()
      } catch (e: Exception) {
        Timber.e(e, "Error clearing history")
      }
    }
  }

  fun toggleBookmark(website: Website) {
    viewModelScope.launch {
      try {
        historyRepository.toggleBookmark(website.url)
      } catch (e: Exception) {
        Timber.e(e, "Error toggling bookmark")
      }
    }
  }

  fun setCustomTabProvider(packageName: String) {
    viewModelScope.launch {
      try {
        preferencesRepository.setCustomTabPackage(packageName)
      } catch (e: Exception) {
        Timber.e(e, "Error setting custom tab provider")
      }
    }
  }

  fun initializeDefaultProvider() {
    viewModelScope.launch {
      val prefs = preferencesRepository.userPreferencesFlow.first()
      if (prefs.preferredCustomTabPackage == null) {
        runCatching { withContext(Dispatchers.IO) { Preferences.findDefaultCustomTabApp(application) } }
          .onFailure { Timber.e(it, "Error getting default custom tab app") }
          .getOrNull()
          ?.let { setCustomTabProvider(it) }
      }
    }
  }

  private companion object {
    const val ACTIVE_TABS_POLL_MS = 750L
    const val SUGGESTIONS_DEBOUNCE_MS = 200L
  }
}
