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

import arun.com.chromer.bubbles.system.BubbleLoadData
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

/** Where Android holds an open bubble. A bubble it no longer holds has no entry. */
enum class BubblePlace { STACK, OVERFLOW }

/** One open bubble. It prints no address, so a log line cannot show where a person went. */
data class OpenBubble(
  val key: BubbleKey,
  val url: String,
  val incognito: Boolean,
  val closedOnPurpose: Boolean = false,
  val place: BubblePlace = BubblePlace.STACK,
  /** What the bubble last posted, so it can post again. Web heads hold none. */
  val loadData: BubbleLoadData? = null,
) {
  val mode: BrowsingMode get() = key.mode

  override fun toString() =
    "OpenBubble(key=$key, incognito=$incognito, closedOnPurpose=$closedOnPurpose, place=$place)"
}

/**
 * The open bubbles of both modes in the order they opened, oldest first. A bubble of each mode can
 * be open at once after the person changes mode, so a read names the mode it wants.
 *
 * An entry marked closed on purpose is one the person dismissed: it never comes back on its own.
 * Only a dismissal by the person sets the marker, never a bubble that ages out of Android's
 * overflow. The marker outlasts the entry, because a dismissal can arrive twice and a closed web
 * head is gone from the list at once. It lives by key, so no incognito address stays behind, and
 * it clears when the person opens that link again.
 */
@Singleton
class BubbleRegistry @Inject constructor() {
  private val lock = Any()
  private val entries = MutableStateFlow<List<OpenBubble>>(emptyList())
  private val closedOnPurpose = mutableSetOf<BubbleKey>()

  val bubbles: StateFlow<List<OpenBubble>> = entries.asStateFlow()

  /**
   * Opens an entry at the end of the list. A link that is already open keeps its entry and place,
   * and takes [loadData] when a value comes. Either way the person opened it, so it is no longer
   * closed on purpose.
   */
  fun add(
    mode: BrowsingMode,
    url: String,
    incognito: Boolean,
    loadData: BubbleLoadData? = null,
  ): OpenBubble = synchronized(lock) {
    val key = BubbleKey.of(mode, url, incognito)
    setMarker(key, closed = false)
    val open = find(key)
    when {
      open == null -> OpenBubble(key, url, incognito, loadData = loadData).also { entries.value += it }
      loadData == null -> open
      else -> open.copy(loadData = loadData).also(::replace)
    }
  }

  /**
   * Takes the entry off the list and does not mark it: a bubble can leave on its own. Returns the
   * entry, or null when it had already left.
   */
  fun remove(key: BubbleKey): OpenBubble? = synchronized(lock) {
    find(key).also { entries.value = entries.value.filterNot { it.key == key } }
  }

  fun find(key: BubbleKey): OpenBubble? = entries.value.find { it.key == key }

  fun find(mode: BrowsingMode, url: String, incognito: Boolean): OpenBubble? {
    val id = bubbleNotificationId(url, incognito)
    return entries.value.find { it.mode == mode && it.key.notificationId == id }
  }

  fun list(mode: BrowsingMode): List<OpenBubble> = entries.value.filter { it.mode == mode }

  /** The entries of [mode] that the last reconcile found in Android's overflow, oldest first. */
  fun overflow(mode: BrowsingMode): List<OpenBubble> =
    list(mode).filter { it.place == BubblePlace.OVERFLOW }

  /**
   * Brings the entries of [mode] in line with what Android holds. [read] gives the place of each
   * notification id, or null when it could not tell, and then nothing changes. It runs under the
   * lock: an entry is added after its post, so a bubble posted during the read cannot be taken off
   * by it. An entry Android no longer holds leaves unmarked. Returns the keys that left.
   */
  fun reconcile(mode: BrowsingMode, read: () -> Map<Int, BubblePlace>?): List<BubbleKey>? =
    synchronized(lock) {
      val places = read() ?: return null
      val (kept, gone) = entries.value.partition { it.mode != mode || it.key.notificationId in places }
      entries.value = kept.map {
        if (it.mode == mode) it.copy(place = places.getValue(it.key.notificationId)) else it
      }
      gone.map { it.key }
    }

  /** Stores what the bubble of [key] posted last. A bubble that has left stays gone. */
  fun updateLoadData(key: BubbleKey, loadData: BubbleLoadData) = synchronized(lock) {
    find(key)?.let { replace(it.copy(loadData = loadData)) }
  }

  fun markClosedOnPurpose(key: BubbleKey) = synchronized(lock) { setMarker(key, closed = true) }

  fun isClosedOnPurpose(key: BubbleKey): Boolean = synchronized(lock) { key in closedOnPurpose }

  /**
   * The overflow bubbles that may come back to the stack, oldest first, so the last one is the
   * newest. A bubble in the stack has nowhere to return from, and one the person closed stays closed.
   */
  fun returnCandidates(mode: BrowsingMode): List<OpenBubble> =
    overflow(mode).filterNot { it.closedOnPurpose }

  private fun replace(entry: OpenBubble) {
    entries.value = entries.value.map { if (it.key == entry.key) entry else it }
  }

  private fun setMarker(key: BubbleKey, closed: Boolean) {
    if (closed) closedOnPurpose += key else closedOnPurpose -= key
    entries.value = entries.value.map {
      if (it.key == key) it.copy(closedOnPurpose = closed) else it
    }
  }
}
