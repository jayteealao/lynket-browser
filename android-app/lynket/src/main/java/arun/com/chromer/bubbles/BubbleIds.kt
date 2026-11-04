/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.bubbles

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The id the notification of a bubble of [url] posts as. Every other id of the bubble (its
 * conversation, shortcut, locus, intents and dismissal) derives from this one. An incognito
 * bubble's id is keyed with a secret that lives only as long as the process, so another app that
 * reads the id cannot test it against a known address.
 */
fun bubbleNotificationId(url: String, incognito: Boolean = false): Int =
  if (incognito) IncognitoBubbleIds.of(url) else url.hashCode()

/** The id of the conversation (and its long-lived shortcut) a bubble of [url] belongs to. */
fun bubbleConversationId(url: String, incognito: Boolean = false): String =
  conversationIdOfNotification(bubbleNotificationId(url, incognito))

/** The id of the conversation (and its long-lived shortcut) of the bubble posted as [notificationId]. */
fun conversationIdOfNotification(notificationId: Int): String = "bubble_$notificationId"

/** Ids of incognito bubbles: a keyed hash of the address, with a key made for this process only. */
private object IncognitoBubbleIds {
  private val key = SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "HmacSHA256")

  fun of(url: String): Int {
    val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
    return ByteBuffer.wrap(mac.doFinal(url.toByteArray(Charsets.UTF_8))).int
  }
}
