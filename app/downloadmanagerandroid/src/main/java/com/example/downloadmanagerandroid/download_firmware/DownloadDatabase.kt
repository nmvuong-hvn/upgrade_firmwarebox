package com.example.downloadmanagerandroid.download_firmware

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.downloadmanagerandroid.DownloadEntity
import com.marusys.tv.mams.db.download_firmware.DownloadDao

@Database(
    entities = [DownloadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DownloadDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getDatabase(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "download_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }

        fun  getInstance(): DownloadDatabase {
            return INSTANCE ?: throw IllegalStateException("DownloadDatabase is not initialized, call getDatabase(context) first")
        }
    }
}
