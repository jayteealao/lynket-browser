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

package arun.com.chromer.tabs.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.TabsManager.Tab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** State of the Tabs screen. */
sealed interface TabsUiState {
  data object Loading : TabsUiState
  data class Loaded(val tabs: List<Tab>, val confirmCloseAll: Boolean = false, val refreshing: Boolean = false) : TabsUiState
  data class Error(val message: String) : TabsUiState
}

/**
 * Tabs: the browsing tasks Lynket has open. A tap brings that task forward; it never opens the page
 * again. A swipe or the close icon finishes the task; close all asks first.
 */
@HiltViewModel
class ModernTabsViewModel @Inject constructor(
  private val tabsManager: TabsManager,
  private val websiteRepository: WebsiteRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow<TabsUiState>(TabsUiState.Loading)
  val uiState: StateFlow<TabsUiState> = _uiState.asStateFlow()

  /** Reads the open tasks, with the stored site of each page when there is one. */
  fun load() {
    _uiState.update { if (it is TabsUiState.Loaded) it.copy(refreshing = true) else it }
    viewModelScope.launch {
      try {
        val tabs = tabsManager.getActiveTabs().map { tab ->
          tab.website = try {
            websiteRepository.getWebsiteReadOnly(tab.url).first()
          } catch (e: Exception) {
            Timber.w(e, "No stored site for a tab")
            null
          }
          tab
        }
        _uiState.update { TabsUiState.Loaded(tabs, confirmCloseAll = (it as? TabsUiState.Loaded)?.confirmCloseAll == true && tabs.isNotEmpty()) }
      } catch (e: Exception) {
        Timber.e(e, "Error loading tabs")
        _uiState.value = TabsUiState.Error(e.message ?: "")
      }
    }
  }

  /** Brings the tab's task forward. When no task holds it any more, the list reloads; nothing opens. */
  fun open(context: Context, tab: Tab) {
    val found = tabsManager.reOrderTabByUrl(context, Website(tab.url), listOf(tab.getTargetActivityName()))
    if (!found) load()
  }

  fun close(context: Context, tab: Tab) {
    tabsManager.finishTabByUrl(context, Website(tab.url), listOf(tab.getTargetActivityName()))
    load()
  }

  fun requestCloseAll() = _uiState.update { if (it is TabsUiState.Loaded) it.copy(confirmCloseAll = true) else it }

  fun dismissCloseAll() = _uiState.update { if (it is TabsUiState.Loaded) it.copy(confirmCloseAll = false) else it }

  fun confirmCloseAll() {
    dismissCloseAll()
    viewModelScope.launch {
      try {
        tabsManager.closeAllTabs()
      } catch (e: Exception) {
        Timber.e(e, "Error closing all tabs")
      }
      load()
    }
  }
}
