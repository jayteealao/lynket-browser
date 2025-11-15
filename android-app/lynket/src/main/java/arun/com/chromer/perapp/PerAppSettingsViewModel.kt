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

package arun.com.chromer.perapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.apps.AppRepository
import arun.com.chromer.data.common.App
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Per-App Settings screen.
 *
 * Migrated to Hilt: Uses @HiltViewModel annotation for automatic ViewModel injection.
 * Converted to Kotlin Coroutines and StateFlow.
 *
 * Manages per-app settings for blacklist and incognito mode.
 */
@HiltViewModel
class PerAppSettingsViewModel
@Inject
constructor(private val appRepository: AppRepository) : ViewModel() {

  private val _loadingState = MutableStateFlow(false)
  val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

  private val _appsState = MutableStateFlow<List<App>>(emptyList())
  val appsState: StateFlow<List<App>> = _appsState.asStateFlow()

  private val _appUpdateChannel = Channel<Pair<Int, App>>(Channel.BUFFERED)
  val appUpdateFlow = _appUpdateChannel.receiveAsFlow()

  private val operationMutex = Mutex()

  fun loadApps() {
    viewModelScope.launch {
      try {
        _loadingState.value = true
        val apps = appRepository.allApps()
        Timber.d("Apps loaded ${apps.size}")
        _appsState.value = apps
        _loadingState.value = false
      } catch (e: Exception) {
        _loadingState.value = false
        Timber.e(e)
      }
    }
  }

  fun incognito(selections: Pair<String, Boolean>) {
    viewModelScope.launch {
      operationMutex.withLock {
        if (_loadingState.value) return@launch

        try {
          _loadingState.value = true
          val (packageName, incognito) = selections

          val app = if (incognito) {
            appRepository.setPackageIncognito(packageName)
          } else {
            appRepository.removeIncognito(packageName)
          }

          val index = _appsState.value.indexOfFirst { it.packageName == app.packageName }
          _appUpdateChannel.send(Pair(index, app))
          _loadingState.value = false
        } catch (e: Exception) {
          _loadingState.value = false
          Timber.e(e)
        }
      }
    }
  }

  fun blacklist(selections: Pair<String, Boolean>) {
    viewModelScope.launch {
      operationMutex.withLock {
        if (_loadingState.value) return@launch

        try {
          _loadingState.value = true
          val (packageName, blacklisted) = selections

          val app = if (blacklisted) {
            appRepository.setPackageBlacklisted(packageName)
          } else {
            appRepository.removeBlacklist(packageName)
          }

          val index = _appsState.value.indexOfFirst { it.packageName == app.packageName }
          _appUpdateChannel.send(Pair(index, app))
          _loadingState.value = false
        } catch (e: Exception) {
          _loadingState.value = false
          Timber.e(e)
        }
      }
    }
  }
}

