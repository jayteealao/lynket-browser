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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * The one avatar of a site or app row: the first letter of [label] on a tinted 40 dp tile, replaced
 * by the image once it loads. [image] is a favicon address or an app icon address
 * (`ApplicationIcon.createUri`); both load through the image loader and its memory cache.
 */
@Composable
fun SiteAvatar(label: String, image: Any?, modifier: Modifier = Modifier, imageSize: Dp = 24.dp) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
  ) {
    var loaded by remember(image) { mutableStateOf(false) }
    if (!loaded) {
      Text(
        label.trim().take(1).uppercase(),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
      )
    }
    if (image != null && (image !is String || image.isNotBlank())) {
      AsyncImage(
        model = image,
        contentDescription = null,
        modifier = Modifier.size(imageSize),
        onSuccess = { loaded = true },
      )
    }
  }
}
