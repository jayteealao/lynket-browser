/*
 *  Lynket
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.data.website

import arun.com.chromer.data.website.model.Website
import kotlinx.coroutines.rx2.awaitSingle

/**
 * Phase 6: Modern WebsiteRepository Extensions
 *
 * Provides suspend function wrappers for WebsiteRepository methods,
 * removing the need for RxJava interop in ViewModels.
 *
 * These extensions bridge the gap between legacy RxJava-based
 * WebsiteRepository and modern Coroutines-based ViewModels.
 */

/**
 * Get website as a suspend function.
 * Converts RxJava Observable<Website> to suspend function.
 */
suspend fun WebsiteRepository.getWebsiteSuspend(url: String): Website {
    return getWebsite(url).awaitSingle()
}

/**
 * Get website (read-only) as a suspend function.
 * Converts RxJava Observable<Website> to suspend function.
 */
suspend fun WebsiteRepository.getWebsiteReadOnlySuspend(url: String): Website {
    return getWebsiteReadOnly(url).awaitSingle()
}
