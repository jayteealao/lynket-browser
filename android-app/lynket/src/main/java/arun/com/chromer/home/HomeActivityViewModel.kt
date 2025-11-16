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

package arun.com.chromer.home

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.R
import arun.com.chromer.data.history.HistoryRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.StringResource
import arun.com.chromer.extenstions.appName
import arun.com.chromer.home.epoxycontroller.model.CustomTabProviderInfo
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.RxPreferences
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.glide.appicon.ApplicationIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.arunkumar.android.common.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Legacy ViewModel for HomeActivity (XML-based UI).
 * Converted from RxJava to Kotlin Coroutines in Phase 8.
 * Uses @HiltViewModel for automatic Hilt injection.
 *
 * Note: Modern Compose UI uses ModernHomeViewModel instead.
 */
@SuppressLint("CheckResource")
@HiltViewModel
class HomeActivityViewModel
@Inject
constructor(
  private val application: Application,
  private val rxPreferences: RxPreferences,
  private val historyRepository: HistoryRepository,
  private val preferences: Preferences
) : ViewModel() {

  val providerInfoLiveData = MutableLiveData<CustomTabProviderInfo>()
  val recentsLiveData = MutableLiveData<Resource<List<Website>>>()

  init {
    start()
  }

  private fun start() {
    bindProviderInfo()
    bindRecentsInfo()
  }

  private fun bindRecentsInfo() {
    viewModelScope.launch {
      historyRepository.recents()
        .map { Resource.Success(it) as Resource<List<Website>> }
        .catch { e -> emit(Resource.Error(e)) }
        .flowOn(Dispatchers.IO)
        .collect { recentsLiveData.value = it }
    }
  }

  private fun bindProviderInfo() {
    viewModelScope.launch {
      combine(
        rxPreferences.customTabProviderPref.observe().map { packageName ->
          when {
            packageName.isEmpty() -> preferences.defaultCustomTabApp ?: ""
            else -> packageName
          }
        },
        rxPreferences.incognitoPref.observe(),
        rxPreferences.webviewPref.observe()
      ) { customTabProvider: String, isIncognito: Boolean, isWebView: Boolean ->
        if (customTabProvider.isEmpty() || isIncognito || isWebView) {
          CustomTabProviderInfo(
            iconUri = ApplicationIcon.createUri(Constants.SYSTEM_WEBVIEW),
            providerDescription = StringResource(
              R.string.tab_provider_status_message_home,
              resourceArgs = listOf(R.string.system_webview)
            ),
            providerReason = if (isIncognito)
              StringResource(R.string.provider_web_view_incognito_reason)
            else StringResource(0),
            allowChange = !isIncognito
          )
        } else {
          val appName = application.appName(customTabProvider)
          CustomTabProviderInfo(
            iconUri = ApplicationIcon.createUri(customTabProvider),
            providerDescription = StringResource(
              R.string.tab_provider_status_message_home,
              listOf(appName)
            ),
            providerReason = StringResource(0)
          )
        }
      }.flowOn(Dispatchers.Default)
        .collect { providerInfoLiveData.value = it }
    }
  }
}
