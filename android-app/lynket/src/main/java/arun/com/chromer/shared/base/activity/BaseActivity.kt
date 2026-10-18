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

// Phase 8.7: Converted from RxJava to Kotlin Flows/Coroutines
// Removed deprecated CompositeSubscription - use lifecycleScope.launch instead

package arun.com.chromer.shared.base.activity

import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import arun.com.chromer.R

/**
 * Base activity for all activities in the app.
 *
 * For lifecycle-aware coroutines, use:
 * - lifecycleScope.launch { } for UI work
 * - lifecycleScope.launch(Dispatchers.IO) { } for background work
 */
abstract class BaseActivity : AppCompatActivity() {

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == R.id.home) {
      finishWithTransition()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  protected fun finishWithTransition() {
    finishAfterTransition()
  }
}
