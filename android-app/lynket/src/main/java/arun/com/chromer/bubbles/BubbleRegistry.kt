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

import arun.com.chromer.data.preferences.BrowsingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
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
  val place: BubblePlace = BubblePlace.STACK,
  /** What the bubble last posted, so it can post again. Web heads hold none. */
  val loadData: BubbleLoadData? = null,
  /**
   * The list's count of changes when Android was last known to hold this bubble: a read that began
   * before it cannot judge it. Null while a post is on its way, because Android lists a notification
   * a moment after the post.
   */
  val settledAt: Long? = 0,
) {
  val mode: BrowsingMode get() = key.mode

  override fun toString() =
    "OpenBubble(key=$key, incognito=$incognito, place=$place)"
}

/** The place of a page among the open bubbles of its mode, from 1, and how many are open. */
data class BubblePageCount(val position: Int, val total: Int)

/**
 * The place of the bubble of [key], counting the bubbles in Android's overflow. Null for a bubble
 * this list does not hold, and for one alone: "1 of 1" says nothing.
 */
fun List<OpenBubble>.pageCount(key: BubbleKey): BubblePageCount? {
  val open = filter { it.mode == key.mode }
  val index = open.indexOfFirst { it.key == key }
  return if (index < 0 || open.size < 2) null else BubblePageCount(index + 1, open.size)
}

/**
 * The open bubbles of both modes in the order they opened, oldest first. A bubble of each mode can
 * be open at once after the person changes mode, so a read names the mode it wants.
 *
 * A key marked closed on purpose is one the person dismissed: it is never offered for a return.
 * Only a dismissal by the person sets the marker, never a bubble that ages out of Android's
 * overflow. The marker outlasts the entry, because a dismissal can arrive twice and a closed web
 * head is gone from the list at once. It lives by key, so no incognito address stays behind, and
 * it clears when the person opens that link again. Only the latest [CLOSED_KEYS_KEPT] are kept: a
 * bubble that left the list has nothing to return with, so forgetting an old mark returns nothing.
 */
@Singleton
class BubbleRegistry @Inject constructor() {
  private val lock = Any()
  private val entries = MutableStateFlow<List<OpenBubble>>(emptyList())
  private val closedOnPurpose = LinkedHashSet<BubbleKey>()
  private var changes = 0L
  private var fullestStack = 0

  val bubbles: StateFlow<List<OpenBubble>> = entries.asStateFlow()

  /**
   * Opens an entry at the end of the list. A link that is already open keeps its entry and place,
   * and takes [loadData] when a value comes. Either way the person opened it, so it is no longer
   * closed on purpose. With [posting], no read drops the entry until [settled] says Android has it.
   */
  fun add(
    mode: BrowsingMode,
    url: String,
    incognito: Boolean,
    loadData: BubbleLoadData? = null,
    posting: Boolean = false,
  ): OpenBubble = synchronized(lock) {
    val key = BubbleKey.of(mode, url, incognito)
    closedOnPurpose -= key
    val open = find(key)
    val settledAt = if (posting) null else ++changes
    when {
      open == null -> OpenBubble(key, url, incognito, loadData = loadData, settledAt = settledAt)
        .also { entries.value += it }
      else -> {
        // Two links can share an id: they already share a notification, and the first keeps the entry.
        if (open.url != url) Timber.w("Two links share the bubble id %d", key.notificationId)
        open.copy(loadData = loadData ?: open.loadData, settledAt = settledAt).also(::replace)
      }
    }
  }

  /**
   * A post of the open bubble of [key] is on its way, and it can take the notification down first.
   * Until [settled], no read drops the entry.
   */
  fun posting(key: BubbleKey) = synchronized(lock) {
    find(key)?.let { replace(it.copy(settledAt = null)) }
  }

  /** Android shows the posted bubble of [key], so a later read may judge it. */
  fun settled(key: BubbleKey) = synchronized(lock) {
    find(key)?.let { replace(it.copy(settledAt = ++changes)) }
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
   * notification id, or null when it could not tell, and then nothing changes. The read asks
   * Android, so it runs outside the lock, which the main thread also takes. An entry that opened
   * after the read began, or whose post is on its way, stays: the read cannot have seen it. An entry
   * Android no longer holds leaves unmarked. Returns the keys that left.
   */
  fun reconcile(mode: BrowsingMode, read: () -> Map<Int, BubblePlace>?): List<BubbleKey>? {
    val readFrom = synchronized(lock) { changes }
    val places = read() ?: return null
    return synchronized(lock) {
      val (kept, gone) = entries.value.partition {
        it.mode != mode || it.key.notificationId in places || it.settledAt.isAfter(readFrom)
      }
      entries.value = kept.map { entry ->
        val place = places[entry.key.notificationId]
        if (entry.mode == mode && place != null) entry.copy(place = place) else entry
      }
      fullestStack = maxOf(fullestStack, entries.value.count { it.mode == mode && it.place == BubblePlace.STACK })
      gone.map { it.key }
    }
  }

  /**
   * The most bubbles any read found in Android's stack, or 0 before the first read. Android does not
   * say how many its stack holds, and an overflow bubble means the stack reached that number.
   */
  fun fullestStack(): Int = synchronized(lock) { fullestStack }

  /** Stores what the bubble of [key] posted last. A bubble that has left stays gone. */
  fun updateLoadData(key: BubbleKey, loadData: BubbleLoadData) = synchronized(lock) {
    find(key)?.let { replace(it.copy(loadData = loadData)) }
  }

  fun markClosedOnPurpose(key: BubbleKey) = synchronized(lock) {
    closedOnPurpose -= key
    closedOnPurpose += key
    if (closedOnPurpose.size > CLOSED_KEYS_KEPT) closedOnPurpose -= closedOnPurpose.first()
  }

  fun isClosedOnPurpose(key: BubbleKey): Boolean = synchronized(lock) { key in closedOnPurpose }

  /**
   * The overflow bubbles that may come back to the stack, oldest first, so the last one is the
   * newest. A bubble in the stack has nowhere to return from, and one the person closed stays closed.
   */
  fun returnCandidates(mode: BrowsingMode): List<OpenBubble> =
    overflow(mode).filterNot { isClosedOnPurpose(it.key) }

  private fun Long?.isAfter(read: Long) = this == null || this > read

  private fun replace(entry: OpenBubble) {
    entries.value = entries.value.map { if (it.key == entry.key) entry else it }
  }

  private companion object {
    const val CLOSED_KEYS_KEPT = 64
  }
}
