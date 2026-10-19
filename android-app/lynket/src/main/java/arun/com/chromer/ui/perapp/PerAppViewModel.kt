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

package arun.com.chromer.ui.perapp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.apps.AppRepository
import arun.com.chromer.data.common.App
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.preferences.readBoolean
import arun.com.chromer.util.debug.DebugHooks
import arun.com.chromer.ui.settings.SettingsKeyTable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Optional
import javax.inject.Inject

/** One app row: its rules as stored in the per-app rule store. */
data class PerAppRow(val packageName: String, val appName: String, val blacklisted: Boolean, val incognito: Boolean)

/** The per-app list: null apps while the first load runs. */
data class PerAppState(val apps: List<PerAppRow>? = null, val refreshing: Boolean = false, val query: String = "") {
  /** Apps whose name or package contains the query, ignoring case. */
  val visible: List<PerAppRow>?
    get() = apps?.let { all ->
      val q = query.trim()
      if (q.isEmpty()) all else all.filter { it.appName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
}

/** Messages and requests of the per-app screen. */
sealed interface PerAppEvent {
  data class MasterSwitched(val on: Boolean) : PerAppEvent
  data object NeedsUsageAccess : PerAppEvent
}

/**
 * Per-app settings: the installed apps with their secondary browser (blacklist) and incognito rules,
 * and the master switch. Each toggle is written to the per-app rule store at once.
 */
@HiltViewModel
class PerAppViewModel(
  private val context: Context,
  private val appRepository: AppRepository,
  private val preferencesRepository: UserPreferencesRepository,
  private val io: CoroutineDispatcher,
  private val debugHooks: DebugHooks = DebugHooks.None,
) : ViewModel() {

  @Inject
  constructor(
    @ApplicationContext context: Context,
    appRepository: AppRepository,
    preferencesRepository: UserPreferencesRepository,
    debugHooks: Optional<DebugHooks>,
  ) : this(context, appRepository, preferencesRepository, Dispatchers.IO, DebugHooks.of(debugHooks))

  private val state = MutableStateFlow(PerAppState())
  val uiState: StateFlow<PerAppState> = state.asStateFlow()

  val perAppEnabled: StateFlow<Boolean> = preferencesRepository.snapshot
    .filterNotNull()
    .map { it.readBoolean(SettingsKeyTable.PER_APP_SETTINGS, false) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesRepository.current().readBoolean(SettingsKeyTable.PER_APP_SETTINGS, false))

  private val messages = MutableSharedFlow<PerAppEvent>(extraBufferCapacity = 4)
  val events: SharedFlow<PerAppEvent> = messages.asSharedFlow()

  init {
    load()
  }

  /** Loads the apps; a refresh keeps the current list on screen until the new one is ready. */
  fun load() {
    viewModelScope.launch {
      state.update { it.copy(refreshing = true) }
      try {
        val apps = withContext(io) {
          debugHooks.perAppLoadDelayMs(context).takeIf { it > 0 }?.let { delay(it) }
          appRepository.allApps().map { it.toRow() }
        }
        Timber.d("Per-app list: %d apps", apps.size)
        state.update { it.copy(apps = apps, refreshing = false) }
      } catch (e: Exception) {
        Timber.e(e, "Per-app list did not load")
        state.update { it.copy(apps = it.apps ?: emptyList(), refreshing = false) }
      }
    }
  }

  fun search(query: String) = state.update { it.copy(query = query) }

  fun setBlacklisted(packageName: String, on: Boolean) = update(packageName) {
    if (on) appRepository.setPackageBlacklisted(packageName) else appRepository.removeBlacklist(packageName)
  }

  fun setIncognito(packageName: String, on: Boolean) = update(packageName) {
    if (on) appRepository.setPackageIncognito(packageName) else appRepository.removeIncognito(packageName)
  }

  /** The master switch; turning it on needs usage access, as before. */
  fun setPerAppEnabled(on: Boolean, canReadUsageStats: Boolean) {
    if (on && !canReadUsageStats) {
      messages.tryEmit(PerAppEvent.NeedsUsageAccess)
      return
    }
    viewModelScope.launch {
      preferencesRepository.setValue(SettingsKeyTable.PER_APP_SETTINGS, on)
      messages.emit(PerAppEvent.MasterSwitched(on))
    }
  }

  /** On opening the screen the switch stays on only while usage access is granted, as before. */
  fun syncWithUsageAccess(canReadUsageStats: Boolean) {
    if (!canReadUsageStats && perAppEnabled.value) {
      viewModelScope.launch { preferencesRepository.setValue(SettingsKeyTable.PER_APP_SETTINGS, false) }
    }
  }

  private fun update(packageName: String, write: suspend () -> App) {
    viewModelScope.launch {
      try {
        val saved = withContext(io) { write() }.toRow()
        state.update { current ->
          current.copy(apps = current.apps?.map { if (it.packageName == packageName) saved.copy(appName = it.appName) else it })
        }
      } catch (e: Exception) {
        Timber.e(e, "Per-app rule for %s not saved", packageName)
      }
    }
  }

  private fun App.toRow() = PerAppRow(packageName, appName, blackListed, incognito)
}
