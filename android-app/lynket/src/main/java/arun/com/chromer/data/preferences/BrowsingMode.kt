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

package arun.com.chromer.data.preferences

/** The browsing mode choices; slide over is "web heads off and native bubbles off". */
enum class BrowsingMode {
  SlideOver,
  WebHeads,
  NativeBubbles,
  ;

  companion object {
    /**
     * The one mode of the two stored switches. Web heads win when both are on, as the settings
     * screens have always shown it; neither on is slide over.
     */
    fun resolve(webHeads: Boolean, nativeBubbles: Boolean): BrowsingMode = when {
      webHeads -> WebHeads
      nativeBubbles -> NativeBubbles
      else -> SlideOver
    }
  }
}
