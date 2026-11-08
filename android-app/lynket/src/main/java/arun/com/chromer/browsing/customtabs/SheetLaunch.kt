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
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.settings.Preferences

/** How a Custom Tab opens: as a sheet over the app the link came from, and at the side when wide. */
data class SheetOptions(val sheet: Boolean, val sideSheet: Boolean)

/**
 * Turns the sheet options into the Partial Custom Tab options of the intent builder. A browser
 * without sheets ignores them and opens the tab full screen, so nothing here checks support.
 */
object SheetLaunch {
  /** From this window width a sheet opens at the side. */
  const val SIDE_SHEET_BREAKPOINT_DP = 600

  /**
   * A breakpoint wider than any window, so a wide window gets the bottom sheet while the side sheet
   * option is off. With no breakpoint the browser would use its own (840 dp in Chrome).
   */
  const val NO_SIDE_SHEET_BREAKPOINT_DP = 100_000

  /** The sheet applies only to Slide over, the mode that opens links in a Custom Tab. */
  fun options(preferences: Preferences): SheetOptions = SheetOptions(
    sheet = preferences.browsingMode() == BrowsingMode.SlideOver && preferences.customTabSheet(),
    sideSheet = preferences.customTabSideSheet(),
  )

  /** The height of the window [activity] shows in, in pixels. */
  fun windowHeightPx(activity: Activity): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      activity.windowManager.currentWindowMetrics.bounds.height()
    } else {
      activity.resources.displayMetrics.heightPixels
    }

  /** The width of the window [activity] shows in, in pixels. */
  fun windowWidthPx(activity: Activity): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      activity.windowManager.currentWindowMetrics.bounds.width()
    } else {
      activity.resources.displayMetrics.widthPixels
    }

  /**
   * Adds the sheet options to [builder]: an initial height of half the window, resizable by a drag,
   * the app behind kept in view but not tappable, and the side sheet breakpoint. With the side sheet
   * on, it also sets an initial width of half the window: the browser opens a side sheet only when a
   * width is set. Returns whether
   * it did; with the sheet off, or no window height to halve, it leaves [builder] as it was.
   */
  fun applyTo(
    builder: CustomTabsIntent.Builder,
    options: SheetOptions,
    windowHeightPx: Int,
    windowWidthPx: Int = 0,
  ): Boolean {
    if (!options.sheet || windowHeightPx <= 1) return false
    // Rounded up: the browser treats half the height as the smallest sheet it opens.
    builder.setInitialActivityHeightPx((windowHeightPx + 1) / 2, CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
    builder.setBackgroundInteractionEnabled(false)
    builder.setActivitySideSheetBreakpointDp(
      if (options.sideSheet) SIDE_SHEET_BREAKPOINT_DP else NO_SIDE_SHEET_BREAKPOINT_DP,
    )
    if (options.sideSheet && windowWidthPx > 1) builder.setInitialActivityWidthPx((windowWidthPx + 1) / 2)
    return true
  }
}
