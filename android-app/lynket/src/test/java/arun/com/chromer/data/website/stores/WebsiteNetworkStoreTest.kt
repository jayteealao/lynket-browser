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

package arun.com.chromer.data.website.stores

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** A page that cannot be read gives no website, so the lookup of a web head reports a failure. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WebsiteNetworkStoreTest {

  private lateinit var server: ServerSocket
  private val store = WebsiteNetworkStore(ApplicationProvider.getApplicationContext())

  @Before
  fun startAServerThatHangsUp() {
    server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    thread(isDaemon = true) {
      while (!server.isClosed) {
        runCatching { server.accept().close() }
      }
    }
  }

  @After
  fun stopTheServer() {
    server.close()
  }

  @Test
  fun aPageWhoseServerHangsUpGivesNoWebsite() = runBlocking<Unit> {
    val url = "http://127.0.0.1:${server.localPort}/article"

    assertThat(store.getWebsite(url).first()).isNull()
  }

  @Test
  fun aPageOnAClosedPortGivesNoWebsite() = runBlocking<Unit> {
    val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    assertThat(store.getWebsite("http://127.0.0.1:$port/article").first()).isNull()
  }
}
