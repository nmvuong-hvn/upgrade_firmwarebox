package com.example.customizedownloadandroid.customizedownloading

import android.app.Application
import com.example.customizedownloadandroid.customizedownloading.db.DownloadingDatabase

class MyApp : Application(){
    override fun onCreate() {
        super.onCreate()
        DownloadingDatabase.create(this@MyApp)
    }
}