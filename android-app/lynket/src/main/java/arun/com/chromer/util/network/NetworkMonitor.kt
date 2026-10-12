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

package arun.com.chromer.util.network

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the device has a working internet connection, from the system's default network. */
interface NetworkMonitor {
  val isOnline: Flow<Boolean>

  /** Reads the connection now (the snackbar's Retry). */
  fun isOnlineNow(): Boolean
}

@Singleton
class ConnectivityNetworkMonitor @Inject constructor(
  private val application: Application,
) : NetworkMonitor {

  private val connectivityManager: ConnectivityManager?
    get() = application.getSystemService()

  override fun isOnlineNow(): Boolean {
    val manager = connectivityManager ?: return true
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  override val isOnline: Flow<Boolean> = callbackFlow {
    val manager = connectivityManager
    if (manager == null) {
      trySend(true)
      awaitClose()
      return@callbackFlow
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        trySend(isOnlineNow())
      }

      override fun onLost(network: Network) {
        trySend(isOnlineNow())
      }

      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
      }
    }
    trySend(isOnlineNow())
    try {
      manager.registerDefaultNetworkCallback(callback)
    } catch (e: RuntimeException) {
      // Too many callbacks registered by the app; treat the device as online rather than fail.
      Timber.e(e, "NetworkMonitor: could not register the default network callback")
    }
    awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
  }.distinctUntilChanged()
}
