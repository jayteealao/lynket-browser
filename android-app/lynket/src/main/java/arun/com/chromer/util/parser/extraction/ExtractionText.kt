/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), com.chimbori.crux.common.StringUtils
 * and HeuristicString. Copyright Chimbori. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: translated to Kotlin; only the functions
 * the article extraction calls are kept.
 */
/*
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

package arun.com.chromer.util.parser.extraction

import org.jsoup.nodes.Element
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern

// The extraction rules were written against java.lang.String, so these helpers keep its semantics
// where Kotlin's differ: trim() removes only characters up to U+0020 (Kotlin's also removes other
// Unicode spaces), split() drops trailing empty strings, and toLowerCase() uses the default locale.

private val WHITESPACE = Pattern.compile("[ \r\t\n]+")

/** [String.trim] of `java.lang.String`: removes leading and trailing characters up to U+0020. */
internal fun String.javaTrim(): String {
  var start = 0
  var end = length
  while (start < end && this[start] <= ' ') start++
  while (start < end && this[end - 1] <= ' ') end--
  return if (start > 0 || end < length) substring(start, end) else this
}

/** `java.lang.String.split(regex)`: trailing empty strings are dropped. */
internal fun String.javaSplit(regex: String): Array<String> = Pattern.compile(regex).split(this)

/** `java.lang.String.toLowerCase()`, in the default locale. */
internal fun String.javaLowerCase(): String = lowercase(java.util.Locale.getDefault())

internal fun urlEncodeSpaceCharacter(url: String): String =
  if (url.isEmpty()) url else WHITESPACE.matcher(url.javaTrim()).replaceAll("%20")

internal fun countMatches(str: String, substring: String): Int {
  var count = 0
  val indexOf = str.indexOf(substring)
  if (indexOf >= 0) {
    count++
    count += countMatches(str.substring(indexOf + substring.length), substring)
  }
  return count
}

/** Collapses runs of spaces, tabs and line breaks to one space, then trims. */
internal fun innerTrim(str: String): String = WHITESPACE.matcher(str).replaceAll(" ").javaTrim()

internal fun countLetters(str: String): Int {
  var chars = 0
  for (c in str) {
    if (Character.isLetter(c)) chars++
  }
  return chars
}

internal fun parseAttrAsInt(element: Element, attr: String): Int = try {
  Integer.parseInt(element.attr(attr))
} catch (e: NumberFormatException) {
  0
}

internal fun getQueryParameters(url: URI): Map<String, String> {
  val nameValuePairs = LinkedHashMap<String, String>()
  // A URI without a query throws here, as it did before; the caller's error handling covers it.
  val queryParameters = url.query.javaSplit("&")
  for (nameValuePair in queryParameters) {
    val equalsSignPosition = nameValuePair.indexOf("=")
    nameValuePairs[URLDecoder.decode(nameValuePair.substring(0, equalsSignPosition), "UTF-8")] =
      URLDecoder.decode(nameValuePair.substring(equalsSignPosition + 1), "UTF-8")
  }
  return nameValuePairs
}

internal fun cleanTitle(title: String): String {
  var title = title
  val res = StringBuilder()
  val index = title.lastIndexOf("|")
  if (index > 0 && title.length / 2 < index) title = title.substring(0, index + 1)

  var counter = 0
  val strs = title.javaSplit("\\|")
  for (part in strs) {
    if (counter == strs.size - 1 && res.length > part.length) continue
    if (counter > 0) res.append("|")
    res.append(part)
    counter++
  }
  return innerTrim(res.toString())
}

/**
 * Resolves [relativeUrl] against [baseUrl], best effort: returns [relativeUrl] unchanged when no
 * absolute URL can be made, and null when it is empty.
 */
internal fun makeAbsoluteUrl(baseUrl: String?, relativeUrl: String?): String? {
  if (relativeUrl == null || relativeUrl.isEmpty()) return null

  val base = unescapeBackslashHex(baseUrl)
  val relative = unescapeBackslashHex(relativeUrl)
  return try {
    URL(URL(base), relative).toString()
  } catch (e: MalformedURLException) {
    relative
  }
}

private val BACKSLASH_HEX_SPACE_PATTERN = Pattern.compile("\\\\([a-zA-Z0-9]+) ") // Space is included.

/** Unescapes CSS-style backslash-hex escapes in URLs, for example "\3a " to ":". */
internal fun unescapeBackslashHex(input: String?): String? {
  if (input == null) return null
  var output: String = input
  val matcher = BACKSLASH_HEX_SPACE_PATTERN.matcher(input)
  while (matcher.find()) {
    val backSlashHexSpace = matcher.group(0)!!
    val hexUnicode = matcher.group(1)!!
    val decimalUnicode = Integer.parseInt(hexUnicode.javaTrim(), 16)
    val unicode = String(Character.toChars(decimalUnicode))
    output = output.replace(backSlashHexSpace, unicode)
  }
  return output
}

/**
 * The first candidate that is not empty. Candidates are read in order and only until one is found.
 * With none, the result is "" when any candidate was "", otherwise [initial]. This is the old
 * extractor's HeuristicString chain.
 */
internal fun heuristicString(initial: String?, vararg candidates: () -> String?): String? {
  if (!initial.isNullOrEmpty()) return initial
  var string = initial
  for (candidate in candidates) {
    val value = candidate() ?: continue
    if (string.isNullOrEmpty()) string = value else return string
  }
  return string
}
