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

package arun.com.chromer.data.apps

import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.data.common.App
import kotlinx.coroutines.rx2.awaitSingle

/**
 * Phase 6: Modern AppRepository Extensions
 *
 * Provides suspend function wrappers for AppRepository methods,
 * removing the need for RxJava interop in ViewModels.
 *
 * These extensions bridge the gap between legacy RxJava-based
 * AppRepository and modern Coroutines-based ViewModels.
 */

/**
 * Get all Custom Tab providers as a suspend function.
 * Converts RxJava Observable<List<Provider>> to suspend function.
 */
suspend fun AppRepository.allProvidersSuspend(): List<Provider> {
    return allProviders().awaitSingle()
}

/**
 * Get all apps as a suspend function.
 * Converts RxJava Observable<List<App>> to suspend function.
 */
suspend fun AppRepository.allAppsSuspend(): List<App> {
    return allApps().awaitSingle()
}

/**
 * Get app by package name as a suspend function.
 * Converts RxJava Observable<App> to suspend function.
 */
suspend fun AppRepository.getAppSuspend(packageName: String): App {
    return getApp(packageName).awaitSingle()
}
