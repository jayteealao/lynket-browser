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

package arun.com.chromer.browsing.providerselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.apps.AppRepository
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.util.events.Event
import arun.com.chromer.util.events.EventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Provider selection: the System WebView and every known or installed Custom Tabs provider. The
 * rules are the old screen's: choosing a provider stores its package and turns WebView off;
 * choosing WebView turns WebView on and keeps the stored package. With no enabled provider the
 * screen shows the no-provider error state.
 */
@HiltViewModel
class ModernProviderSelectionViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val eventBus: EventBus
) : ViewModel() {

    sealed interface ProviderSelectionUiState {
        data object Loading : ProviderSelectionUiState

        data class Success(
            val providers: List<Provider>,
            val selectedPackage: String?,
            val usingWebView: Boolean
        ) : ProviderSelectionUiState

        /** No enabled package can render Custom Tabs. */
        data class NoProvider(val providers: List<Provider>, val usingWebView: Boolean) : ProviderSelectionUiState

        data class Error(val message: String) : ProviderSelectionUiState
    }

    private val _uiState = MutableStateFlow<ProviderSelectionUiState>(ProviderSelectionUiState.Loading)
    val uiState: StateFlow<ProviderSelectionUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            try {
                _uiState.value = ProviderSelectionUiState.Loading
                val providers = appRepository.allProviders()
                val preferences = preferencesRepository.userPreferencesFlow.first()
                _uiState.value = if (providers.none { it.installed }) {
                    ProviderSelectionUiState.NoProvider(providers, preferences.useWebView)
                } else {
                    ProviderSelectionUiState.Success(
                        providers = providers,
                        selectedPackage = preferences.preferredCustomTabPackage,
                        usingWebView = preferences.useWebView
                    )
                }
                Timber.d("Loaded ${providers.size} Custom Tab providers")
            } catch (e: Exception) {
                Timber.e(e, "Error loading providers")
                _uiState.value = ProviderSelectionUiState.Error(e.message ?: "Failed to load providers")
            }
        }
    }

    /** Stores [provider] as the Custom Tab provider and turns WebView off; not-installed ones are ignored. */
    fun selectProvider(provider: Provider) {
        if (!provider.installed) {
            Timber.w("Cannot select non-installed provider: ${provider.packageName}")
            return
        }
        viewModelScope.launch {
            try {
                // Stores the package and turns WebView off in one edit.
                preferencesRepository.setCustomTabPackage(provider.packageName)
                notifyProviderChanged()
                loadProviders()
            } catch (e: Exception) {
                Timber.e(e, "Error selecting provider")
            }
        }
    }

    /** Uses the System WebView; the stored provider package stays, as before. */
    fun selectWebView() {
        viewModelScope.launch {
            try {
                preferencesRepository.setUseWebView(true)
                notifyProviderChanged()
                loadProviders()
            } catch (e: Exception) {
                Timber.e(e, "Error selecting WebView")
            }
        }
    }

    private fun notifyProviderChanged() {
        eventBus.tryEmit(Event.SettingsEvent.ProviderChanged)
    }

    fun refresh() {
        loadProviders()
    }
}
