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
import android.graphics.Bitmap
import android.graphics.Color
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.util.Utils
import coil3.ImageLoader
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Favicons for bubbles, task descriptions and shortcuts: a valid favicon is returned as loaded,
 * a missing, tiny or failing one becomes the 56 dp letter placeholder, and a failure never throws.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebsiteIconLoaderTest {

  private val context: Application get() = RuntimeEnvironment.getApplication()
  private val requests = mutableListOf<coil3.request.ImageRequest>()

  /** An image loader whose network answers with [answer] (null = failure). */
  private fun loader(answer: Bitmap?): ImageLoader = ImageLoader.Builder(context)
    .components {
      add(Interceptor { chain ->
        requests += chain.request
        if (answer != null) {
          SuccessResult(answer.asImage(), chain.request)
        } else {
          ErrorResult(null, chain.request, IllegalStateException("offline"))
        }
      })
    }
    .build()

  private fun bitmap(size: Int) = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

  private fun website(themeColor: String? = null) = Website("https://example.com/page").apply {
    title = "Example"
    faviconUrl = "https://example.com/favicon.png"
    this.themeColor = themeColor
  }

  @Test
  fun aValidFaviconIsReturnedAsLoadedWithSoftwareBitmaps() {
    val favicon = bitmap(64)

    val icon = WebsiteIconLoader.iconWithPlaceholder(context, website(), loader(favicon))

    assertThat(icon).isSameInstanceAs(favicon)
    val request = requests.single()
    assertThat(request.data).isEqualTo("https://example.com/favicon.png")
    assertThat(request.allowHardware).isFalse()
  }

  @Test
  fun aTinyFaviconBecomesThePlaceholderInTheThemeColour() {
    val icon = WebsiteIconLoader.iconWithPlaceholder(context, website("#FF0000"), loader(bitmap(16)))!!

    val size = Utils.dpToPx(56.0)
    assertThat(icon.width).isEqualTo(size)
    assertThat(icon.height).isEqualTo(size)
    assertThat(icon.getPixel(size / 2, size / 4)).isEqualTo(Color.RED)
  }

  @Test
  fun aFailingFaviconBecomesThePlaceholderAndDoesNotThrow() {
    val icon = WebsiteIconLoader.iconWithPlaceholder(context, website(), loader(null))

    assertThat(icon).isNotNull()
    assertThat(icon!!.width).isEqualTo(Utils.dpToPx(56.0))
  }

  @Test
  fun aFailingFaviconAloneGivesNull() {
    assertThat(WebsiteIconLoader.favicon(context, "https://example.com/favicon.png", imageLoader = loader(null))).isNull()
    assertThat(WebsiteIconLoader.favicon(context, null, imageLoader = loader(bitmap(64)))).isNull()
  }

  @Test
  fun theRoundIconAsksForACircleCrop() {
    WebsiteIconLoader.favicon(context, "https://example.com/favicon.png", circleCrop = true, imageLoader = loader(bitmap(64)))

    assertThat(requests.single().transformations.single()).isInstanceOf(CircleCropTransformation::class.java)
  }

  @Test
  fun theSuspendingFormMatchesTheBlockingForm() = runTest {
    val favicon = bitmap(64)

    assertThat(WebsiteIconLoader.loadIconWithPlaceholder(context, website(), loader(favicon))).isSameInstanceAs(favicon)
  }
}
