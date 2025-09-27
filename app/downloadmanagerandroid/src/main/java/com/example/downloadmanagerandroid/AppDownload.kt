package com.example.downloadmanagerandroid

import android.app.Application
import com.example.downloadmanagerandroid.download_firmware.DownloadDatabase

class AppDownload : Application() {

    override fun onCreate() {
        super.onCreate()
        DownloadingManagerAndroid.create(this@AppDownload)
        DownloadDatabase.getDatabase(this@AppDownload)
    }
}