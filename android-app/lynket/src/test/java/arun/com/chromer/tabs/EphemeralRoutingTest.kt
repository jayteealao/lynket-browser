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

package arun.com.chromer.tabs

import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.browsing.customtabs.CustomTabs
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import com.google.common.truth.Truth.assertWithMessage
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import javax.inject.Inject

/**
 * Where a link that opens in a tab shows: an incognito link opens in an ephemeral Custom Tab when
 * the person's browser can open one, and in the app's web view otherwise.
 */
@HiltAndroidTest
class EphemeralRoutingTest : LynketRobolectricSuite() {
  @Inject
  lateinit var tabs: TabsManager

  private val provider = "com.android.chrome"
  private val url = "https://www.example.com/page"

  private var providerInstalled = true
  private var ephemeral = true

  @Before
  fun stubTheProvider() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.SlideOver)
    mockkObject(CustomTabs.Companion)
    every { CustomTabs.getCustomTabSupportingPackages(any()) } answers { if (providerInstalled) listOf(provider) else emptyList() }
    every { CustomTabs.customTabPackage(any()) } answers { if (providerInstalled) provider else null }
    every { CustomTabs.customTabPackageAmong(any(), any()) } answers { if (providerInstalled) provider else null }
    every { CustomTabs.isEphemeralSupported(any(), any()) } answers { providerInstalled && ephemeral }
  }

  @After
  fun unmock() {
    unmockkObject(CustomTabs.Companion)
  }

  private enum class Link { NORMAL, INCOGNITO, FULL_INCOGNITO_MODE }

  private data class Row(
    val appWebView: Boolean,
    val provider: Boolean,
    val link: Link,
    val ephemeral: Boolean,
    val webView: Boolean,
  )

  /** The whole table: every combination, with the expected place written out by its rule. */
  private val table: List<Row> = buildList {
    for (appWebView in listOf(false, true)) for (provider in listOf(false, true)) for (link in Link.entries) {
      for (ephemeral in listOf(false, true)) {
        val webView = when {
          appWebView -> true
          !provider -> true
          link == Link.NORMAL -> false
          else -> !ephemeral
        }
        add(Row(appWebView, provider, link, ephemeral, webView))
      }
    }
  }

  @Test
  fun everyCaseOfTheTableOpensWhereItShould() {
    assertThat(table).hasSize(24)
    for (row in table) {
      preferences.useWebView(row.appWebView)
      preferences.fullIncognitoMode(row.link == Link.FULL_INCOGNITO_MODE)
      providerInstalled = row.provider
      ephemeral = row.ephemeral

      assertWithMessage(row.toString())
        .that(tabs.shouldUseWebView(incognito = row.link == Link.INCOGNITO))
        .isEqualTo(row.webView)
    }
  }

  @Test
  fun anIncognitoLinkOpensAnIncognitoCustomTabWithAnEphemeralProvider() {
    ephemeral = true

    tabs.openBrowsingTab(application, Website(url), fromNewTab = false, incognito = true)

    val tab = shadowOf(application).nextStartedActivity
    assertThat(tab.component?.className).isEqualTo(CustomTabActivity::class.java.name)
    assertThat(tab.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
    assertThat(tab.dataString).isEqualTo(url)
  }

  @Test
  fun anIncognitoLinkOpensTheIncognitoWebViewWithAProviderWithoutEphemeralTabs() {
    ephemeral = false

    tabs.openBrowsingTab(application, Website(url), fromNewTab = false, incognito = true)

    val tab = shadowOf(application).nextStartedActivity
    assertThat(tab.component?.className).isEqualTo(WebViewActivity::class.java.name)
    assertThat(tab.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
  }

  @Test
  fun aNormalLinkNeverAsksTheProviderAboutEphemeralTabs() {
    tabs.openBrowsingTab(application, Website(url), fromNewTab = false, incognito = false)

    val tab = shadowOf(application).nextStartedActivity
    assertThat(tab.component?.className).isEqualTo(CustomTabActivity::class.java.name)
    assertThat(tab.hasExtra(EXTRA_KEY_INCOGNITO)).isFalse()
    verify(exactly = 0) { CustomTabs.isEphemeralSupported(any(), any()) }
  }
}
