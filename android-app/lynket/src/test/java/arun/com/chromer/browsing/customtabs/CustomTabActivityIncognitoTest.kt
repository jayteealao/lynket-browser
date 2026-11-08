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

package arun.com.chromer.browsing.customtabs

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_ENABLE_EPHEMERAL_BROWSING
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * The activity that hands a link to the Custom Tab provider: an incognito link asks the provider
 * for an ephemeral tab, and stays incognito when no provider can take it.
 */
@HiltAndroidTest
class CustomTabActivityIncognitoTest : LynketRobolectricSuite() {

  private val provider = "com.android.chrome"
  private val url = "https://example.com/page"

  @Before
  fun clean() {
    clearPreferences()
    preferences.browsingMode(BrowsingMode.SlideOver)
  }

  private fun installProvider() {
    shadowOf(application.packageManager).installPackage(PackageInfo().apply { packageName = provider })
    preferences.customTabPackage(provider)
  }

  /** Every activity the pass-through started, in order. */
  private fun startedBy(incognito: Boolean?): List<Intent> {
    val intent = Intent(application, CustomTabActivity::class.java).setData(Uri.parse(url))
    if (incognito != null) intent.putExtra(EXTRA_KEY_INCOGNITO, incognito)
    val activity = Robolectric.buildActivity(CustomTabActivity::class.java, intent).setup().get()
    val shadow = shadowOf(activity)
    return generateSequence { shadow.nextStartedActivity }.toList()
  }

  @Test
  fun anIncognitoLinkAsksTheProviderForAnEphemeralTab() {
    installProvider()

    val tab = startedBy(incognito = true).single { it.`package` == provider }

    assertThat(tab.getBooleanExtra(EXTRA_ENABLE_EPHEMERAL_BROWSING, false)).isTrue()
  }

  @Test
  fun aNormalLinkOpensANormalTab() {
    installProvider()

    val tab = startedBy(incognito = null).single { it.`package` == provider }

    assertThat(tab.hasExtra(EXTRA_ENABLE_EPHEMERAL_BROWSING)).isFalse()
  }

  @Test
  fun withNoProviderTheWebViewFallbackStaysIncognito() {
    val page = startedBy(incognito = true).single { it.component?.className == WebViewActivity::class.java.name }

    assertThat(page.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
    assertThat(page.dataString).isEqualTo(url)
  }
}
