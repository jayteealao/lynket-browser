/*
 * Adapted from Crux 2.0.1 (https://github.com/chimbori/crux), class articles.PreprocessHelpers.
 * Copyright Chimbori. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: translated to Kotlin; the unlikely-candidate
 * pass, which the original had switched off, is left out.
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
import org.jsoup.nodes.Node

/** Removes scripts, styles, forms and comments before the content is scored. */
internal object PreprocessHelpers {

  fun preprocess(doc: Document) {
    removeScriptsStylesForms(doc)
    removeComments(doc.body())
  }

  private fun removeScriptsStylesForms(doc: Document) {
    for (item in doc.getElementsByTag("script")) item.remove()
    for (item in doc.getElementsByTag("noscript")) item.remove()
    for (item in doc.getElementsByTag("style")) item.remove()
    for (item in doc.getElementsByTag("form")) item.remove()
  }

  private fun removeComments(node: Node) {
    var i = 0
    while (i < node.childNodes().size) {
      val child = node.childNode(i)
      if (child.nodeName() == "#comment") {
        child.remove()
      } else {
        removeComments(child)
        i++
      }
    }
  }
}
