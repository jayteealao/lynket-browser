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

package arun.com.chromer.bubbles.system

/** The conversation ids a bubble of [url] can post under: the normal one and the incognito one. */
fun bubbleIdsOf(url: String): Set<String> =
  setOf(bubbleConversationId(url), bubbleConversationId(url, incognito = true))
