/*
 * Lynket
 *
 * Copyright (C) 2024 Arunkumar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.util

import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import java.util.Locale

/**
 * Makes the matching part of the string bold
 * @param query The query string to match and make bold
 * @return SpannableString with matching text in bold
 */
fun String.makeMatchingBold(query: String?): SpannableString {
    val spannable = SpannableString(this)
    if (query.isNullOrEmpty() || this.isEmpty()) {
        return spannable
    }

    val lowerCaseText = this.lowercase(Locale.getDefault())
    val lowerCaseQuery = query.lowercase(Locale.getDefault())

    var startIndex = lowerCaseText.indexOf(lowerCaseQuery)
    while (startIndex >= 0) {
        val endIndex = startIndex + lowerCaseQuery.length
        if (endIndex <= this.length) {
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                startIndex,
                endIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        startIndex = lowerCaseText.indexOf(lowerCaseQuery, endIndex)
    }

    return spannable
}
