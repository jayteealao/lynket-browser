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
import coil3.Uri
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.DataSource
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Precision
import coil3.size.Size
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A favicon decodes with Coil's real decoders at no more than its largest use (the 108 dp bubble
 * icon), and a small favicon keeps its own size, so it still becomes the letter placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FaviconDecodeSizeTest {

  private val context: Application get() = RuntimeEnvironment.getApplication()
  private val requests = mutableListOf<ImageRequest>()
  private val url = "https://example.com/favicon.png"

  /** A solid PNG of [width] × [height] pixels. */
  private fun png(width: Int, height: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.BLUE)
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
  }

  /** An image loader whose network answers every address with [bytes]; Coil's decoders run. */
  private fun loader(bytes: ByteArray, vararg decoders: Decoder.Factory): ImageLoader {
    val factory = Fetcher.Factory<Uri> { _, options: Options, _ ->
      Fetcher {
        SourceFetchResult(
          source = ImageSource(Buffer().write(bytes), options.fileSystem),
          mimeType = "image/png",
          dataSource = DataSource.NETWORK,
        )
      }
    }
    return ImageLoader.Builder(context)
      .components {
        decoders.forEach { add(it) }
        add(factory)
        add(coil3.intercept.Interceptor { chain ->
          requests += chain.request
          chain.proceed()
        })
      }
      .memoryCache(null)
      .diskCache(null)
      .build()
  }

  private val px get() = WebsiteIconLoader.largestIconPx(context)

  private fun website() = Website("https://example.com/page").apply {
    title = "Example"
    faviconUrl = url
  }

  @Test
  fun a_1024_px_favicon_decodes_at_the_largest_use() {
    val icon = WebsiteIconLoader.favicon(context, url, imageLoader = loader(png(1024, 1024)))!!

    assertThat(px).isEqualTo((108 * context.resources.displayMetrics.density).roundToInt())
    assertThat(px).isLessThan(1024)
    assertThat(icon.width).isEqualTo(px)
    assertThat(icon.height).isEqualTo(px)
  }

  @Test
  fun a_1024_px_favicon_decodes_at_the_largest_use_with_bitmap_factory() {
    val icon = WebsiteIconLoader.favicon(
      context, url, imageLoader = loader(png(1024, 1024), BitmapFactoryDecoder.Factory())
    )!!

    assertThat(icon.width).isAtMost(px)
    assertThat(icon.height).isAtMost(px)
    assertThat(icon.width).isGreaterThan(px / 2)
    assertThat(icon.height).isGreaterThan(px / 2)
  }

  @Test
  fun the_round_web_head_icon_decodes_at_the_largest_use() {
    val icon = WebsiteIconLoader.favicon(
      context, url, circleCrop = true, imageLoader = loader(png(1024, 1024))
    )!!

    assertThat(icon.width).isAtMost(px)
    assertThat(icon.height).isAtMost(px)
  }

  @Test
  fun the_bubble_icon_decodes_at_the_largest_use() = runTest {
    val icon = WebsiteIconLoader.loadIconWithPlaceholder(context, website(), loader(png(1024, 1024)))!!

    assertThat(icon.width).isAtMost(px)
    assertThat(icon.height).isAtMost(px)
  }

  @Test
  fun a_wide_favicon_keeps_its_shape_inside_the_largest_use() {
    val icon = WebsiteIconLoader.favicon(context, url, imageLoader = loader(png(1024, 512)))!!

    assertThat(icon.width).isEqualTo(px)
    assertThat(abs(icon.height - px / 2)).isAtMost(1)
  }

  @Test
  fun a_tiny_favicon_is_not_upscaled_and_still_becomes_the_placeholder() {
    val favicon = WebsiteIconLoader.favicon(context, url, imageLoader = loader(png(16, 16)))!!
    assertThat(favicon.width).isEqualTo(16)
    assertThat(favicon.height).isEqualTo(16)

    val icon = WebsiteIconLoader.iconWithPlaceholder(context, website(), loader(png(16, 16)))!!
    assertThat(icon.width).isEqualTo(Utils.dpToPx(56.0))
  }

  @Test
  fun the_request_asks_for_the_largest_use_without_upscaling() = runTest {
    WebsiteIconLoader.favicon(context, url, imageLoader = loader(png(1024, 1024)))

    val request = requests.single()
    assertThat(request.sizeResolver.size()).isEqualTo(Size(px, px))
    assertThat(request.precision).isEqualTo(Precision.INEXACT)
  }
}
