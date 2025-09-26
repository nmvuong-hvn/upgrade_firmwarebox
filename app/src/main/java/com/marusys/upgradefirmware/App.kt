package com.marusys.upgradefirmware

import android.app.Application
import com.marusys.upgradefirmware.download_firmware.DownloadDatabase

class App : Application() {

    override fun onCreate() {
        super.onCreate()

//        val downloadDao = DownloadDatabase.getDatabase(this@App).downloadDao()
//        TestDownloadingManager.create(this@App, downloadDao)
//        TestDownloadingManager.getInstance().syncDataFromDb()
        TestDownloadingManager1.create(this@App)
        DownloadDatabase.getDatabase(this@App)
    }

    override fun onTerminate() {
        super.onTerminate()

    }
}
