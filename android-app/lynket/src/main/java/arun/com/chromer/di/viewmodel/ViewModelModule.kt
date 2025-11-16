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

package arun.com.chromer.di.viewmodel

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Hilt ViewModelComponent module.
 *
 * All ViewModels now use @HiltViewModel and are automatically bound by Hilt.
 * This module exists but is empty - Hilt handles all ViewModel injection automatically.
 *
 * Legacy note: Removed all @Binds methods. ViewModels with @HiltViewModel are
 * automatically registered by Hilt and don't need manual binding.
 */
@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule
