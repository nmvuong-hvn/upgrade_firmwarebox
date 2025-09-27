package com.example.customizedownloadandroid;

import android.app.Application;

import com.example.customizedownloadandroid.customizedownloading.db.DownloadingDatabase;

public class AppTest extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DownloadingDatabase.Companion.create(this);
    }
}
