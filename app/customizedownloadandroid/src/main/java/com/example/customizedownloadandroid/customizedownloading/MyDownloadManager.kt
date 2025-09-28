package com.example.customizedownloadandroid.customizedownloading

import android.content.Context
import android.util.Log
import android.webkit.URLUtil
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.customizedownloadandroid.customizedownloading.db.DownloadingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MyDownloadManager (context: Context) : NetworkConnectionManager.NetworkStateListener {
    private val downloadingMap = ConcurrentHashMap<Long, DownloadEntity>()
    private val downloadTaskRunMap = ConcurrentHashMap<Long, MyDownloadTask>()
    private val downloadTaskRetrofitRunMap = ConcurrentHashMap<Long, RetrofitRequestClient>()
    private val TAG = "MyDownloadManager"
    private val networkConnectionManager = NetworkConnectionManager(context, this)
    init {
        networkConnectionManager.init()
        checkSyncDownloading()
    }
    fun checkSyncDownloading(){
        CoroutineScope(Dispatchers.IO).launch {
            DownloadingDatabase.getInstance().getDownloadingDao().getAllDownloadingList().forEach {data ->
                Log.d(TAG, "checkSyncDownloading: ===> data = $data")
                downloadFile(data)
            }
        }
    }
    fun downloadFile(entity: DownloadEntity) {
        if (!networkConnectionManager.isConnected()) {
            Log.d(TAG, "downloadFile: =====> no network")
            return
        }
        val downloadingEntity = downloadingMap[entity.downloadId]
        if (downloadingEntity != null) {
            Log.d(TAG, "downloadFile: ====> existing")
            val downloadTask = downloadTaskRunMap[downloadingEntity.downloadId]
            downloadTask?.close()
            val downloadTaskRun = MyDownloadTask(downloadingEntity)
            downloadTaskRun.execute()
            downloadTaskRunMap[downloadingEntity.downloadId] = downloadTaskRun
            return
        }
        runBlocking {
            DownloadingDatabase.getInstance().getDownloadingDao().insert(entity)
        }
        downloadingMap[entity.downloadId] = entity
        val downloadTaskRun = MyDownloadTask(entity)
        downloadTaskRun.execute()
        downloadTaskRunMap[entity.downloadId] = downloadTaskRun
    }

    fun downloadFileWithRetrofit(entity: DownloadEntity){
        if (!networkConnectionManager.isConnected()) {
            Log.d(TAG, "downloadFile: =====> no network")
            return
        }
        val downloadingEntity = downloadingMap[entity.downloadId]
        if (downloadingEntity != null) {
            Log.d(TAG, "downloadFile: ====> existing")
            val downloadTask = downloadTaskRetrofitRunMap[downloadingEntity.downloadId]
            downloadTask?.close()
            val downloadTaskRun = RetrofitRequestClient(downloadingEntity)
            downloadTaskRun.execute()
            downloadTaskRetrofitRunMap[downloadingEntity.downloadId] = downloadTaskRun
            return
        }
        runBlocking {
            DownloadingDatabase.getInstance().getDownloadingDao().insert(entity)
        }
        downloadingMap[entity.downloadId] = entity
        val downloadTaskRun = RetrofitRequestClient(entity)
        downloadTaskRun.execute()
        downloadTaskRetrofitRunMap[entity.downloadId] = downloadTaskRun
    }

    fun resumeDownloading(downloadId : Long){
        val downloadTaskRun = downloadTaskRunMap[downloadId]
        downloadTaskRun?.resume()

    }
    fun pauseDownloading(downloadId: Long) {
        val downloadTaskRun = downloadTaskRunMap[downloadId]
        downloadTaskRun?.pause()
    }

    override fun onNetworkAvailable() {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "onNetworkAvailable: =====> DELAY 5000")
            delay(5000L)
            DownloadingDatabase.getInstance().getDownloadingDao().getAllDownloadingList().forEach { data->
                var currentDownloadEntity = data
                val maxBytes =  data.downloadedBytes
                val tempPath = FileStorage.getTempPath(data.dirPath, data.fileName)
                val taskRun = downloadTaskRunMap.remove(data.downloadId)
                Log.d(TAG, "onNetworkAvailable: ====> maxBytes = $maxBytes")
                if (tempPath.isNotEmpty() && taskRun?.isResumeSupported() == true ){
                    val filePath = File(tempPath)
                    Log.d(TAG, "onNetworkAvailable: =====> filePath = $filePath - ")
                    currentDownloadEntity = data.copy(status = DownloadEntity.STATUS_DOWNLOADING)
                    if (filePath.exists()) {
                        if (maxBytes != filePath.length()) {
                            currentDownloadEntity = data.copy(downloadedBytes = filePath.length())
                            Log.d(TAG, "onNetworkLost: ======> currentDownloadEntity = $currentDownloadEntity - initial = $data")
                            DownloadingDatabase.getInstance().getDownloadingDao()
                                .updateDownloadedBytes(data.downloadId, filePath.length())

                        }
                    }
                }
                taskRun?.close()
                val myDownloadTask = RetrofitRequestClient(currentDownloadEntity)
                myDownloadTask.execute()
                downloadTaskRetrofitRunMap[data.downloadId] = myDownloadTask
            }
        }
    }

    override fun onNetworkLost() {
        CoroutineScope(Dispatchers.IO).launch {
            DownloadingDatabase.getInstance().getDownloadingDao().getAllDownloadingList().forEach { data->
                DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(data.downloadId, DownloadEntity.STATUS_PAUSED)
                val taskRun = downloadTaskRetrofitRunMap[data.downloadId]
                taskRun?.cancel()
            }
        }
    }
}

@Entity(tableName = "DownloadEntity")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val downloadId : Long = 0L,
    val url : String = "",
    val dirPath : String = "",
    val fileName : String = "",
    val filePath : String = "",
    val tempPath : String = "",
    val downloadedBytes : Long = 0L,
    val totalBytes : Long = 0L,
    val status : Int = STATUS_PENDING
){
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_PAUSED = 2
        const val STATUS_FAILED = 3
        const val STATUS_COMPLETED = 4
    }
}