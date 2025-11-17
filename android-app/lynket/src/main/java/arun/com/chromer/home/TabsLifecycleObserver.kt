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

// Phase 8: Converted from RxJava to Kotlin Coroutines
package arun.com.chromer.home

import androidx.lifecycle.LifecycleOwner
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.lifecycle.ActivityLifecycle
import arun.com.chromer.util.lifecycle.LifecycleEvents
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@ActivityScoped
class TabsLifecycleObserver
@Inject
constructor(
  @ActivityLifecycle
  lifecycleOwner: LifecycleOwner,
  private val tabsManager: TabsManager,
  private val websiteRepository: WebsiteRepository
) : LifecycleEvents(lifecycleOwner) {
  fun activeTabs(): Flow<List<TabsManager.Tab>> = starts.flatMapLatest {
    flow {
      // Emit immediately, then every 750ms
      emit(tabsManager.getActiveTabs())
      while (true) {
        delay(750)
        emit(tabsManager.getActiveTabs())
      }
    }
      .distinctUntilChanged()
      .flatMapLatest { tabs ->
        flow {
          // Emit raw tabs first
          emit(tabs)

          // Process and enrich tabs with website data
          val enrichedTabs = tabs.map { tab ->
            val website = websiteRepository.getWebsiteReadOnly(tab.url).firstOrNull()
            tab.copy(website = website)
          }.sortedBy { it.website?.createdAt ?: 0 }

          // Emit enriched tabs
          emit(enrichedTabs)
        }.debounce(200)
      }
      .flowOn(Dispatchers.IO)
  }
}
