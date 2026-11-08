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

package arun.com.chromer.ui.browser

import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

/** A link opened from the Browser key keeps full incognito mode. */
@HiltAndroidTest
class BrowserIntentTest : LynketRobolectricSuite() {

  private val url = "https://example.com/page"

  @Before
  fun clean() {
    clearPreferences()
  }

  @Test
  fun fullIncognitoModeOpensTheLinkIncognito() {
    preferences.fullIncognitoMode(true)

    val intent = browserIntent(application, url)

    assertThat(intent.component?.className).isEqualTo(CustomTabActivity::class.java.name)
    assertThat(intent.dataString).isEqualTo(url)
    assertThat(intent.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)).isTrue()
  }

  @Test
  fun aNormalLinkCarriesNoIncognitoFlag() {
    val intent = browserIntent(application, url)

    assertThat(intent.hasExtra(EXTRA_KEY_INCOGNITO)).isFalse()
  }
}
