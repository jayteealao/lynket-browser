// Phase 8: Converted from RxJava to Kotlin Coroutines
/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.tabs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.tabs.TabsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Legacy ViewModel for Tabs UI (XML-based).
 *
 * Migrated to Hilt: Uses @HiltViewModel annotation for automatic ViewModel injection.
 * Converted to Kotlin Coroutines and StateFlow.
 *
 * Note: Modern Compose UI uses ModernTabsViewModel instead.
 */
@HiltViewModel
class TabsViewModel
@Inject
constructor(
  private val tabsManager: TabsManager,
  private val websiteRepository: WebsiteRepository
) : ViewModel() {
  private val _loadingState = MutableStateFlow(false)
  val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

  private val _tabsData = MutableStateFlow<List<TabsManager.Tab>>(emptyList())
  val tabsData: StateFlow<List<TabsManager.Tab>> = _tabsData.asStateFlow()

  fun loadTabs() {
    viewModelScope.launch {
      try {
        _loadingState.value = true

        val activeTabs = try {
          tabsManager.getActiveTabs()
        } catch (e: Exception) {
          emptyList()
        }

        val tabsWithWebsites = activeTabs.map { tab ->
          val website = websiteRepository.getWebsiteReadOnly(tab.url)
          tab.apply {
            this.website = website
          }
        }

        _tabsData.value = tabsWithWebsites
        _loadingState.value = false
      } catch (e: Exception) {
        _loadingState.value = false
        Timber.e(e)
      }
    }
  }

  fun clearAllTabs() {
    viewModelScope.launch {
      try {
        tabsManager.closeAllTabs()
        loadTabs()
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }
}
