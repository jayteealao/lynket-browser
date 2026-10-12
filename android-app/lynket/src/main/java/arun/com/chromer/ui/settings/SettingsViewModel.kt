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

package arun.com.chromer.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import arun.com.chromer.data.preferences.UserPreferencesRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Names of the apps the browsing options rows show; null when not set. */
data class AppLabels(
  val providerPackage: String? = null,
  val providerName: String? = null,
  val secondaryPackage: String? = null,
  val secondaryName: String? = null,
  val favSharePackage: String? = null,
  val favShareName: String? = null,
)

/**
 * The one view model of the settings root, browsing mode, look and feel and browsing options
 * screens. It reads every value from the settings store, writes each change through
 * [SettingsRules] in one store edit, and queues the dialogs the rules ask for.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val repository: UserPreferencesRepository,
  private val preferences: Preferences,
  private val tabsManager: TabsManager,
) : ViewModel() {

  val values: StateFlow<SettingsValues> = repository.snapshot
    .filterNotNull()
    .map { SettingsValues.from(it, context) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsValues.from(repository.current(), context))

  private val queue = MutableStateFlow<List<SettingsDialog>>(emptyList())

  /** The dialogs to show, first one on screen. */
  val dialogs: StateFlow<List<SettingsDialog>> = queue.asStateFlow()

  private val labels = MutableStateFlow(AppLabels())
  val appLabels: StateFlow<AppLabels> = labels.asStateFlow()

  /** Sets [key] to [value] with every write and dialog the old screens tied to it. */
  fun set(key: String, value: Any?, canReadUsageStats: Boolean = Utils.canReadUsageStats(context)) {
    val result = SettingsRules.change(key, value, values.value, canReadUsageStats)
    if (result.dialogs.isNotEmpty()) queue.update { it + result.dialogs }
    viewModelScope.launch {
      repository.setValues(result.writes)
      if (key in APP_KEYS) loadAppLabels()
    }
    if (key == SettingsKeyTable.WEB_HEADS_COLOR && value is Int) {
      // Heads on screen take the new colour at once, as they did from the old screen.
      LocalBroadcastManager.getInstance(context).sendBroadcast(
        Intent(Constants.ACTION_WEBHEAD_COLOR_SET).putExtra(Constants.EXTRA_KEY_WEBHEAD_COLOR, value),
      )
    }
  }

  fun chooseMode(mode: BrowsingMode, overlayGranted: Boolean, notificationsAllowed: Boolean) =
    apply(SettingsRules.chooseMode(mode, overlayGranted, notificationsAllowed))

  /** Back from the overlay setting; nothing changes when the permission is still missing. */
  fun onOverlayResult(granted: Boolean, notificationsAllowed: Boolean) {
    if (granted) apply(SettingsRules.overlayGranted(notificationsAllowed))
  }

  /** Closes the dialog on screen; the native bubbles warning is followed by the notification ask. */
  fun dismissDialog(notificationsAllowed: Boolean = true) {
    queue.update { current ->
      val closed = current.firstOrNull() ?: return@update current
      val next = current.drop(1)
      if (closed == SettingsDialog.NativeBubblesWarning) SettingsRules.afterBubblesWarning(notificationsAllowed) + next else next
    }
  }

  /** After the usage access dialog: the app colour box follows the permission, as before. */
  fun syncAppColour(canReadUsageStats: Boolean) {
    if (!canReadUsageStats && values.value.bool(SettingsKeyTable.DYNAMIC_COLOR_APP)) {
      write(mapOf(SettingsKeyTable.DYNAMIC_COLOR_APP to false))
    }
  }

  /** Opens the native bubbles guide in a tab. */
  fun openBubblesGuide(activityContext: Context) {
    tabsManager.openUrl(activityContext, Website(BUBBLES_GUIDE_URL), true, false, false, false, false)
  }

  /** Reads the names of the provider, the secondary browser and the favourite share app. */
  fun refreshAppLabels() {
    viewModelScope.launch { loadAppLabels() }
  }

  private suspend fun loadAppLabels() {
    labels.value = withContext(Dispatchers.IO) {
      // The provider row reads the provider the way the old card did: a missing package falls
      // back to the default provider, which is stored.
      val provider = preferences.customTabPackage()
      val secondary = preferences.secondaryBrowserPackage()
      val favShare = preferences.favSharePackage()
      AppLabels(
        providerPackage = provider,
        providerName = provider?.let { Utils.getAppNameWithPackage(context, it) },
        secondaryPackage = secondary,
        secondaryName = secondary?.let { Utils.getAppNameWithPackage(context, it) },
        favSharePackage = favShare,
        favShareName = favShare?.let { Utils.getAppNameWithPackage(context, it) },
      )
    }
  }

  fun setApp(key: String, component: ComponentName) = set(key, component.flattenToString())

  private fun apply(result: RuleResult) {
    write(result.writes)
    if (result.dialogs.isNotEmpty()) queue.update { it + result.dialogs }
  }

  private fun write(writes: Map<String, Any?>) {
    viewModelScope.launch { repository.setValues(writes) }
  }

  companion object {
    const val BUBBLES_GUIDE_URL = "https://github.com/arunkumar9t2/lynket-browser/wiki/Android-10-Bubbles-Guide"
    private val APP_KEYS = setOf(SettingsKeyTable.SECONDARY_BROWSER, SettingsKeyTable.FAV_SHARE, SettingsKeyTable.PREFERRED_PACKAGE)
  }
}
