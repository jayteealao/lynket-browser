/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.data.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.AndroidSQLiteDriver
import arun.com.chromer.data.database.dao.WebsiteDao
import arun.com.chromer.data.database.entity.WebsiteEntity
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

/**
 * The history database (`chromer.db`), on Room 3 with the platform SQLite driver.
 *
 * Version 3 has the same tables as version 2; the empty 2 to 3 migration makes Room 3 validate
 * the existing tables and write its own identity data. There is no destructive fallback: a
 * missing migration fails the open, and [HistoryDatabase] keeps the file instead of wiping it.
 * Schemas are exported to `lynket/schemas/`.
 */
@Database(
    entities = [
        WebsiteEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class ChromerDatabase : RoomDatabase() {

    abstract fun websiteDao(): WebsiteDao

    companion object {
        const val DATABASE_NAME = "chromer.db"

        /**
         * Migration from legacy SQLite database (version 1) to Room database (version 2).
         *
         * The legacy database used table name "History" with uppercase column names.
         * The new Room database uses table name "history" with snake_case column names.
         *
         * Strategy:
         * 1. Create new Room schema table
         * 2. Copy data from old table to new table
         * 3. Drop old table
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                Timber.d("Migrating database from version 1 to 2")

                try {
                    // Create new Room-based table with correct schema
                    connection.execSQL("""
                        CREATE TABLE IF NOT EXISTS history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            url TEXT NOT NULL,
                            title TEXT,
                            favicon_url TEXT,
                            canonical_url TEXT,
                            theme_color TEXT,
                            amp_url TEXT,
                            bookmarked INTEGER NOT NULL DEFAULT 0,
                            created_at INTEGER NOT NULL,
                            visit_count INTEGER NOT NULL DEFAULT 1
                        )
                    """)

                    // Create indices for better query performance
                    connection.execSQL("""
                        CREATE UNIQUE INDEX IF NOT EXISTS index_history_url
                        ON history(url)
                    """)

                    connection.execSQL("""
                        CREATE INDEX IF NOT EXISTS index_history_created_at
                        ON history(created_at)
                    """)

                    // Check if legacy History table exists
                    val legacyTableExists = connection
                        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='History'")
                        .use { it.step() }

                    if (legacyTableExists) {
                        Timber.d("Legacy History table found, migrating data...")

                        // Copy data from legacy table to new table
                        // Map old column names to new ones
                        connection.execSQL("""
                            INSERT INTO history (url, title, favicon_url, canonical_url, theme_color, amp_url, bookmarked, created_at, visit_count)
                            SELECT
                                URL,
                                TITLE,
                                FAVICON,
                                CANONICAL,
                                COLOR,
                                AMP,
                                IFNULL(BOOKMARKED, 0),
                                CAST(IFNULL(CREATED, 0) AS INTEGER),
                                IFNULL(VISITED, 1)
                            FROM History
                        """)

                        // Drop legacy table
                        connection.execSQL("DROP TABLE IF EXISTS History")

                        Timber.d("Migration completed successfully")
                    } else {
                        Timber.d("No legacy table found, starting with fresh database")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Migration failed")
                    throw e
                }
            }
        }

        /** Room 2 to Room 3: the tables do not change; Room validates them and rewrites its identity row. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) = Unit
        }

        /** Builds the database on [DATABASE_NAME]; [HistoryDatabase] is the only caller in the app. */
        fun create(context: Context, name: String = DATABASE_NAME): ChromerDatabase =
            Room.databaseBuilder<ChromerDatabase>(context.applicationContext, name)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
