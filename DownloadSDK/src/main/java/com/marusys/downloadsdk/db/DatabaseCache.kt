package com.marusys.downloadsdk.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DataRequestModel::class],
    version = 2,
    exportSchema = false
)
abstract class DatabaseCache : RoomDatabase() {

    abstract fun getDataRequestDao(): DataRequestDao

    companion object {
        private const val DATABASE_NAME = "download_requests_db"
        
        @Volatile
        private var INSTANCE: DatabaseCache? = null
        
        // Migration from version 1 to 2 (if users have old database)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop old table if exists and create new one
                database.execSQL("DROP TABLE IF EXISTS data_request_table")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS download_requests (
                        downloadId INTEGER PRIMARY KEY NOT NULL,
                        fileName TEXT NOT NULL,
                        url TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        progress REAL NOT NULL,
                        downloadedBytes INTEGER NOT NULL,
                        totalBytes INTEGER NOT NULL,
                        state INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        etag TEXT,
                        retryCount INTEGER NOT NULL,
                        errorMessage TEXT
                    )
                """.trimIndent())
            }
        }
        
        fun create(context: Context) {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.applicationContext,
                        DatabaseCache::class.java,
                        DATABASE_NAME
                    )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // For development - remove in production
                    .build()
                }
            }
        }

        fun getInstance(): DatabaseCache {
            return INSTANCE ?: throw IllegalStateException(
                "Database not initialized. Call create(context) first."
            )
        }
        
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}