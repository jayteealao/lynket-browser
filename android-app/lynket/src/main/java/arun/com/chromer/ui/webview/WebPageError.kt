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

package arun.com.chromer.ui.webview

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.ui.components.ErrorState

/** What the page area shows for one cause: its icon, its title, and a body that may name the site. */
internal data class WebPageErrorContent(
  val icon: ImageVector,
  @StringRes val title: Int,
  @StringRes val body: Int,
  /** Whether [body] takes the site's name as its one argument. */
  val bodyNamesSite: Boolean,
)

/** The icon and the words of each reason a page did not load. */
internal fun errorContentOf(cause: LoadFailure): WebPageErrorContent = when (cause) {
  LoadFailure.Offline -> WebPageErrorContent(Icons.Outlined.WifiOff, R.string.web_page_error_offline_title, R.string.web_page_error_offline_body, bodyNamesSite = false)
  LoadFailure.CannotReach -> WebPageErrorContent(Icons.Outlined.ErrorOutline, R.string.web_page_error_cannot_reach_title, R.string.web_page_error_cannot_reach_body, bodyNamesSite = true)
  LoadFailure.Certificate -> WebPageErrorContent(Icons.Outlined.Lock, R.string.web_page_error_certificate_title, R.string.web_page_error_certificate_body, bodyNamesSite = true)
  LoadFailure.General -> WebPageErrorContent(Icons.Outlined.Warning, R.string.web_page_error_general_title, R.string.web_page_error_general_body, bodyNamesSite = true)
}

/** The site a person recognises in [address]: its host, or the address itself when it has none. */
internal fun siteOf(address: String): String = Uri.parse(address).host?.takeIf { it.isNotBlank() } ?: address

/**
 * A page that did not load: the cause and Retry, centred in the page area. In a short window the
 * picture goes first, and the content scrolls when it is still too tall, so the title and Retry
 * stay reachable. Screen readers hear the cause when it replaces the page.
 */
@Composable
fun WebPageError(failure: PageLoad.Failed, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  val content = errorContentOf(failure.cause)
  val body = if (content.bodyNamesSite) stringResource(content.body, siteOf(failure.address)) else stringResource(content.body)
  BoxWithConstraints(modifier.fillMaxSize().testTag("webview-error").semantics { liveRegion = LiveRegionMode.Polite }) {
    val showBlob = maxHeight >= BLOB_MIN_PAGE_HEIGHT
    Column(
      Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .heightIn(min = if (constraints.hasBoundedHeight) maxHeight else 0.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      ErrorState(
        title = stringResource(content.title),
        body = body,
        icon = content.icon,
        retryLabel = stringResource(R.string.retry),
        onRetry = onRetry,
        showBlob = showBlob,
      )
    }
  }
}

/**
 * The page-area height below which the picture leaves: the picture, the title, two lines of body,
 * Retry and their gaps take about 330 dp at the default text size.
 */
private val BLOB_MIN_PAGE_HEIGHT = 360.dp
