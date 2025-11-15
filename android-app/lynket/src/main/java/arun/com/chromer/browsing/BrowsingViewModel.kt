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

package arun.com.chromer.browsing

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.Application
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.R
import arun.com.chromer.data.Result
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.Utils
import arun.com.chromer.util.compat.TaskDescriptionCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * A simple view model delivering a Website from repo and handling related tasks.
 *
 * Migrated to Hilt: Uses @HiltViewModel annotation for automatic ViewModel injection.
 * Converted from RxJava to Kotlin Coroutines in Phase 8.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@HiltViewModel
class BrowsingViewModel
@Inject
constructor(
  @ApplicationContext private val application: Application,
  private val preferences: Preferences,
  private val websiteRepository: WebsiteRepository
) : ViewModel() {

  var isIncognito: Boolean = false

  private val websiteQueue = MutableSharedFlow<String>(extraBufferCapacity = Int.MAX_VALUE)
  private val taskDescriptionQueue = MutableSharedFlow<Website>(extraBufferCapacity = Int.MAX_VALUE)

  val toolbarColor = MutableLiveData<Int>()
  val websiteLiveData = MutableLiveData<Result<Website>>()
  val activityDescription = MutableLiveData<ActivityManager.TaskDescription>()

  init {
    toolbarColor.value = ContextCompat.getColor(application, R.color.colorPrimary)

    // Monitor website requests
    viewModelScope.launch {
      websiteQueue
        .filter { it.isNotEmpty() }
        .flatMapMerge { url ->
          websiteFlow(url)
            .map { Result.Success(it) as Result<Website> }
            .catch { e -> emit(Result.Failure(e)) }
            .flowOn(Dispatchers.IO)
        }
        .catch { e -> Timber.e(e) }
        .collect { result ->
          websiteLiveData.value = result
          if (result is Result.Success) {
            taskDescriptionQueue.tryEmit(result.data!!)
          }
        }
    }

    // Set task descriptions
    viewModelScope.launch {
      taskDescriptionQueue
        .onEach { website ->
          // First update with basic info
          setTaskDescription(
            TaskDescriptionCompat(website.safeLabel(), null, toolbarColor.value!!)
          )
        }
        .map { website ->
          val iconColor = websiteRepository.getWebsiteIconWithPlaceholderAndColor(website)
          val selectedToolbarColor = when {
            !preferences.dynamiceToolbarEnabledAndWebEnabled() -> toolbarColor.value!!
            website.themeColor() != Constants.NO_COLOR -> website.themeColor()
            else -> iconColor.second
          }
          TaskDescriptionCompat(
            website.safeLabel(),
            iconColor.first,
            selectedToolbarColor
          )
        }
        .flowOn(Dispatchers.IO)
        .catch { e -> Timber.e(e) }
        .collect { setTaskDescription(it) }
    }
  }

  private fun websiteFlow(url: String): Flow<Website> = if (!isIncognito) {
    websiteRepository.getWebsite(url)
  } else {
    websiteRepository.getWebsiteReadOnly(url)
  }

  private fun setTaskDescription(task: TaskDescriptionCompat?) {
    task?.let {
      toolbarColor.postValue(task.color)
      if (Utils.ANDROID_LOLLIPOP) {
        activityDescription.postValue(task.toActivityTaskDescription())
      }
    }
  }

  fun loadWebSiteDetails(url: String) {
    websiteQueue.tryEmit(url)
  }
}
