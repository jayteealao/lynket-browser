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

package arun.com.chromer.bubbles

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.ColorInt
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import java.lang.ref.WeakReference

/** What a native bubble posts. It sits beside the open list, which stores it to post again. */
data class BubbleLoadData(
  val website: Website,
  val fromMinimize: Boolean,
  val fromAmp: Boolean,
  val incognito: Boolean,
  val contextRef: WeakReference<Context?> = WeakReference(null),
  val icon: Bitmap? = null,
  val color: Int = Constants.NO_COLOR,
  /** The height the first post asked for; a later post keeps it, whatever window is on screen then. */
  val desiredHeightDp: Int? = null,
  /**
   * The site's own colour for the bars of the bubble's page, or none when the site has no colour, the
   * colour settings do not follow the site, or the bubble is incognito. It is not the notification's
   * colour, which [color] holds.
   */
  @ColorInt val siteColor: Int = Constants.NO_COLOR,
)
