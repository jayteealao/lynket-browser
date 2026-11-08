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

import android.app.Activity
import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.PackageInfo
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_ENABLE_EPHEMERAL_BROWSING
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_INITIAL_ACTIVITY_HEIGHT_PX
import androidx.browser.customtabs.CustomTabsSession
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.data.preferences.BrowsingMode
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
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

  private fun tabs(activity: Activity) = CustomTabs(activity).forUrl("https://example.com/page")

  private fun openActivityWithProvider(): Activity {
    installProvider(provider, ephemeral = true)
    preferences.customTabPackage(provider)
    preferences.browsingMode(BrowsingMode.SlideOver)
    return Robolectric.buildActivity(Activity::class.java).setup().get()
  }

  @Test
  fun anIncognitoLaunchAsksForAnEphemeralTab() {
    val activity = openActivityWithProvider()

    val built = tabs(activity).incognito(true).buildIntent()

    assertThat(built.isEphemeralBrowsingEnabled).isTrue()
    assertThat(built.intent.getBooleanExtra(EXTRA_ENABLE_EPHEMERAL_BROWSING, false)).isTrue()
  }

  @Test
  fun aNormalLaunchCarriesNoEphemeralExtra() {
    val activity = openActivityWithProvider()

    val built = tabs(activity).buildIntent()

    assertThat(built.intent.hasExtra(EXTRA_ENABLE_EPHEMERAL_BROWSING)).isFalse()
    assertThat(built.isEphemeralBrowsingEnabled).isFalse()
  }

  @Test
  fun anIncognitoLinkWithTheSheetOnOpensAnEphemeralSheet() {
    val activity = openActivityWithProvider()
    preferences.customTabSheet(true)

    val intent = tabs(activity).incognito(true).buildIntent().intent
    assertThat(intent.getBooleanExtra(EXTRA_ENABLE_EPHEMERAL_BROWSING, false)).isTrue()
    assertThat(intent.hasExtra(EXTRA_INITIAL_ACTIVITY_HEIGHT_PX)).isTrue()

    assertThat(tabs(activity).incognito(true).launch()).isTrue()
    val started = shadowOf(activity).nextStartedActivityForResult
    assertThat(started.requestCode).isEqualTo(CustomTabs.SHEET_REQUEST)
    assertThat(started.intent.getBooleanExtra(EXTRA_ENABLE_EPHEMERAL_BROWSING, false)).isTrue()
  }

  @Test
  fun anIncognitoLaunchIsNotTiedToTheWebHeadsSession() {
    val activity = openActivityWithProvider()
    val session = mockk<CustomTabsSession>(relaxed = true)

    val incognito = tabs(activity).withSession(session).incognito(true).buildIntent().intent
    assertThat(incognito.extras?.getBinder(CustomTabsIntent.EXTRA_SESSION)).isNull()

    val normal = tabs(activity).withSession(session).buildIntent().intent
    assertThat(normal.extras?.getBinder(CustomTabsIntent.EXTRA_SESSION)).isNotNull()
  }

  private companion object {
    const val CUSTOM_TABS_SERVICE = "android.support.customtabs.action.CustomTabsService"
    const val EPHEMERAL_CATEGORY = "androidx.browser.customtabs.category.EphemeralBrowsing"
  }
}
