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

package arun.com.chromer.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.AndroidSQLiteDriver
import java.util.Collections

/**
 * A [SQLiteDriver] that records the SQL of every statement Room prepares, so a test can count the
 * statements one DAO call runs. Room 3 has no query callback; this wraps the real driver instead.
 */
class StatementCountingDriver(private val delegate: SQLiteDriver = AndroidSQLiteDriver()) : SQLiteDriver {

  private val prepared: MutableList<String> = Collections.synchronizedList(mutableListOf())

  override val hasConnectionPool: Boolean
    get() = delegate.hasConnectionPool

  override fun open(fileName: String): SQLiteConnection = CountingConnection(delegate.open(fileName))

  /**
   * The prepared statements that name the `history` table, in order. Room's own bookkeeping (the
   * invalidation log and its triggers), `PRAGMA`s and transaction statements are left out: they
   * are not statements of the DAO.
   */
  fun historyStatements(): List<String> = synchronized(prepared) { prepared.toList() }
    .map { it.trim() }
    .filter { HISTORY.containsMatchIn(it) }
    .filterNot { sql -> ROOM_BOOKKEEPING.any { sql.contains(it, ignoreCase = true) } }
    .filterNot { sql -> CONTROL.any { sql.startsWith(it, ignoreCase = true) } }

  /** Forgets every statement recorded so far. */
  fun clear() = prepared.clear()

  private inner class CountingConnection(private val connection: SQLiteConnection) : SQLiteConnection {
    override fun inTransaction(): Boolean = connection.inTransaction()

    override fun prepare(sql: String): SQLiteStatement {
      prepared += sql
      return connection.prepare(sql)
    }

    override fun close() = connection.close()
  }

  private companion object {
    val HISTORY = Regex("""\bhistory\b""", RegexOption.IGNORE_CASE)
    val ROOM_BOOKKEEPING = listOf("room_table_modification_log", "room_table_modification_trigger", "TRIGGER")
    val CONTROL = listOf("PRAGMA", "BEGIN", "COMMIT", "END", "ROLLBACK", "SAVEPOINT", "RELEASE")
  }
}
