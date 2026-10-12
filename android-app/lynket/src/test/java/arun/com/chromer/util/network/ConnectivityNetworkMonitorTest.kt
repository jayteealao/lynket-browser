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
import android.net.NetworkCapabilities
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * The offline message depends on the monitor reporting a lost default network. Inside a network
 * callback the synchronous ConnectivityManager reads can still return the network that was just
 * lost (platform javadoc of `NetworkCallback.onLost`), so the monitor must trust the callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ConnectivityNetworkMonitorTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  @Test
  fun aLostDefaultNetworkReportsOfflineEvenWhileTheActiveNetworkStillReadsConnected() = runTest {
    val manager = application.getSystemService(ConnectivityManager::class.java)
    val network = manager.activeNetwork!!
    val internet = ShadowNetworkCapabilities.newInstance().also {
      shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    shadowOf(manager).setNetworkCapabilities(network, internet)
    val monitor = ConnectivityNetworkMonitor(application)

    monitor.isOnline.test {
      assertThat(awaitItem()).isTrue()
      val callback = shadowOf(manager).networkCallbacks.single()

      // The race: the system reports the loss before getActiveNetwork stops returning it.
      callback.onLost(network)

      assertThat(awaitItem()).isFalse()
      cancelAndIgnoreRemainingEvents()
    }
  }
}
