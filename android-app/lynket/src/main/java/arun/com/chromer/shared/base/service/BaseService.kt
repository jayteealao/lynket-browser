/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
// Phase 7: Fully migrated to Hilt - removed legacy Dagger 2 component injection
package arun.com.chromer.shared.base.service

import android.app.Service
import dagger.hilt.android.AndroidEntryPoint

/**
 * Base service for all services in the app.
 * Phase 7: Migrated to Hilt - now uses @AndroidEntryPoint for automatic injection
 */
@AndroidEntryPoint
abstract class BaseService : Service()
