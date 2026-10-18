/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), class articles.ExtractionHelpers.
 * Copyright Chimbori. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: translated to Kotlin.
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

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

/** Scores the elements of a page to find the one that holds the article. */
internal object ExtractionHelpers {
  const val GRAVITY_SCORE_ATTRIBUTE = "gravityScore"
  const val GRAVITY_SCORE_SELECTOR = "*[$GRAVITY_SCORE_ATTRIBUTE]"

  private val IMPORTANT_NODES = Pattern.compile("p|div|td|h1|h2|article|section")

  private val UNLIKELY_CSS_CLASSES_AND_IDS = Pattern.compile(
    "com(bx|ment|munity)|dis(qus|cuss)|e(xtra|[-]?mail)|foot|" +
      "header|menu|re(mark|ply)|rss|sh(are|outbox)|sponsor" +
      "a(d|ll|gegate|rchive|ttachment)|(pag(er|ination))|popup|print|" +
      "login|si(debar|gn|ngle)|facebook|twitter|email"
  )

  private val POSITIVE_CSS_CLASSES_AND_IDS = Pattern.compile(
    "(^(body|content|h?entry|main|page|post|text|blog|story|haupt))" +
      "|arti(cle|kel)|instapaper_body"
  )

  private val NEGATIVE_CSS_CLASSES_AND_IDS = Pattern.compile(
    "nav($|igation)|user|com(ment|bx)|(^com-)|contact|" +
      "foot|masthead|(me(dia|ta))|outbrain|promo|related|scroll|(sho(utbox|pping))|" +
      "sidebar|sponsor|tags|tool|widget|player|disclaimer|toc|infobox|vcard|post-ratings"
  )

  private val NEGATIVE_CSS_STYLES = Pattern.compile("hidden|display: ?none|font-size: ?small")

  /**
   * The weight of [e]: its class, id and style, its own text, and above all its child elements,
   * because class and id names cannot be predicted.
   */
  fun getWeight(e: Element): Int {
    var weight = calcWeight(e)
    weight += Math.round(e.ownText().length / 100.0 * 10).toInt()
    weight += weightChildNodes(e)
    return weight
  }

  /**
   * Weighs the children of [rootEl]. Not every page nests its paragraphs right inside the article
   * element, so text one level down scores more than text two levels down, which favours the
   * element with fewer nesting levels.
   */
  private fun weightChildNodes(rootEl: Element): Int {
    var weight = 0
    var caption: Element? = null
    val pEls = ArrayList<Element>(5)
    for (child in rootEl.children()) {
      var ownText = child.ownText()

      // A paragraph counts all its text, including text inside formatting tags.
      if (child.tagName() == "p") ownText = child.text()

      val ownTextLength = ownText.length
      if (ownTextLength < 20) continue

      if (ownTextLength > 200) weight += maxOf(50, ownTextLength / 10)

      if (child.tagName() == "h1" || child.tagName() == "h2") {
        weight += 30
      } else if (child.tagName() == "div" || child.tagName() == "p") {
        weight += calcWeightForChild(child, ownText)
        if (child.tagName() == "p" && ownTextLength > 50) pEls.add(child)

        if (child.className().javaLowerCase() == "caption") caption = child
      }
    }

    // A caption means an image with it.
    if (caption != null) weight += 30

    if (pEls.size >= 2) {
      for (subEl in rootEl.children()) {
        if ("h1;h2;h3;h4;h5;h6".contains(subEl.tagName())) {
          weight += 20
        } else if ("table;li;td;th".contains(subEl.tagName())) {
          addScore(subEl, -30)
        }

        if ("p".contains(subEl.tagName())) addScore(subEl, 30)
      }
    }
    return weight
  }

  private fun addScore(el: Element, score: Int) {
    setScore(el, getScore(el) + score)
  }

  private fun getScore(el: Element): Int = try {
    Integer.parseInt(el.attr(GRAVITY_SCORE_ATTRIBUTE))
  } catch (ex: NumberFormatException) {
    0
  }

  private fun setScore(el: Element, score: Int) {
    el.attr(GRAVITY_SCORE_ATTRIBUTE, score.toString())
  }

  private fun calcWeightForChild(child: Element, ownText: String): Int {
    var c = countMatches(ownText, "&quot;")
    c += countMatches(ownText, "&lt;")
    c += countMatches(ownText, "&gt;")
    c += countMatches(ownText, "px")
    val value = if (c > 5) -30 else Math.round(ownText.length / 25.0).toInt()

    addScore(child, value)
    return value
  }

  private fun calcWeight(element: Element): Int {
    val className = element.className()
    val id = element.id()
    val style = element.attr("style")

    var weight = 0
    if (POSITIVE_CSS_CLASSES_AND_IDS.matcher(className).find()) weight += 35
    if (POSITIVE_CSS_CLASSES_AND_IDS.matcher(id).find()) weight += 40
    if (UNLIKELY_CSS_CLASSES_AND_IDS.matcher(className).find()) weight -= 20
    if (UNLIKELY_CSS_CLASSES_AND_IDS.matcher(id).find()) weight -= 20
    if (NEGATIVE_CSS_CLASSES_AND_IDS.matcher(className).find()) weight -= 50
    if (NEGATIVE_CSS_CLASSES_AND_IDS.matcher(id).find()) weight -= 50
    if (style.isNotEmpty() && NEGATIVE_CSS_STYLES.matcher(style).find()) weight -= 50
    return weight
  }

  /** The candidate elements in page order; each gets a starting score that halves down the page. */
  fun getNodes(doc: Document): Collection<Element> {
    // Elements compare by identity, so the set only keeps the page order.
    val nodes = LinkedHashSet<Element>(64)
    var score = 100
    for (el in doc.select("body").select("*")) {
      if (IMPORTANT_NODES.matcher(el.tagName()).matches()) {
        nodes.add(el)
        setScore(el, score)
        score /= 2
      }
    }
    return nodes
  }
}
