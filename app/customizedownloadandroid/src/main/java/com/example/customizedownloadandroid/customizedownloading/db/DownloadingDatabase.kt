package com.example.customizedownloadandroid.customizedownloading.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.customizedownloadandroid.customizedownloading.DownloadEntity

@Database(entities = [DownloadEntity::class], version = 1 , exportSchema = false)
abstract class DownloadingDatabase : RoomDatabase() {
    abstract fun getDownloadingDao(): DownloadingDao
    companion object {
        private var downloadingDatabase : DownloadingDatabase ?= null

        fun create(context : Context){
            downloadingDatabase = Room.databaseBuilder(context.applicationContext,
                DownloadingDatabase::class.java,"downloading_data").build()
        }
        fun getInstance() : DownloadingDatabase {
            return downloadingDatabase!!
        }
    }
}