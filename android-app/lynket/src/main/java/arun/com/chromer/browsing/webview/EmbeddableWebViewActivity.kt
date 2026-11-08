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

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.shared.Constants.EXTRA_KEY_TOOLBAR_COLOR
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
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
 *
 * Open in full window, the first item of the menu in a bubble, is the way out to a full window: it
 * opens the same page in a task of its own and closes the bubble, see [openInFullWindow].
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

  /** Set by [openInFullWindow], so the close of the bubble does not keep its stack open. */
  private var leavingForFullWindow = false

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
      val id = intent.getIntExtra(EXTRA_BUBBLE_NOTIFICATION_ID, 0)
      bubbleCloser.get().closePage(id, leavingTheStack = leavingForFullWindow) { super.finish() }
    } else {
      super.finish()
    }
  }

  /**
   * Opens the page on screen in a full window, with its colour and its incognito state, then closes
   * the bubble. The window starts first, so the page is never gone with nothing in its place. The
   * bubble closes once its page has stopped, which is when the window in front has folded the stack:
   * closed while the stack was still open, the bubbles that stay were left drawn where the open
   * stack was, over the new window. [FULL_WINDOW_CLOSE_LIMIT_MS] closes it if the page never stops.
   * Does nothing for a page that is not in a bubble or that is already closing or leaving.
   */
  fun openInFullWindow() {
    if (!inBubble || isFinishing || leavingForFullWindow) return
    startActivity(fullWindowIntent(this, getCurrentUrl(), incognito, toolbarColour))
    leavingForFullWindow = true
    lifecycleScope.launch {
      delay(FULL_WINDOW_CLOSE_LIMIT_MS)
      if (!isFinishing) finish()
    }
  }

  override fun onStop() {
    super.onStop()
    if (leavingForFullWindow && !isFinishing) finish()
  }

  private companion object {
    /** How long a page that left for a full window can stay before its bubble closes anyway. */
    const val FULL_WINDOW_CLOSE_LIMIT_MS = 2_000L
  }
}

/**
 * The intent of the full window for a page that leaves its bubble: [WebViewActivity] in a task of
 * its own, so Android opens it full screen and not in the bubble. Only the page on screen moves, not
 * its history.
 */
internal fun fullWindowIntent(context: Context, address: String, incognito: Boolean, toolbarColour: Int): Intent =
  Intent(context, WebViewActivity::class.java)
    .setData(Uri.parse(address))
    .putExtra(EXTRA_KEY_INCOGNITO, incognito)
    .putExtra(EXTRA_KEY_TOOLBAR_COLOR, toolbarColour)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
