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

package arun.com.chromer.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * The title of a large top app bar. Expanded it is display small at weight 600, shrinking to fit a
 * long title on one line; collapsed it keeps the bar's own title style.
 */
@Composable
fun ScreenTitle(text: String) {
  val barStyle = LocalTextStyle.current
  val expanded = barStyle.fontSize == MaterialTheme.typography.headlineMedium.fontSize
  val colour = LocalContentColor.current
  if (expanded) {
    val style = MaterialTheme.typography.displaySmall
    BasicText(
      text,
      style = style.copy(color = colour, fontWeight = FontWeight.SemiBold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = style.fontSize),
    )
  } else {
    BasicText(
      text,
      style = barStyle.copy(color = colour, fontWeight = FontWeight.SemiBold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
