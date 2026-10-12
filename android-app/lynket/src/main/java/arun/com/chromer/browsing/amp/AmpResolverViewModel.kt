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

package arun.com.chromer.browsing.amp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.util.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Where the AMP lookup of one link stands. */
sealed interface AmpState {
  val url: String

  /** The lookup runs. */
  data class Loading(override val url: String) : AmpState

  /** The page has an AMP version; it opens. */
  data class Found(val website: Website) : AmpState {
    override val url: String get() = website.url
  }

  /** The page has no AMP version; the normal page opens, as before. */
  data class NotFound(val website: Website) : AmpState {
    override val url: String get() = website.url
  }

  /** The lookup could not run (the device is offline); the person picks Try again or the normal page. */
  data class Error(override val url: String) : AmpState
}

/** Looks up the AMP version of a link once; the result survives recreation of the dialog. */
@HiltViewModel
class AmpResolverViewModel @Inject constructor(
  private val websiteRepository: WebsiteRepository,
  private val networkMonitor: NetworkMonitor,
) : ViewModel() {

  private val _state = MutableStateFlow<AmpState?>(null)
  val state: StateFlow<AmpState?> = _state.asStateFlow()

  private var lookup: Job? = null
  private var incognito = false

  /** Starts the lookup for [url] unless one already ran or runs (for example after recreation). */
  fun start(url: String, incognito: Boolean) {
    if (_state.value != null) return
    this.incognito = incognito
    resolve(url)
  }

  /** Runs the lookup again (the error state's Try again). */
  fun retry() {
    val url = _state.value?.url ?: return
    resolve(url)
  }

  private fun resolve(url: String) {
    lookup?.cancel()
    _state.value = AmpState.Loading(url)
    lookup = viewModelScope.launch {
      val website = runCatching {
        if (incognito) websiteRepository.getWebsiteReadOnly(url).first() else websiteRepository.getWebsite(url).first()
      }.onFailure { Timber.e(it, "AMP lookup failed for %s", url) }.getOrNull()
      _state.value = when {
        website != null && website.hasAmp() -> AmpState.Found(website)
        !networkMonitor.isOnlineNow() -> AmpState.Error(url)
        else -> AmpState.NotFound(website ?: Website(url))
      }
    }
  }
}
