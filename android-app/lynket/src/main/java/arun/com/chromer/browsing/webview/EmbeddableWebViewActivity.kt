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

package arun.com.chromer.browsing.webview

import android.os.Build
import android.os.Bundle
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.lifecycle.lifecycleScope
import arun.com.chromer.bubbles.BubbleKey
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.bubbles.pageCount
import arun.com.chromer.bubbles.system.BubbleCloser
import arun.com.chromer.bubbles.system.EXTRA_BUBBLE_NOTIFICATION_ID
import arun.com.chromer.data.preferences.BrowsingMode
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Only exists to provide embeddable activity for Android 10 bubbles. Its bubble intent names the
 * bubble, and only that intent does: a page opened from the conversation shortcut is not in a
 * bubble and shows no count.
 *
 * A bubble cannot outlive its page: when the page finishes, Android removes the bubble and sends no
 * delete intent. So every finish of a page in a bubble closes the bubble, the X of the top bar and a
 * page that gave up alike, and the newest overflow bubble can take its place. Back folds the stack
 * and Android ends a page whose bubble has left without either of them reaching [finish].
 */
@AndroidEntryPoint
class EmbeddableWebViewActivity : WebViewActivity() {
  @Inject
  lateinit var bubbleRegistry: BubbleRegistry

  /** Lazy: only a page in a bubble asks for it, and bubbles start at Android 10. */
  @Inject
  lateinit var bubbleCloser: Lazy<BubbleCloser>

  /** True only for the page its bubble opened, on Android 10 and later. */
  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
  val inBubble: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && intent.hasExtra(EXTRA_BUBBLE_NOTIFICATION_ID)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (isFinishing || !intent.hasExtra(EXTRA_BUBBLE_NOTIFICATION_ID)) return
    val key = BubbleKey(BrowsingMode.NativeBubbles, intent.getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0))
    // Collected from create to destroy: it takes the first value before the first draw.
    lifecycleScope.launch {
      bubbleRegistry.bubbles.map { it.pageCount(key) }.distinctUntilChanged().collect(::showPageCount)
    }
  }

  override fun finish() {
    if (!isFinishing && inBubble) {
      bubbleCloser.get().closePage(intent.getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0)) { super.finish() }
    } else {
      super.finish()
    }
  }
}
