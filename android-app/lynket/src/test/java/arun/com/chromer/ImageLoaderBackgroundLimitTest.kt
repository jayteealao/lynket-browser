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

package arun.com.chromer

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The app's one image loader keeps only part of its memory cache while no Lynket screen is
 * visible. The value is read back from the loader that [Lynket.newImageLoader] builds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ImageLoaderBackgroundLimitTest {

  private val context: Context = RuntimeEnvironment.getApplication()

  /**
   * Coil keeps the loader class and its option read-back internal to Kotlin callers, but both are
   * public on the JVM, so the test reads the value the builder stored through those methods.
   */
  private fun ImageLoader.backgroundPercent(): Double {
    val options = javaClass.getMethod("getOptions").invoke(this)
    return Class.forName("coil3.ImageLoaders_androidKt")
      .getMethod("getMemoryCacheMaxSizePercentWhileInBackground", options.javaClass)
      .invoke(null, options) as Double
  }

  @Test
  fun the_app_image_loader_limits_its_memory_cache_in_the_background() {
    val percent = Lynket().newImageLoader(context).backgroundPercent()

    assertEquals(Lynket.BACKGROUND_MEMORY_CACHE_PERCENT, percent, 0.0)
    assertTrue("background share $percent must be below the full cache", percent < 1.0)
  }

  @Test
  fun the_app_image_loader_keeps_a_memory_cache() {
    assertNotNull(Lynket().newImageLoader(context).memoryCache)
  }

  @Test
  fun a_loader_without_the_option_has_no_background_limit() {
    assertEquals(1.0, ImageLoader.Builder(context).build().backgroundPercent(), 0.0)
  }
}
