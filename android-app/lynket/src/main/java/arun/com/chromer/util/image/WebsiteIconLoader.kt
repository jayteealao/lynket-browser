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

import arun.com.chromer.util.WebUrl
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.Rect
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.annotation.WorkerThread
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.ColorUtil
import arun.com.chromer.util.Utils.dpToPx
import arun.com.chromer.util.Utils.getFirstLetter
import arun.com.chromer.util.Utils.isValidFavicon
import coil3.ImageLoader
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.toBitmap
import coil3.transform.CircleCropTransformation
import timber.log.Timber
import java.util.Locale

/**
 * Website favicons as bitmaps for bubbles, task descriptions and shortcuts.
 *
 * A favicon loads at its own size with software bitmaps (bitmaps are read by Palette and drawn
 * on a Canvas). When a website has no usable favicon, [iconWithPlaceholder] draws a letter
 * placeholder: a 56 dp circle in the website theme colour (or one of seven fixed colours) with
 * the first letter of the address.
 */
object WebsiteIconLoader {

  private val placeholderColors = intArrayOf(
    Color.parseColor("#3891A6"),
    Color.parseColor("#FDE74C"),
    Color.parseColor("#DB5461"),
    Color.parseColor("#EC4E20"),
    Color.parseColor("#6EEB83"),
    Color.parseColor("#29335C"),
    Color.parseColor("#BDE4A8")
  )

  private fun request(context: Context, url: String, circleCrop: Boolean): ImageRequest =
    ImageRequest.Builder(context)
      .data(url)
      .size(Size.ORIGINAL)
      .allowHardware(false)
      .apply { if (circleCrop) transformations(CircleCropTransformation()) }
      .build()

  /**
   * The favicon at [url], or null when it cannot load. Only `http` and `https` addresses load: a
   * page cannot make the app read a local `content:`, `file:` or app-icon address as its icon.
   * Blocks; call it off the main thread.
   */
  @WorkerThread
  fun favicon(
    context: Context,
    url: String?,
    circleCrop: Boolean = false,
    imageLoader: ImageLoader = context.imageLoader,
  ): Bitmap? {
    if (url.isNullOrEmpty() || !WebUrl.hasWebScheme(url)) return null
    return try {
      (imageLoader.executeBlocking(request(context, url, circleCrop)) as? SuccessResult)
        ?.image
        ?.toBitmap()
    } catch (e: Exception) {
      Timber.e(e, "Favicon failed to load from %s", WebUrl.hostForLog(url))
      null
    }
  }

  /** Suspending form of [favicon]. */
  suspend fun loadFavicon(
    context: Context,
    url: String?,
    imageLoader: ImageLoader = context.imageLoader,
  ): Bitmap? {
    if (url.isNullOrEmpty() || !WebUrl.hasWebScheme(url)) return null
    return try {
      (imageLoader.execute(request(context, url, circleCrop = false)) as? SuccessResult)
        ?.image
        ?.toBitmap()
    } catch (e: Exception) {
      Timber.e(e, "Favicon failed to load from %s", WebUrl.hostForLog(url))
      null
    }
  }

  /**
   * The favicon of [website] when it is valid ([isValidFavicon]), else the letter placeholder.
   * Blocks; call it off the main thread. Null only when drawing fails.
   */
  @WorkerThread
  fun iconWithPlaceholder(
    context: Context,
    website: Website,
    imageLoader: ImageLoader = context.imageLoader,
  ): Bitmap? = withPlaceholder(website, favicon(context, website.faviconUrl, imageLoader = imageLoader))

  /** Suspending form of [iconWithPlaceholder]. */
  suspend fun loadIconWithPlaceholder(
    context: Context,
    website: Website,
    imageLoader: ImageLoader = context.imageLoader,
  ): Bitmap? = withPlaceholder(website, loadFavicon(context, website.faviconUrl, imageLoader))

  private fun withPlaceholder(website: Website, favicon: Bitmap?): Bitmap? = try {
    if (isValidFavicon(favicon)) {
      favicon
    } else {
      // The website theme colour when it has one, else a random fixed colour.
      val color = if (website.themeColor() != Constants.NO_COLOR) {
        website.themeColor()
      } else {
        placeholderColors.random()
      }
      placeholder(color, website.safeLabel())
    }
  } catch (e: Exception) {
    Timber.e(e)
    null
  }

  /** The 56 dp letter placeholder in [color] for [label]. */
  fun placeholder(@ColorInt color: Int, label: String): Bitmap {
    val size = dpToPx(56.0)
    val icon = Bitmap.createBitmap(size, size, ARGB_8888)
    val canvas = Canvas(icon)
    val shadowRadius = dpToPx(1.8).toFloat()
    val shadowDx = dpToPx(0.1).toFloat()
    val shadowDy = dpToPx(0.8).toFloat()
    val textSize = dpToPx(24.0).toFloat()

    val bgPaint = Paint(ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      setColor(color)
      setShadowLayer(shadowRadius, shadowDx, shadowDy, Color.parseColor("#33000000"))
    }

    val padding = dpToPx(1.0)

    canvas.drawCircle(
      (size / 2).toFloat() - padding / 2,
      (size / 2).toFloat() - padding / 2,
      (size / 2).toFloat() - padding,
      bgPaint
    )

    val textPaint = Paint(ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
      setTextSize(textSize)
      setColor(ColorUtil.getForegroundWhiteOrBlack(color))
      style = Paint.Style.FILL
    }
    drawTextInCanvasCentre(canvas, textPaint, getFirstLetter(label).uppercase(Locale.getDefault()))
    return icon
  }

  private fun drawTextInCanvasCentre(canvas: Canvas, paint: Paint, text: String) {
    val cH = canvas.clipBounds.height()
    val cW = canvas.clipBounds.width()
    val rect = Rect()
    paint.textAlign = Paint.Align.LEFT
    paint.getTextBounds(text, 0, text.length, rect)
    val x = cW / 2f - rect.width() / 2f - rect.left.toFloat()
    val y = cH / 2f + rect.height() / 2f - rect.bottom
    canvas.drawText(text, x, y, paint)
  }
}
