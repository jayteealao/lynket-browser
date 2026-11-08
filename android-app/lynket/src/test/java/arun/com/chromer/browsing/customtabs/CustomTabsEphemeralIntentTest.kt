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

import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.PackageInfo
import arun.com.chromer.LynketRobolectricSuite
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * An incognito link opens in an ephemeral Custom Tab only when the provider says it browses
 * ephemerally; the check reads the provider's Custom Tabs service as the library does.
 */
@HiltAndroidTest
class CustomTabsEphemeralIntentTest : LynketRobolectricSuite() {

  private val provider = "com.android.chrome"

  @Before
  fun clean() {
    clearPreferences()
  }

  private fun installProvider(packageName: String, ephemeral: Boolean) {
    val pm = shadowOf(application.packageManager)
    pm.installPackage(PackageInfo().apply { this.packageName = packageName })
    val service = ComponentName(packageName, "$packageName.CustomTabsService")
    pm.addServiceIfNotPresent(service)
    pm.addIntentFilterForService(
      service,
      IntentFilter(CUSTOM_TABS_SERVICE).apply { if (ephemeral) addCategory(EPHEMERAL_CATEGORY) },
    )
  }

  @Test
  fun aProviderThatDeclaresEphemeralBrowsingSupportsIt() {
    installProvider(provider, ephemeral = true)

    assertThat(CustomTabs.isEphemeralSupported(application, provider)).isTrue()
  }

  @Test
  fun aProviderWithoutTheDeclarationDoesNotSupportIt() {
    installProvider(provider, ephemeral = false)

    assertThat(CustomTabs.isEphemeralSupported(application, provider)).isFalse()
  }

  @Test
  fun noProviderOrAnUninstalledOneDoesNotSupportIt() {
    installProvider("org.example.other", ephemeral = true)

    assertThat(CustomTabs.isEphemeralSupported(application, null)).isFalse()
    assertThat(CustomTabs.isEphemeralSupported(application, "org.example.absent")).isFalse()
  }

  @Test
  fun theProviderAskedIsTheOneThePersonChose() {
    installProvider(provider, ephemeral = false)
    installProvider("org.example.chosen", ephemeral = false)
    preferences.customTabPackage("org.example.chosen")

    assertThat(CustomTabs.customTabPackage(application)).isEqualTo("org.example.chosen")
  }

  private companion object {
    const val CUSTOM_TABS_SERVICE = "android.support.customtabs.action.CustomTabsService"
    const val EPHEMERAL_CATEGORY = "androidx.browser.customtabs.category.EphemeralBrowsing"
  }
}
