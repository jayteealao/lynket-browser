/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.tabs

import kotlinx.coroutines.rx2.await

/**
 * Phase 6: Modern TabsManager Extensions
 *
 * Provides suspend function wrappers for TabsManager methods,
 * removing the need for RxJava interop in ViewModels.
 *
 * These extensions bridge the gap between legacy RxJava-based
 * TabsManager and modern Coroutines-based ViewModels.
 */

/**
 * Get active tabs as a suspend function.
 * Converts RxJava Single<List<Tab>> to suspend function.
 */
suspend fun TabsManager.getActiveTabsSuspend(): List<TabsManager.Tab> {
    return getActiveTabs().await()
}

/**
 * Close all tabs as a suspend function.
 * Converts RxJava Single<List<Tab>> to suspend function.
 */
suspend fun TabsManager.closeAllTabsSuspend(): List<TabsManager.Tab> {
    return closeAllTabs().await()
}
