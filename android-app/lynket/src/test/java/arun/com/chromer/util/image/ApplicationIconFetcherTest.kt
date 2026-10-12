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

package arun.com.chromer.util.image

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import arun.com.chromer.util.appicon.ApplicationIcon
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toUri
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Provider icons are `application-icon:<package>` URIs. The fetcher answers only that scheme and
 * loads the installed app's launcher icon, so `AsyncImage(model = iconUri)` shows it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ApplicationIconFetcherTest {

  private val context: Application get() = RuntimeEnvironment.getApplication()

  @Test
  fun thePackageIsReadFromAnApplicationIconUriOnly() {
    val uri = ApplicationIcon.createUri("com.android.chrome").toString().toUri()

    assertThat(ApplicationIconFetcher.packageNameOf(uri)).isEqualTo("com.android.chrome")
    assertThat(ApplicationIconFetcher.packageNameOf("https://example.com/icon.png".toUri())).isNull()
    assertThat(ApplicationIconFetcher.packageNameOf("application-icon:".toUri())).isNull()
  }

  @Test
  fun anInstalledAppIconLoadsThroughTheImageLoader() = runTest {
    shadowOf(context.packageManager).setApplicationIcon("com.android.chrome", ColorDrawable(Color.BLUE))
    val loader = ImageLoader.Builder(context)
      .components { add(ApplicationIconFetcher.Factory()) }
      .build()

    val result = loader.execute(
      ImageRequest.Builder(context).data(ApplicationIcon.createUri("com.android.chrome")).build()
    )

    assertThat(result).isInstanceOf(SuccessResult::class.java)
  }

  @Test
  fun aMissingAppGivesNoIconAndNoCrash() = runTest {
    val loader = ImageLoader.Builder(context)
      .components { add(ApplicationIconFetcher.Factory()) }
      .build()

    val result = loader.execute(
      ImageRequest.Builder(context).data(ApplicationIcon.createUri("com.example.missing")).build()
    )

    assertThat(result).isNotInstanceOf(SuccessResult::class.java)
  }
}
