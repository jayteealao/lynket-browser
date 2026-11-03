/*
 *
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
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

package arun.com.chromer.bubbles

import arun.com.chromer.bubbles.system.bubbleNotificationId
import arun.com.chromer.data.preferences.BrowsingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Names one open bubble: its mode and the notification id Android knows it by. The id separates a
 * normal bubble of a link from an incognito one, and holds no address for an incognito bubble.
 */
data class BubbleKey(val mode: BrowsingMode, val notificationId: Int) {
  init {
    require(mode != BrowsingMode.SlideOver) { "Slide over has no bubbles" }
  }

  companion object {
    fun of(mode: BrowsingMode, url: String, incognito: Boolean) =
      BubbleKey(mode, bubbleNotificationId(url, incognito))
  }
}

/** One open bubble. */
data class OpenBubble(val key: BubbleKey, val url: String, val incognito: Boolean) {
  val mode: BrowsingMode get() = key.mode
}

/**
 * The open bubbles of both modes in the order they opened, oldest first. A bubble of each mode can
 * be open at once after the person changes mode, so a read names the mode it wants.
 */
@Singleton
class BubbleRegistry @Inject constructor() {
  private val lock = Any()
  private val entries = MutableStateFlow<List<OpenBubble>>(emptyList())

  val bubbles: StateFlow<List<OpenBubble>> = entries.asStateFlow()

  /** Opens an entry at the end of the list. A link that is already open keeps its entry and place. */
  fun add(mode: BrowsingMode, url: String, incognito: Boolean): OpenBubble = synchronized(lock) {
    val key = BubbleKey.of(mode, url, incognito)
    entries.value.find { it.key == key }
      ?: OpenBubble(key, url, incognito).also { entries.value += it }
  }

  fun remove(key: BubbleKey) = synchronized(lock) {
    entries.value = entries.value.filterNot { it.key == key }
  }

  fun find(mode: BrowsingMode, url: String, incognito: Boolean): OpenBubble? {
    val id = bubbleNotificationId(url, incognito)
    return entries.value.find { it.mode == mode && it.key.notificationId == id }
  }

  fun list(mode: BrowsingMode): List<OpenBubble> = entries.value.filter { it.mode == mode }
}
