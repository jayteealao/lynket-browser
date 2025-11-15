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

package arun.com.chromer.data.history.model

/**
 * Phase 7: Converted from Java to Kotlin
 *
 * Database table schema constants for History.
 * Kotlin object provides singleton pattern for constants.
 */
object HistoryTable {
    const val COLUMN_ID = "_ID"
    const val COLUMN_URL = "URL"
    const val COLUMN_TITLE = "TITLE"
    const val COLUMN_FAVICON = "FAVICON"
    const val COLUMN_CANONICAL = "CANONICAL"
    const val COLUMN_COLOR = "COLOR"
    const val COLUMN_AMP = "AMP"
    const val COLUMN_BOOKMARKED = "BOOKMARKED"
    const val COLUMN_CREATED_AT = "CREATED"
    const val COLUMN_VISITED = "VISITED"

    const val TABLE_NAME = "History"

    const val DATABASE_CREATE = "CREATE TABLE IF NOT EXISTS " +
            "$TABLE_NAME ( " +
            "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
            "$COLUMN_URL TEXT NOT NULL ," +
            "$COLUMN_TITLE TEXT, " +
            "$COLUMN_FAVICON TEXT, " +
            "$COLUMN_CANONICAL TEXT, " +
            "$COLUMN_COLOR TEXT, " +
            "$COLUMN_AMP TEXT, " +
            "$COLUMN_BOOKMARKED INTEGER, " +
            "$COLUMN_CREATED_AT TEXT, " +
            "$COLUMN_VISITED INTEGER" +
            ");"

    val ALL_COLUMN_PROJECTION = arrayOf(
        COLUMN_URL,
        COLUMN_TITLE,
        COLUMN_FAVICON,
        COLUMN_CANONICAL,
        COLUMN_COLOR,
        COLUMN_AMP,
        COLUMN_BOOKMARKED,
        COLUMN_CREATED_AT,
        COLUMN_VISITED
    )

    const val ORDER_BY_TIME_DESC = " CREATED DESC"
}
