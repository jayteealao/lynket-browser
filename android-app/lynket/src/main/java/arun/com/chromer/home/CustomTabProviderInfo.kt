/*
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.home

import android.net.Uri
import arun.com.chromer.extenstions.StringResource

/**
 * What the home's provider card shows: the browser that renders pages, why (incognito forces the
 * web view), whether it can be changed, and whether no Custom Tabs browser is installed at all.
 */
data class CustomTabProviderInfo(
  val iconUri: Uri,
  val providerDescription: StringResource,
  val providerReason: StringResource,
  val allowChange: Boolean = true,
  val noProviderInstalled: Boolean = false,
)
