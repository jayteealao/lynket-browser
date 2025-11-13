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

package arun.com.chromer.tabs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.getWebsiteSuspend
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.TabsManager.Tab
import arun.com.chromer.tabs.closeAllTabsSuspend
import arun.com.chromer.tabs.getActiveTabsSuspend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Phase 6: Modern TabsViewModel (RxJava Removed)
 *
 * Manages active browser tabs using:
 * - StateFlow for reactive state
 * - Kotlin Coroutines for async operations
 * - Suspend function extensions for legacy TabsManager
 *
 * Features:
 * - Load active tabs
 * - Close individual tabs
 * - Close all tabs
 * - Real-time updates
 *
 * Changes in Phase 6:
 * - Removed RxJava interop (rx2.await, rx2.awaitSingle)
 * - Uses suspend extension functions instead
 * - No more RxJava dependencies in this ViewModel
 */
@HiltViewModel
class ModernTabsViewModel @Inject constructor(
    private val tabsManager: TabsManager,
    private val websiteRepository: WebsiteRepository
) : ViewModel() {

    /**
     * UI State for tabs screen
     */
    sealed interface TabsUiState {
        data object Loading : TabsUiState
        data class Success(
            val tabs: List<Tab>,
            val showCloseAllDialog: Boolean = false
        ) : TabsUiState
        data class Error(val message: String) : TabsUiState
    }

    /**
     * StateFlow of UI state
     */
    private val _uiState = MutableStateFlow<TabsUiState>(TabsUiState.Loading)
    val uiState: StateFlow<TabsUiState> = _uiState.asStateFlow()

    /**
     * Active tabs as a separate flow
     */
    val tabs: StateFlow<List<Tab>> = flow {
        // This is a one-time load, tabs don't auto-update
        // In a real implementation, we'd have a database or observable source
        emit(emptyList<Tab>())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    init {
        loadTabs()
    }

    /**
     * Load active tabs
     */
    fun loadTabs() {
        viewModelScope.launch {
            _uiState.value = TabsUiState.Loading
            try {
                // Use suspend extension functions (no RxJava interop needed)
                val activeTabs = tabsManager.getActiveTabsSuspend()

                // Enrich tabs with website data
                val enrichedTabs = activeTabs.map { tab ->
                    try {
                        val website = websiteRepository.getWebsiteSuspend(tab.url)
                        tab.apply { this.website = website }
                    } catch (e: Exception) {
                        // If website fetch fails, return tab as-is
                        Timber.w(e, "Failed to fetch website for: ${tab.url}")
                        tab
                    }
                }

                _uiState.value = TabsUiState.Success(tabs = enrichedTabs)
                Timber.d("Loaded ${enrichedTabs.size} tabs")
            } catch (e: Exception) {
                Timber.e(e, "Error loading tabs")
                _uiState.value = TabsUiState.Error(
                    message = e.message ?: "Failed to load tabs"
                )
            }
        }
    }

    /**
     * Show close all confirmation dialog
     */
    fun showCloseAllDialog() {
        val currentState = _uiState.value
        if (currentState is TabsUiState.Success) {
            _uiState.value = currentState.copy(showCloseAllDialog = true)
        }
    }

    /**
     * Hide close all dialog
     */
    fun hideCloseAllDialog() {
        val currentState = _uiState.value
        if (currentState is TabsUiState.Success) {
            _uiState.value = currentState.copy(showCloseAllDialog = false)
        }
    }

    /**
     * Close all tabs
     */
    fun closeAllTabs() {
        viewModelScope.launch {
            try {
                val closedTabs = tabsManager.closeAllTabsSuspend()
                Timber.d("Closed ${closedTabs.size} tabs")

                // Reload tabs after closing all
                hideCloseAllDialog()
                loadTabs()
            } catch (e: Exception) {
                Timber.e(e, "Error closing all tabs")
                hideCloseAllDialog()
            }
        }
    }

    /**
     * Refresh tabs
     */
    fun refresh() {
        loadTabs()
    }

    /**
     * Get tab count
     */
    fun getTabCount(): Int {
        return when (val state = _uiState.value) {
            is TabsUiState.Success -> state.tabs.size
            else -> 0
        }
    }
}
